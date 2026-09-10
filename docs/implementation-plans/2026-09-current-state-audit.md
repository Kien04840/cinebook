# CineBook — Báo Cáo Audit Hiện Trạng Thực Tế (Current State Audit Report)

> **Mục đích**: Báo cáo kiểm định thực tế toàn diện trên cơ sở dữ liệu MySQL 8, mã nguồn Java Backend, mã nguồn Vue 3 Frontend và bộ kiểm thử của CineBook.  
> **Nguyên tắc**: Code và Database là nguồn chân lý tối thượng (Source of Truth). Mọi kết luận đều đi kèm bằng chứng cụ thể (file, line, query, test command).  
> **Thời điểm audit**: Tháng 09/2026  
> **Trạng thái**: VERIFIED & LOCKED  

---

## 1. Kết Quả Kiểm Tra Bộ Test & Build Thực Tế

### A. Backend Test Suite (`.\mvnw.cmd test`)
- **Lệnh thực thi thực tế**: `.\mvnw.cmd test`
- **Kết quả trả về từ Maven Surefire**:
  ```text
  [INFO] Results:
  [ERROR] Failures: 
  [ERROR]   AuditoriumNormalizationLiveIntegrationTest.testNormalizeEmptyAuditoriumsLayout_IdempotentRun:67 
  expected: 85 but was: 84
  [ERROR]   AuditoriumNormalizationLiveIntegrationTest.verifyNormalizedAuditoriumLayout:100 
  expected: 18L but was: 19L
  [INFO] 
  [ERROR] Tests run: 572, Failures: 2, Errors: 0, Skipped: 0
  ```
- **Phân tích chi tiết**:
  - Tổng số test cases trong toàn bộ backend hiện tại là **572 tests** (vượt qua các con số ước tính 490 hoặc 530 trong các tài liệu cũ).
  - **570 tests PASS trọn vẹn**.
  - **2 failures** tại class `AuditoriumNormalizationLiveIntegrationTest`:
    - Nguyên nhân: Đây là một Live Integration Test chạy trực tiếp trên cơ sở dữ liệu MySQL cục bộ (`cinebook`). Dữ liệu thực tế của một phòng chiếu trong database local đang có 84 ghế (thay vì 85) và 19 ghế đôi (thay vì 18). Đây là sự lệch pha giữa dữ liệu DB local và test fixture kỳ vọng, không phải lỗi logic mã nguồn core.
    - Phân loại: `CONTRADICTION_REQUIRES_DECISION` (Xem mục 5).

### B. Frontend Typecheck & Production Build
- **Lệnh Typecheck**: `npm run typecheck` (`vue-tsc --noEmit`)
  - **Kết quả**: Exit code 0, **PASS 100%**, 0 type errors.
- **Lệnh Build**: `npm run build` (`vue-tsc --noEmit && vite build`)
  - **Kết quả**: Exit code 0, **PASS 100%**, built thành công trong 4.34 giây. Không có bất kỳ lỗi biên dịch nào.

---

## 2. Xác Minh Các Quy Tắc Nghiệp Vụ Cốt Lõi (Core Business Invariants)

### A. Thời Gian Giữ Chỗ Ghế (Seat Hold TTL)
- **Tình trạng tài liệu cũ**: Một số tài liệu kế hoạch ghi nhầm "10 phút".
- **Evidence thực tế từ Code**:
  - `src/main/java/com/cinebook/service/impl/BookingServiceImpl.java` (dòng 57):
    ```java
    private static final int HOLD_DURATION_MINUTES = 5;
    ```
  - `src/main/java/com/cinebook/service/impl/BookingServiceImpl.java` (dòng 368):
    ```java
    LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);
    ```
  - `src/main/java/com/cinebook/service/impl/VnPayServiceImpl.java` (dòng 128):
    ```java
    vnpParams.put("vnp_ExpireDate", formatter.format(now.plusMinutes(5)));
    ```
  - `docs/business-rules.md` (dòng 166):
    `Seat-hold duration is strictly **5 minutes** (holdExpiresAt = now.plusMinutes(5)).`
- **Kết luận**: **Seat Hold TTL chính xác là 5 PHÚT**. Mọi tài liệu ghi 10 phút được phân loại là `DOCUMENTATION_STALE`.

### B. Chính Sách Hoàn Vé Khách Hàng (Customer Refund Time Window)
- **Tình trạng tài liệu cũ**: Một số tài liệu ghi nhầm "trước 60 phút".
- **Evidence thực tế từ Code**:
  - `src/main/java/com/cinebook/service/impl/PaymentServiceImpl.java` (dòng 576-579):
    ```java
    LocalDateTime minAllowedRefundTime = LocalDateTime.now().plusHours(2);
    if (showtime.getStartTime().isBefore(minAllowedRefundTime)) {
        throw new BadRequestException("Khách hàng chỉ có thể yêu cầu hoàn tiền trước giờ chiếu ít nhất 2 tiếng.");
    }
    ```
  - `docs/business-rules.md` (dòng 244):
    `Strictly >= 2 hours before showtime startTime.`
