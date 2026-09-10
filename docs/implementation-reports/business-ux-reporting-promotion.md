# Implementation Report: Business UX, Reporting, User Privacy & Promotion Enhancement

> **Project**: CineBook — Monolithic Cinema Booking Platform  
> **Date**: 2026-09-11  
> **Status**: COMPLETED & VERIFIED  
> **Verification Gate**: Backend 225/225 targeted tests PASS (650/652 total test suite PASS, 2 pre-existing local integration DB mismatches preserved), Frontend Typecheck 100% PASS, Frontend Build 100% PASS.

---

## 1. Executive Summary

Sau giai đoạn hoàn thiện Localization và Error Standardization, CineBook tiếp tục thực hiện đợt nâng cấp chất lượng tập trung vào 4 nhóm vấn đề nghiệp vụ cốt lõi:

1. **Khắc phục lỗi Encoding/Mojibake của dữ liệu F&B**: Dọn dẹp triệt để chuỗi lỗi font tiếng Việt trong MySQL và bổ sung cấu hình mã hóa đồng bộ trên kết nối JDBC.
2. **Khắc phục tỷ lệ lấp đầy phòng chiếu (Occupancy Rate > 100%) & nâng cấp Admin Dashboard/Reports**: Thay thế công thức tính trung bình cộng số học đơn giản bằng công thức tổng hợp có trọng số theo sức chứa thực tế; bổ sung chốt chặn phòng vệ `[0.00, 100.00]%` và cảnh báo bất thường; làm sạch định dạng hiển thị phần trăm.
3. **Bộ lọc ngày & khoảng ngày cho thống kê**: Chuẩn hóa cận thời gian bao gồm đầy đủ (`00:00:00` đến `23:59:59.999999999`), kiểm tra hợp lệ `from <= to`, cung cấp các preset nhanh (`Hôm nay`, `Hôm qua`, `7 ngày qua`, `30 ngày qua`, `Tháng này`, `Tùy chọn`), skeleton loading và trạng thái dữ liệu rỗng.
4. **Giới hạn quyền Admin khi chỉnh sửa User (Bảo vệ quyền riêng tư)**: Thu hẹp phạm vi chỉnh sửa của Admin chỉ gồm `fullName`, `status`, `roles`. Nghiêm cấm và loại bỏ khả năng chỉnh sửa `phone`, `email`, `password` từ phía Admin ở cả tầng DTO backend và giao diện người dùng.
5. **Nâng cấp trải nghiệm người dùng với Khuyến mãi (Promotion UX)**: Hỗ trợ song song cả 2 cơ chế: khách hàng duyệt danh sách voucher khả dụng (`🎁 Chọn ưu đãi`) qua API `GET /api/v1/promotions/available` hoặc tự nhập mã khuyến mãi thủ công; duy trì nguyên tắc không tự động áp dụng (no auto-apply) và máy chủ là nguồn kiểm duyệt, tính toán giảm giá duy nhất.

---

## 2. Root-Cause Analysis & Fix for F&B Encoding

### 2.1 Nguyên nhân gốc rễ
- **Bảng `food_items`**: Bảng trong MySQL được khởi tạo với bảng mã `utf8mb4` và collation `utf8mb4_0900_ai_ci`, tuy nhiên script chèn dữ liệu ban đầu chạy qua client không chỉ định charset UTF-8 hoặc chạy trong môi trường Windows ANSI/CP1252.
- **Hệ quả**: Các ký tự tiếng Việt có dấu bị lỗi thành các ký tự rác (mojibake) như `Bß║»p Rang Bãí Ngß╗ìt...` hoặc `Ti├¬u chuß║®n...`.
- **Đường truyền JDBC**: Tham số kết nối datasource trong `application.yml` chưa chỉ định tường minh `&characterEncoding=UTF-8`.

### 2.2 Giải pháp thực hiện
1. **Dữ liệu MySQL**: Thực thi script cập nhật an toàn (`UPDATE cinebook.food_items SET name = ..., description = ... WHERE id = ...`) sử dụng kết nối `--default-character-set=utf8mb4`. Không sử dụng lệnh phá hủy (`DROP`, `TRUNCATE`). Toàn bộ 8 món ăn/nước uống và combo đã được phục hồi chính xác 100% tiếng Việt chuẩn.
2. **Cấu hình JDBC**: Cập nhật URL kết nối trong `src/main/resources/application.yml` thành:
   ```yaml
   url: jdbc:mysql://localhost:3306/cinebook?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
   ```
3. **Frontend**: Xác minh các component `FoodSelection.vue` và `AdminFoodsView.vue` render động hoàn toàn từ API backend mà không cần dùng đến các hàm replace/hack chuỗi phía client.

---

## 3. Root-Cause Analysis & Fix for Occupancy Rate

### 3.1 Nguyên nhân gốc rễ
1. **Sai lệch trọng số trong Dashboard Summary**: Trong `ReportServiceImpl.getDashboardSummary`, tỷ lệ lấp đầy trung bình toàn hệ thống (`averageOccupancyRate`) được tính bằng trung bình cộng số học của các suất chiếu (`sumRate / showtimes.size()`).
   - *Ví dụ sai lệch*: Một suất chiếu ở phòng nhỏ (10 ghế) bán được 1 ghế (10%) và một suất chiếu ở phòng lớn (200 ghế) bán được 180 ghế (90%). Trung bình cộng là $(10\% + 90\%) / 2 = 50\%$, trong khi tỷ lệ lấp đầy thực tế theo số người xem là $(1 + 180) / (10 + 200) \approx 86.19\%$.
2. **Nguy cơ vượt ngưỡng 100%**: Khi dữ liệu thử nghiệm tạo ghế thủ công hoặc có sự không nhất quán tạm thời giữa số vé hợp lệ và sức chứa ghế, tỷ lệ có thể vượt $100\%$ do thiếu bước chặn bảo vệ (defensive clamping).

### 3.2 Giải pháp thực hiện
1. **Công thức tổng hợp có trọng số**: Cập nhật `ReportServiceImpl.java` tính toán:
   $$\text{averageOccupancyRate} = \frac{\sum \text{occupiedSeats của tất cả suất chiếu}}{\sum \text{totalCapacity của tất cả suất chiếu}} \times 100\%$$
2. **Chốt chặn phòng vệ (Defensive Clamping)**:
   - Trong `calculateOccupancies`:
     ```java
     double rate = (totalCapacity > 0)
             ? ((double) occupiedCapacity / totalCapacity) * 100.0
             : 0.0;
     if (occupiedCapacity > totalCapacity) {
         log.warn("[OCCUPANCY ANOMALY] Showtime ID {} has occupiedCapacity ({}) exceeding totalCapacity ({})",
                 showtime.getId(), occupiedCapacity, totalCapacity);
     }
     rate = Math.min(100.0, Math.max(0.0, rate));
     ```
   - Khi `totalCapacity == 0`, tỷ lệ trả về an toàn là `0.00%`.
3. **Chuẩn hóa định dạng phần trăm**:
   - `frontend/src/utils/formatters.ts`: `formatPercent` định dạng số phần trăm gọn gàng, tránh số thực dài (`72.4%`).
   - Giao diện `AdminReportsView.vue` hiển thị màu sắc trực quan (xanh lá $\ge 70\%$, vàng cam $\ge 40\%$, xám xịt $< 40\%$).

---

## 4. Date Range Filters & Semantics

### 4.1 Ngữ nghĩa cận thời gian (Inclusive Datetime)
- Khi người dùng truyền `from = 2026-09-01`, backend tự động parse thành `2026-09-01T00:00:00`.
- Khi người dùng truyền `to = 2026-09-10`, backend tự động parse thành `2026-09-10T23:59:59.999999999`.
- Toàn bộ giao dịch và dữ liệu trong ngày kết thúc được tổng hợp đầy đủ, không bị sót đơn hàng cuối ngày.

### 4.2 Kiểm tra hợp lệ (Date Range Validation)
- Bổ sung validation `from <= to` trong `AdminReportController` và `ReportServiceImpl`.
- Nếu `from > to`, hệ thống lập tức ném `BadRequestException("Ngày bắt đầu không được lớn hơn ngày kết thúc.")` với mã lỗi HTTP 400.