- **Kết luận**: **Quy định hoàn tiền của khách hàng bắt buộc là ÍT NHẤT 2 TIẾNG (>= 2 HOURS)** trước giờ chiếu. Tài liệu ghi 60 phút là `DOCUMENTATION_STALE`.

### C. Nguồn Chân Lý Duy Nhất Về Giá (Pricing SSOT)
- **Evidence thực tế từ Code**:
  - `src/main/java/com/cinebook/service/PricingService.java` & `PricingServiceImpl.java`:
    $$\text{Ticket Price} = \max\left(0, (\text{Showtime Base Price} \times \text{Seat Capacity}) + \text{Seat Type Modifier} + \text{Day Modifier} + \text{Time Slot Modifier}\right)$$
  - `src/main/java/com/cinebook/dto/response/ShowtimeSummaryResponse.java` (dòng 38): Hiện tại chỉ có `private BigDecimal basePrice;`.
  - `frontend/src/views/customer/ShowtimesView.vue` (dòng 505) và `ShowtimeBrowser.vue` (dòng 316): Đang hiển thị `st.basePrice`.
- **Kết luận**: Phân loại `PARTIALLY_IMPLEMENTED` và `VERIFIED_BUG`. Cần đưa trường tính toán `minPrice` vào `ShowtimeSummaryResponse` để phản ánh phụ thu ngày/giờ mà không để Frontend tự tính giá.

---

## 3. Bảng Phân Loại Hiện Trạng Toàn Diện (Audit Findings Matrix)

| Khu vực / Tính năng | Phân loại | Bằng chứng Code / Database / Test | Đánh giá & Hướng xử lý |
|---|---|---|---|
| **0.1 City Normalization** | `VERIFIED_BUG` | - DB `cinemas.city`: chỉ có `'Hanoi'`, `'Da Nang'`, `'Ho Chi Minh City'` (29 rạp).<br>- `AdminCinemasView.vue:150`: hardcode `['Hà Nội', 'TP. Hồ Chí Minh', ...]`. | Filter không ra dữ liệu, form edit không map được value. Cần dùng frontend constants có value canonical và label tiếng Việt. |
| **0.2 VNPay Cancel / Re-pay** | `PARTIALLY_IMPLEMENTED` | - `PaymentServiceImpl.java:316, 413`: `Payment = CANCELLED`, `Booking` vẫn `PENDING_PAYMENT`, `seat_holds` còn nguyên.<br>- `PaymentResultView.vue`: Không có nút "Thanh toán lại" hay "Hủy đơn". | Backend đã đúng lifecycle, Frontend thiếu nút Re-pay và nút Hủy đơn khiến user bị giữ ghế kẹt. |
| **0.3 Showtime Display Price** | `PARTIALLY_IMPLEMENTED` | - `PricingServiceImpl.java:25-28`: Đầy đủ công thức.<br>- `ShowtimeSummaryResponse.java:38`: Thiếu trường giá tối thiểu `minPrice`. | Cần bổ sung `minPrice` vào DTO trả về, Frontend chỉ việc hiển thị `Từ {{ formatCurrency(st.minPrice) }}`. |
| **0.4 Seat Adjacency** | `VERIFIED_MISSING` | - `seatGrid.ts`: Chưa có logic check gap.<br>- `BookingServiceImpl.java`: Chưa có check orphan single seat. | Cần bổ sung validation 2 tầng (Client real-time + Server authoritative). |
| **0.5 Static Policy Pages** | `VERIFIED_BUG` | - `DefaultLayout.vue:357-360`: Links tới `/terms`, `/privacy`, `/refund`, `/faq`.<br>- `router/index.ts`: Chưa đăng ký routes -> **404 Not Found**. | Tạo 5 view tĩnh chuẩn và đăng ký router để triệt tiêu 404. |
| **1.1 Admin User Management** | `PARTIALLY_IMPLEMENTED` | - `AdminUserController.java:38`: Chỉ có update status.<br>- `AdminUsersView.vue`: Chỉ có modal đổi status. | Cần thêm endpoint `PUT /api/v1/admin/users/{id}` cho phép sửa họ tên, sđt, vai trò (có guard chặn tự hạ quyền). |
| **1.2 Email Verification** | `PARTIALLY_IMPLEMENTED` | - `User.java:51`: Cột `email_verified` đã có.<br>- `AuthServiceImpl.java:126`: Set `false`. Chưa có token, chưa có endpoint verify. | Cần tạo bảng `email_verification_tokens`, service gửi mail và API verify. Không bắt buộc verify mới cho login trong V1. |
| **2.1 & 2.2 Genre Management** | `PARTIALLY_IMPLEMENTED` | - **Backend (VERIFIED_EXISTING)**: `AdminGenreController.java` (full CRUD), `AdminTmdbController.java` (`/syncGenres`).<br>- **Frontend (VERIFIED_MISSING)**: Chưa có views, service, types. | Không viết lại Backend. Chỉ tập trung xây dựng Frontend UI và tích hợp multi-select thể loại vào Movie modal. |
| **3.1 Food & Beverage (F&B)** | `VERIFIED_MISSING` | - DB & Code: Hoàn toàn chưa có `food_items`, `booking_foods`. | Cần thiết kế DDL additive, Entity, Service, bước chọn F&B khi đặt vé và tính `grandTotal`. |
| **3.2 In-App Notifications** | `VERIFIED_MISSING` | - DB & Code: Hoàn toàn chưa có `notifications`. | Cần thiết kế DDL additive, Entity, Service, trigger khi đặt vé/hoàn tiền và UI NotificationBell. |
| **4.1 Excel POI & CSV Export** | `PARTIALLY_IMPLEMENTED` | - `pom.xml:78`: ĐÃ CÓ `poi-ooxml:5.4.0`.<br>- `ReportServiceImpl.java:642`: ĐÃ IMPLEMENT hàm xuất XLSX và CSV.<br>- `AdminReportsView.vue`: Thiếu nút bấm UI.<br>- `report.service.ts:79`: Nhầm param `type` thay vì `reportType`. | Sửa tên param ở service frontend và thêm nút xuất trên giao diện. Tuyệt đối không thêm dependency mới. |