### 4.3 Preset bộ lọc & Trải nghiệm giao diện
- `AdminReportsView.vue` hỗ trợ 6 chế độ lọc:
  - `TODAY`: Hôm nay
  - `YESTERDAY`: Hôm qua
  - `7d` (Mặc định): 7 ngày qua
  - `30d`: 30 ngày qua
  - `THIS_MONTH`: Tháng này
  - `custom`: Tùy chọn khoảng ngày với 2 ô chọn ngày kèm thông báo kiểm tra tính hợp lệ.
- Bổ sung Skeleton loader cho biểu đồ doanh thu, cơ cấu vé, phim ăn khách, rạp chiếu và bảng tỷ lệ lấp đầy.
- Bổ sung Empty-state thân thiện: `"Chưa có dữ liệu trong khoảng thời gian này"` kèm icon minh họa.

---

## 5. Admin User Privacy Enforcement

### 5.1 Ranh giới kiến trúc & Bảo vệ quyền riêng tư
- **Nguyên tắc bảo vệ dữ liệu**: Tài khoản người dùng thuộc quyền sở hữu của cá nhân khách hàng. Quản trị viên (Admin) chỉ có quyền quản lý định danh hiển thị (`fullName`), trạng thái tài khoản (`status`), và phân quyền (`roles`).
- **Bảo vệ tuyệt đối**: Admin **KHÔNG ĐƯỢC PHÉP** chỉnh sửa:
  - `email`: Định danh đăng nhập và kênh bảo mật cá nhân.
  - `phone`: Số điện thoại liên hệ cá nhân.
  - `password` / `passwordHash`: Mật khẩu tài khoản chỉ được thay đổi bởi chủ tài khoản qua quy trình xác thực.

### 5.2 Triển khai Backend
- Loại bỏ trường `phone` và validation `@Pattern` khỏi DTO `AdminUpdateUserRequest.java`.
- Trong `UserServiceImpl.java::adminUpdateUser`, xóa bỏ logic cập nhật số điện thoại. Duy trì các chốt bảo vệ an toàn:
  - Tự vô hiệu hóa (`Self-Disable Guard`).
  - Tự hạ quyền (`Self-Demotion Guard`).
  - Khóa bi quan bảo vệ quản trị viên duy nhất (`Last-Active-Admin Guard`).
- Bổ sung unit test `adminUpdateUser_PreservesPrivateFields_EmailPhonePassword` chứng minh các trường nhạy cảm giữ nguyên vẹn 100%.

### 5.3 Triển khai Frontend
- `AdminUsersView.vue`: Chuyển hiển thị Email và Số điện thoại trong modal chỉnh sửa sang dạng khối thông tin chỉ đọc (Read-only) với huy hiệu `Chỉ đọc` và ghi chú bảo vệ quyền riêng tư.
- Loại bỏ `phone` khỏi payload cập nhật gửi lên backend.

---

## 6. Promotion UX Upgrade & Available API

### 6.1 Thiết kế API Khuyến mãi khả dụng
- Bổ sung endpoint công khai/xác thực:
  ```http
  GET /api/v1/promotions/available
  ```
- Query tối ưu trong `PromotionRepository.java`:
  ```sql
  SELECT p FROM Promotion p 
  WHERE p.status = com.cinebook.enums.PromotionStatus.ACTIVE 
    AND p.startAt <= :now AND p.endAt >= :now 
    AND (p.usageLimit IS NULL OR p.usedCount < p.usageLimit) 
  ORDER BY p.endAt ASC
  ```
- Trả về danh sách voucher đang hoạt động, trong thời hạn hiệu lực và chưa hết lượt sử dụng, sắp xếp theo thời điểm hết hạn gần nhất.

### 6.2 Nâng cấp trải nghiệm đặt vé (BookingSummary.vue)
- **Hỗ trợ song song 2 phương thức**:
  1. Chọn ưu đãi có sẵn: Nút `🎁 Chọn ưu đãi` mở modal liệt kê các voucher khả dụng, hiển thị điều kiện đơn tối thiểu (`minOrderAmount`), giá trị giảm, hạn dùng.
  2. Nhập mã thủ công: Ô nhập mã voucher và nút `Áp dụng` vẫn được giữ nguyên.