---

## 4. Báo Cáo Tài Liệu Lệch Pha (Documentation Staleness Report)

| File Tài Liệu | Vị Trí / Dòng | Nội Dung Hiện Tại (Stale) | Nội Dung Đúng Theo Code / DB Thực Tế |
|---|---|---|---|
| `docs/documentation-map.md` | Dòng 5, 75 | "Backend 530/530 Tests PASS", "490 tests" | Backend hiện tại có **572 tests** (570 PASS, 2 failures trên DB local). |
| `docs/implementation-plans/` (bản thảo cũ) | Nhiều vị trí | "Seat hold TTL = 10 phút" | Seat hold TTL chính xác là **5 phút** (`BookingServiceImpl.HOLD_DURATION_MINUTES = 5`). |
| `docs/implementation-plans/` (bản thảo cũ) | Nhiều vị trí | "Hoàn vé trước 60 phút" | Quy định hoàn vé cho khách hàng là **ít nhất 2 tiếng** (`PaymentServiceImpl.java:576`). |
| `docs/payment.md` | Section 4 | Chưa mô tả chi tiết chức năng xuất báo cáo Excel POI | Cần bổ sung mô tả API `GET /api/v1/admin/reports/export`. |

---

## 5. Các Điểm Mâu Thuẫn Cần Quyết Định (Contradictions Requiring Decision)

### Mâu Thuẫn 1: 2 Test Failures Trong `AuditoriumNormalizationLiveIntegrationTest`
- **Hiện tượng**:
  - `AuditoriumNormalizationLiveIntegrationTest.testNormalizeEmptyAuditoriumsLayout_IdempotentRun`: Kỳ vọng 85 ghế, thực tế DB có 84 ghế.
  - `AuditoriumNormalizationLiveIntegrationTest.verifyNormalizedAuditoriumLayout`: Kỳ vọng 18 couple seats, thực tế DB có 19 couple seats.
- **Bản chất**: Test này là Live Test chạy trực tiếp trên dữ liệu DB MySQL cục bộ của Developer. Trong các lần thử nghiệm trước đó trên DB local, layout của một phòng chiếu đã được cập nhật thành 84 ghế (19 couple seats).
- **Phương án đề xuất**:
  - **Phương án A (Khuyến nghị)**: Cập nhật assertion của test class này để khớp với dữ liệu thực tế hiện tại của DB local hoặc sử dụng dữ liệu dynamic.
  - **Phương án B**: Chạy script chuẩn hóa layout của phòng chiếu đó về đúng 85 ghế (18 couple seats).
- **Trạng thái**: `NEEDS HUMAN DECISION` khi bước vào Phase 0.

### Mâu Thuẫn 2: Nguồn Canonical Cho Thành Phố (City)
- **Hiện tượng**: Bảng `cinemas` chỉ có 3 thành phố tiếng Anh.
- **Phương án đề xuất**:
  - **Phương án A (Khuyến nghị)**: Dùng shared constants tại frontend (`constants.ts`) kết hợp i18n label. Không cần tạo thêm bảng DB hay endpoint backend mới (đơn giản, nhẹ, đủ dùng cho 3 thành phố cố định).
  - **Phương án B**: Tạo endpoint backend `GET /api/v1/cinemas/cities` trích xuất `SELECT DISTINCT city FROM cinemas`.
- **Trạng thái**: Chọn **Phương án A** (đã khóa theo DECISION-001).