- **Quy tắc No Auto-Apply**: Hệ thống không tự tiện áp dụng giảm giá. Khách hàng chủ động bấm chọn hoặc nhập mã.
- **Hiển thị trực quan**: Khi áp dụng thành công, hiển thị badge `✓ Giảm X đ` kèm nút `[Bỏ chọn]` để hủy nhanh.
- **Bóc tách hóa đơn rõ ràng**:
  - Tiền vé
  - Tiền bắp nước (F&B)
  - Giảm giá khuyến mãi (`- X đ`)
  - Tổng thanh toán cuối cùng

---

## 7. Cross-Module Consistency & Financial Invariants

Hệ thống đảm bảo tính toàn vẹn tài chính trên toàn bộ chu trình đặt vé và thanh toán:

$$\text{Tổng thanh toán} = \max(0, \text{Tiền vé} + \text{Tiền F\&B} - \text{Khuyến mãi})$$

1. **Thẩm quyền máy chủ (Server Authoritative)**: Frontend chỉ tính toán trước số tiền giảm ước tính để phản hồi tức thì cho người dùng. Khi gửi yêu cầu tạo booking (`POST /api/v1/bookings`), Backend kiểm tra độc lập lại toàn bộ điều kiện:
   - Sức chứa ghế và giá vé theo loại ghế (`SeatType.priceModifier`) và ngày chiếu (`DayPricingRule`).
   - Giá từng món F&B từ cơ sở dữ liệu.
   - Hiệu lực của mã khuyến mãi, kiểm tra ngưỡng đơn hàng tối thiểu $\ge \text{minOrderAmount}$.
   - Khóa bi quan `SELECT ... FOR UPDATE` kiểm tra hạn mức sử dụng `usedCount < usageLimit`.
2. **Bất biến thanh toán VNPay**:
   - `PaymentServiceImpl` lấy chính xác `booking.totalAmount` đã tính toán để tạo giao dịch VNPay.
   - Khi VNPay IPN phản hồi, số tiền thanh toán `vnp_Amount / 100` được so sánh tuyệt đối với `booking.totalAmount`.
   - Trong suốt quá trình thanh toán và hoàn tiền, số tiền giảm giá trong `booking_promotions` là bất biến (immutable snapshot).

---

## 8. Frontend Polish & Performance

- **Loading Skeletons**: Thêm hiệu ứng `animate-pulse` đồng bộ cho toàn bộ các biểu đồ và bảng dữ liệu trong `AdminReportsView.vue`, loại bỏ giật bố cục (layout shift) khi chuyển đổi tab hoặc tải dữ liệu.
- **Zero-States**: Các biểu đồ và bảng dữ liệu hiển thị thông báo rõ ràng: `"Chưa có dữ liệu trong khoảng thời gian này"` kèm icon sinh động khi khoảng thời gian chọn không phát sinh giao dịch.
- **Internationalization (i18n)**: Bổ sung 100% khóa dịch song ngữ Anh - Việt cho các nhãn preset thời gian, thông báo bảo vệ quyền riêng tư, và thông tin modal khuyến mãi.

---

## 9. Verification Results & Regression Matrix

### 9.1 Backend Test Suites
Thực thi kiểm thử hồi quy trên toàn bộ các domain bị tác động:
```powershell
.\mvnw.cmd test "-Dtest=FoodItemServiceTest,AdminFoodItemControllerTest,ReportServiceTest,AdminReportControllerTest,ReportSecurityTest,UserServiceTest,AdminUserControllerTest,UserControllerTest,PromotionServiceTest,PromotionControllerTest,AdminPromotionControllerTest,PromotionConcurrencyTest,BookingServiceTest,BookingControllerTest,PaymentServiceTest,AdminPaymentControllerTest"
```
**Kết quả**:
- **Tests run**: 225
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Build Status**: `BUILD SUCCESS` (16.376 s)

Toàn bộ test suite dự án đạt **650/652 test PASS** (2 failure duy nhất là integration test kiểm tra dữ liệu local phòng chiếu đã được ghi nhận trong các đợt trước).

### 9.2 Frontend Typecheck & Build
```powershell
npm run typecheck
npm run build
```
**Kết quả**:
- `vue-tsc --noEmit`: 0 errors.
- `vite build`: 314 modules transformed thành công, `dist/` bundle hoàn tất trong 5.39s.

---

## 10. Modified Files Inventory & Documentation Synchronization

### 10.1 Backend Files Changed
- `src/main/resources/application.yml`: Bổ sung `&characterEncoding=UTF-8` vào JDBC datasource URL.
- `src/main/java/com/cinebook/service/impl/ReportServiceImpl.java`: Công thức occupancy có trọng số, defensive clamp, log warning anomaly, inclusive datetime validation.
- `src/main/java/com/cinebook/controller/AdminReportController.java`: Inclusive date parsing và validation `from <= to`.
- `src/main/java/com/cinebook/dto/request/AdminUpdateUserRequest.java`: Loại bỏ trường `phone`.
- `src/main/java/com/cinebook/service/impl/UserServiceImpl.java`: Loại bỏ cập nhật `phone`, bảo vệ thông tin cá nhân.
- `src/main/java/com/cinebook/repository/PromotionRepository.java`: Bổ sung `findAvailablePromotions()`.
- `src/main/java/com/cinebook/service/PromotionService.java` & `PromotionServiceImpl.java`: Bổ sung `getAvailablePromotions()`.
- `src/main/java/com/cinebook/controller/PromotionController.java`: Endpoint `GET /api/v1/promotions/available`.
- `src/test/java/com/cinebook/service/UserServiceTest.java`: Unit test bảo vệ private fields của user.
- `src/test/java/com/cinebook/service/ReportServiceTest.java`: Unit test cho weighted occupancy và anomaly logging.
- `src/test/java/com/cinebook/controller/AdminReportControllerTest.java`: Unit test cho date range validation.
- `src/test/java/com/cinebook/service/PromotionServiceTest.java`: Unit test cho available promotions.
- `src/test/java/com/cinebook/controller/PromotionControllerTest.java`: Unit test controller cho available promotions.

### 10.2 Frontend Files Changed
- `frontend/src/types/user.types.ts`: Loại bỏ `phone` khỏi payload cập nhật user của Admin.
- `frontend/src/views/admin/AdminUsersView.vue`: Hiển thị email và phone ở dạng Read-only kèm huy hiệu bảo mật.
- `frontend/src/types/report.types.ts`: Định nghĩa kiểu `DateFilterPreset`.
- `frontend/src/utils/formatters.ts`: Chuẩn hóa `formatPercent` và `formatCurrency`.
- `frontend/src/views/admin/AdminReportsView.vue`: Preset ngày, picker tùy chọn, validation, skeletons, empty states.
- `frontend/src/components/charts/DonutChart.vue`: Nâng cấp giao diện biểu đồ tròn.
- `frontend/src/services/promotion.service.ts`: Thêm `getAvailablePromotions()`.
- `frontend/src/components/booking/BookingSummary.vue`: Nút & modal `🎁 Chọn ưu đãi`, bóc tách giá tiền, huy hiệu đã chọn.
- `frontend/src/locales/vi.ts` & `frontend/src/locales/en.ts`: Cập nhật i18n đồng bộ.

### 10.3 Documentation Updated
- `docs/api.md`: Bổ sung `GET /api/v1/promotions/available`, cập nhật `PUT /api/v1/admin/users/{id}`, chuẩn hóa query params của reporting.
- `docs/business-rules.md`: Bổ sung quy tắc bảo vệ thông tin người dùng, công thức tỷ lệ lấp đầy có trọng số và clamping, cơ chế dual-input promotion.
- `docs/database.md`: Ghi chú về charset `utf8mb4` và tính toàn vẹn dữ liệu bảng `food_items`.
- `docs/use-cases/reporting.md`: Cập nhật chi tiết về công thức occupancy và các preset ngày.
- `docs/use-cases/administration.md`: Cập nhật ranh giới quyền riêng tư của người dùng.
- `docs/use-cases/promotion.md`: Cập nhật luồng chọn ưu đãi khả dụng trong checkout.
- `docs/documentation-map.md`: Ghi nhận các sắc thái kỹ thuật mới (nuances 12-14) và đăng ký implementation report mới.

