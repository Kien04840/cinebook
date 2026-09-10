# CineBook — Kế Hoạch Triển Khai Tổng Thể (Master Implementation Plan)
## Lộ Trình 5 Giai Đoạn Phát Triển & Khóa Quyết Định Kiến Trúc (Phiên Bản Đã Xác Minh Thực Tế)

> **Mục tiêu**: Định nghĩa toàn bộ lộ trình triển khai chi tiết cho 5 giai đoạn phát triển tiếp theo của CineBook, dựa trên kết quả audit mã nguồn, cơ sở dữ liệu và bộ kiểm thử thực tế.  
> **Nguyên tắc bất biến**: Mã nguồn và CSDL thực tế là chân lý tối thượng. Tuân thủ 100% nguyên tắc Monolith Layered Architecture, Database Safety (Additive-only) và Single Source of Truth.  
> **Trạng thái hiện tại**: PLANNING ONLY — CHƯA SỬA ĐỔI SOURCE CODE HAY DATABASE.  

---

## 1. Tóm Tắt Hiện Trạng Xác Minh Thực Tế

1. **Bộ kiểm thử & Build**:
   - Backend hiện có **572 tests** (570 PASS, 2 failures thuộc về Live Integration Test trên dữ liệu phòng chiếu DB local).
   - Frontend `vue-tsc --noEmit` và `npm run build` đều đạt **100% PASS** (0 errors).
2. **Quy tắc nghiệp vụ cốt lõi (Source of Truth)**:
   - **Thời gian giữ chỗ (Seat Hold TTL)**: Chính xác là **5 PHÚT** (`BookingServiceImpl.HOLD_DURATION_MINUTES = 5`).
   - **Thời gian hoàn vé khách hàng**: Bắt buộc **TRƯỚC GIỜ CHIẾU ÍT NHẤT 2 TIẾNG (>= 2 HOURS)** (`PaymentServiceImpl.java:576`).
   - **Thư viện xuất Excel**: ĐÃ CÓ SẴN `org.apache.poi:poi-ooxml:5.4.0` trong `pom.xml` (dòng 78-81). Backend đã implement đầy đủ hàm xuất XLSX và CSV trong `ReportServiceImpl.java`. **Không thêm dependency mới cho Phase 4**.
   - **Thành phố (City)**: Bảng `cinemas.city` trong DB lưu 3 giá trị tiếng Anh `'Hanoi'`, `'Da Nang'`, `'Ho Chi Minh City'`. Frontend `AdminCinemasView.vue:150` hardcode tiếng Việt có dấu dẫn đến lỗi lệch pha lọc và hiển thị.
   - **Hủy thanh toán VNPay**: Khi VNPay trả về mã 24, backend đánh dấu `Payment = CANCELLED`, nhưng `Booking` vẫn là `PENDING_PAYMENT` và `seat_holds` được giữ nguyên trong 5 phút. Frontend thiếu nút "Thanh toán lại" và "Hủy đơn", làm kẹt ghế của người dùng.
   - **Trang chính sách**: Footer có liên kết tới 4 trang tĩnh nhưng router chưa đăng ký, gây lỗi **404 Not Found**.

---

## 2. Thứ Tự Triển Khai & Sơ Đồ Phụ Thuộc (Dependency Graph)

```text
PHASE 0: Core Hardening, Integrity & Bug Fixes (Nền tảng cấp thiết, sửa lỗi hỏng hóc hiện tại)
  ├── 0.1 Chuẩn hóa Thành phố (Shared Constants song ngữ, sửa bug Admin filter & edit)
  ├── 0.2 Hoàn thiện VNPay Cancel / Re-pay Flow (Countdown 5 phút, nút Thanh toán lại & Hủy đơn)
  ├── 0.3 Đồng bộ Pricing SSOT (minPrice trên ShowtimeSummaryResponse)
  ├── 0.4 Bổ sung Quy tắc Ghế đơn lẻ (Seat Adjacency Validation 2 tầng: Client + Server)
  └── 0.5 Xây dựng 5 Trang Chính sách Tĩnh (Triệt tiêu 404 Footer)
        │
        ▼
PHASE 1: User & Security Management
  ├── 1.1 Mở rộng API & UI Cập nhật Người dùng Admin (Có Last-Admin Protection & chặn tự hạ quyền)
  └── 1.2 Triển khai Cơ chế Xác thực Email (Token 24h, Rate limit 60s, Non-blocking V1)
        │
        ▼
PHASE 2: Genre Management & TMDB Sync
  ├── 2.1 Xây dựng Giao diện Quản lý Thể loại Phim (Tận dụng 100% Backend CRUD + Sync TMDB có sẵn)
  └── 2.2 Tích hợp Multi-Select Thể loại vào Modal Phim Admin
        │
        ▼
PHASE 3: Commercial Expansion — F&B & Notifications
  ├── 3.1 Phân hệ Bắp nước / Combo (Schema Additive, CRUD Admin, Chọn F&B khi đặt vé, Grand Total)
  └── 3.2 Phân hệ Thông báo Nội bộ (Schema Additive, Service, Trigger khi thanh toán/hoàn tiền, UI Bell)
        │
        ▼
PHASE 4: Business Analytics & Reporting
  ├── 4.1 Sửa lỗi Param & Thêm nút Xuất Excel XLSX / CSV trên Admin Reports
  └── 4.2 Tối ưu hóa & Đồng bộ Toàn bộ Tài liệu Dự án
```

---

## 3. Kế Hoạch Chi Tiết Từng Phase

---

### PHASE 0: CORE HARDENING, INTEGRITY & BUG FIXES

#### Nhiệm vụ 0.1: Chuẩn Hóa Thành Phố (City Normalization)
- **Mục tiêu**: Khắc phục lỗi lệch pha giữa mảng tiếng Việt hardcode và dữ liệu DB tiếng Anh, giúp bộ lọc và form sửa rạp hoạt động chính xác.
- **Thay đổi Frontend**:
  - `frontend/src/utils/constants.ts`: Định nghĩa mảng canonical cities song ngữ:
    ```typescript
    export const CANONICAL_CITIES = [
      { value: 'Hanoi', labelVi: 'Hà Nội', labelEn: 'Hanoi' },
      { value: 'Da Nang', labelVi: 'Đà Nẵng', labelEn: 'Da Nang' },
      { value: 'Ho Chi Minh City', labelVi: 'TP. Hồ Chí Minh', labelEn: 'Ho Chi Minh City' },
    ]
    ```
  - `frontend/src/views/admin/AdminCinemasView.vue`: Thay thế mảng hardcode dòng 150 bằng `CANONICAL_CITIES`. Dropdown binding lưu `value`, hiển thị `labelVi` hoặc `labelEn` theo locale.
- **Ràng buộc**: Không sửa dữ liệu cột `city` trong DB, không tạo bảng mới.

#### Nhiệm vụ 0.2: Ngữ Nghĩa Hủy Thanh Toán VNPay & Re-pay Flow
- **Mục tiêu**: Người dùng hủy giao dịch trên VNPay có thể bấm "Thanh toán lại" ngay trên đơn cũ nếu chưa hết 5 phút giữ chỗ.
- **Thay đổi Frontend**:
  - `frontend/src/views/customer/PaymentResultView.vue`:
    - Khi kết quả là `CANCELLED` hoặc `FAILED`: Kiểm tra `holdExpiresAt`.
    - Nếu còn hạn: Hiển thị đồng hồ đếm ngược thời gian giữ chỗ 5 phút còn lại.
    - Nút **"Thanh toán lại"**: Gọi `POST /api/v1/bookings/{id}/payments` để lấy URL thanh toán VNPay mới (idempotent resume/retry).
    - Nút **"Hủy đơn hàng"**: Gọi `POST /api/v1/bookings/{id}/cancel` để giải phóng ghế ngay lập tức và đưa booking về `CANCELLED`.
- **Ràng buộc**: Không tự động xóa `seat_holds` khi khách chỉ mới hủy trên cổng VNPay.

#### Nhiệm vụ 0.3: Giá Hiển Thị Suất Chiếu & Pricing SSOT
- **Mục tiêu**: Hiển thị giá khởi điểm thực tế (đã gồm phụ thu ngày/giờ) ngay trên danh sách suất chiếu, bảo đảm tính minh bạch.
- **Thay đổi Backend**:
  - `src/main/java/com/cinebook/dto/response/ShowtimeSummaryResponse.java`: Bổ sung trường `private BigDecimal minPrice;`.
  - `src/main/java/com/cinebook/mapper/ShowtimeMapper.java`: Tính `minPrice` bằng cách gọi `pricingService.calculateShowtimeBaseBreakdown(showtime).getBasePrice()`.
- **Thay đổi Frontend**:
  - `ShowtimesView.vue` và `ShowtimeBrowser.vue`: Render `Từ {{ formatCurrency(st.minPrice || st.basePrice) }}`.

#### Nhiệm vụ 0.4: Quy Tắc Ghế Đơn Lẻ (Seat Adjacency Validation)
- **Mục tiêu**: Chặn hành vi đặt ghế để lại 1 ghế trống đơn độc không bán được.
- **Thay đổi Frontend**:
  - `frontend/src/utils/seatAdjacency.ts` [NEW]: Viết hàm kiểm tra quy tắc No Single Orphan Seat theo từng hàng, hỗ trợ ghế đôi Couple (`capacity = 2`), bỏ qua ghế hỏng và khoảng trống lối đi.
  - `frontend/src/components/booking/SeatMap.vue`: Gọi validator khi click ghế, cảnh báo người dùng chọn liền kề và disable nút tiếp tục nếu vi phạm.
- **Thay đổi Backend**:
  - `src/main/java/com/cinebook/service/impl/BookingServiceImpl.java`: Bổ sung phương thức `validateSeatAdjacency(showtime, seatsToHold)` trước khi lưu `seat_holds`. Nếu vi phạm quăng `BadRequestException`.

#### Nhiệm vụ 0.5: Xây Dựng Các Trang Chính Sách Tĩnh (Fix 404 Footer)
- **Mục tiêu**: Triệt tiêu hoàn toàn lỗi 404 khi bấm vào các liên kết chân trang.
- **Thay đổi Frontend**:
  - Tạo 5 component tại `frontend/src/views/static/`:
    - `TermsOfUseView.vue` (Điều khoản sử dụng: quy định giữ chỗ 5 phút, thanh toán, tài khoản).
    - `PrivacyPolicyView.vue` (Chính sách bảo mật: bảo mật thông tin cá nhân và giao dịch VNPay).
    - `RefundPolicyView.vue` (Chính sách hoàn tiền: quy định trước giờ chiếu ít nhất 2 tiếng, không áp dụng cho vé đã check-in).
    - `FaqView.vue` (Câu hỏi thường gặp: hướng dẫn đặt vé, thanh toán, nhận vé).
    - `AboutUsView.vue` (Giới thiệu CineBook).
  - `frontend/src/router/index.ts`: Đăng ký 5 routes công khai dưới layout `DefaultLayout`.

---

### PHASE 1: USER & SECURITY MANAGEMENT

#### Nhiệm vụ 1.1: Mở Rộng API & UI Cập Nhật Người Dùng Phía Admin
- **Mục tiêu**: Admin có thể quản trị họ tên, số điện thoại, trạng thái và vai trò của tài khoản người dùng, đồng thời có cơ chế bảo vệ tài khoản quản trị.
- **Thay đổi Backend**:
  - `src/main/java/com/cinebook/dto/request/AdminUpdateUserRequest.java` [NEW]: Validation `@NotBlank fullName`, `@Pattern phone`, `UserStatus status`, `Set<String> roles`.
  - `src/main/java/com/cinebook/controller/AdminUserController.java`: Thêm endpoint `PUT /api/v1/admin/users/{id}`.
  - `src/main/java/com/cinebook/service/impl/UserServiceImpl.java`:
    - Chặn Admin tự đổi role của chính mình (chặn tự hạ quyền).
    - Chặn Admin tự khóa tài khoản của chính mình.
    - Triển khai **Last-Admin Protection**: Nếu hệ thống chỉ còn 1 Admin active duy nhất, cấm thao tác khóa hoặc tước quyền tài khoản này.
- **Thay đổi Frontend**:
  - `frontend/src/views/admin/AdminUsersView.vue`: Nâng cấp Modal chỉnh sửa người dùng đầy đủ (họ tên, sđt, trạng thái, chọn vai trò).

#### Nhiệm vụ 1.2: Xác Thực Email (Email Verification — Non-blocking V1)
- **Mục tiêu**: Hoàn thiện vòng đời `email_verified` qua email kích hoạt có mã token bảo mật.
- **Thay đổi Database**: Thực thi script `V1_1__add_email_verification_tokens.sql` (tạo bảng mới).
- **Thay đổi Backend**:
  - Entity: `EmailVerificationToken.java` [NEW].
  - Repository: `EmailVerificationTokenRepository.java` [NEW].
  - Service: `EmailService.java` (thêm `sendVerificationEmail`), `AuthServiceImpl.java` (sinh token khi đăng ký, TTL 24h).
  - Controller: `AuthController.java` (thêm `POST /api/v1/auth/verify-email` và `POST /api/v1/auth/resend-verification` có rate limit 60s).
- **Thay đổi Frontend**:
  - `frontend/src/views/auth/VerifyEmailView.vue` [NEW]: Nhận token từ link email và hiển thị kết quả.
  - Banner nhắc nhở trên Profile: Nhắc nhở người dùng kích hoạt email để nhận thông báo và vé điện tử.

---

### PHASE 2: GENRE MANAGEMENT & TMDB SYNC

#### Nhiệm vụ 2.1: Xây Dựng Giao Diện Quản Lý Thể Loại
- **Mục tiêu**: Kết nối giao diện Admin với backend CRUD Thể loại và tính năng đồng bộ TMDB đã có sẵn.
- **Thay đổi Frontend**:
  - `frontend/src/types/genre.types.ts` [NEW]: Định nghĩa `GenreResponse`, `CreateGenreRequest`, `UpdateGenreRequest`.
  - `frontend/src/services/genre.service.ts` [NEW]: Wrapper gọi `/api/v1/admin/genres` và `/api/v1/admin/tmdb/genres/sync`.
  - `frontend/src/views/admin/AdminGenresView.vue` [NEW]: Bảng danh sách thể loại, Modal Thêm/Sửa/Xóa và nút **"Đồng bộ thể loại từ TMDB"**.
  - `frontend/src/router/index.ts` & `AdminLayout.vue`: Đăng ký route và thêm Menu Sidebar "Thể loại phim".

#### Nhiệm vụ 2.2: Tích Hợp Thể Loại Vào Modal Phim Admin
- **Thay đổi Frontend**:
  - `frontend/src/views/admin/AdminMoviesView.vue`: Thay trường nhập text thể loại bằng Multi-Select Dropdown tải động từ `genreService.getAllGenres()`.

---

### PHASE 3: COMMERCIAL EXPANSION — F&B & NOTIFICATIONS

#### Nhiệm vụ 3.1: Phân Hệ Bắp Nước & Combo (F&B)
- **Mục tiêu**: Cho phép khách hàng chọn mua thêm bắp nước/combo khi đặt vé, tự động tính tổng thanh toán `grandTotal`.
- **Thay đổi Database**: Thực thi script `V1_2__add_food_and_concessions.sql` (bảng `food_items`, `booking_foods`, cột `bookings.total_food_amount`).
- **Thay đổi Backend**:
  - Entities: `FoodItem.java`, `BookingFood.java`, cập nhật `Booking.java`.
  - Repositories: `FoodItemRepository.java`, `BookingFoodRepository.java`.
  - Services: `FoodItemService.java`, `FoodItemServiceImpl.java`.
  - Cập nhật `BookingServiceImpl.java`: Tiếp nhận mảng `foods`, snapshot giá bắp nước vào `booking_foods`, tính $\text{grossAmount} = \text{ticketAmount} + \text{foodAmount}$ và $\text{grandTotal} = \text{grossAmount} - \text{discountAmount}$.
  - Controllers: `FoodItemController.java` (Public xem món), `AdminFoodItemController.java` (Admin CRUD).
- **Thay đổi Frontend**:
  - `frontend/src/types/food.types.ts` [NEW], `frontend/src/services/food.service.ts` [NEW].
  - `frontend/src/views/admin/AdminFoodsView.vue` [NEW]: Quản lý danh mục bắp nước.
  - `frontend/src/components/booking/BookingFoodSelection.vue` [NEW]: Bước 2 trong luồng đặt vé.
  - Cập nhật `BookingSummary.vue`: Hiển thị chi tiết tiền vé, tiền bắp nước, giảm giá và tổng thanh toán.

#### Nhiệm vụ 3.2: Phân Hệ Thông Báo Nội Bộ (In-App Notifications)
- **Mục tiêu**: Người dùng nhận được thông báo trong hệ thống khi đặt vé thành công hoặc có cập nhật giao dịch.
- **Thay đổi Database**: Thực thi script `V1_3__add_user_notifications.sql` (bảng `notifications`).
- **Thay đổi Backend**:
  - Entity: `Notification.java`.
  - Repository: `NotificationRepository.java`.
  - Service: `NotificationService.java`, `NotificationServiceImpl.java`.
  - Controller: `NotificationController.java` (`GET /api/v1/notifications`, `PATCH /{id}/read`, `PATCH /read-all`, `GET /unread-count`).
  - Trigger tự động trong `BookingServiceImpl.confirmPaidBooking` và `PaymentServiceImpl.completeRefundTransaction`.
- **Thay đổi Frontend**:
  - `frontend/src/components/common/NotificationBell.vue` [NEW]: Icon chuông trên Navbar kèm badge đỏ và popover danh sách.
  - `frontend/src/services/notification.service.ts` [NEW].

---

### PHASE 4: BUSINESS ANALYTICS & REPORTING

#### Nhiệm vụ 4.1: Sửa Lỗi Param & Thêm Nút Xuất Excel XLSX / CSV Phía Admin
- **Mục tiêu**: Admin có thể xuất dữ liệu báo cáo ra file Excel (.xlsx) định dạng chuẩn hoặc CSV.
- **Thay đổi Frontend**:
  - `frontend/src/services/report.service.ts` (dòng 79): Đổi tên param `type` thành `reportType` để khớp với `@RequestParam ReportType reportType` của `AdminReportController.java`.
  - `frontend/src/views/admin/AdminReportsView.vue`:
    - Thêm Dropdown nút bấm: "Xuất báo cáo Excel (.xlsx)" và "Xuất báo cáo CSV (.csv)".
    - Xử lý tải file nhị phân Blob về máy tính người dùng.
- **Ràng buộc**: Tuyệt đối không thêm dependency Apache POI mới vào `pom.xml`.

#### Nhiệm vụ 4.2: Tối Ưu Hóa & Đồng Bộ Toàn Bộ Tài Liệu
- Chạy toàn bộ test regression (`.\mvnw.cmd clean test`, `npm run build`).
- Cập nhật đồng bộ các file tài liệu trong `docs/`.

---

## 4. Kế Hoạch Đồng Bộ Tài Liệu Sau Triển Khai (Docs Staleness Prevention)

Sau khi mỗi Phase hoàn tất, thực hiện cập nhật ngay 5 tài liệu trung tâm:
1. `docs/api.md`: Thêm hợp đồng API mới cho Auth, Admin Users, F&B, Notifications, Export.
2. `docs/database.md`: Cập nhật schema các bảng mới (`email_verification_tokens`, `food_items`, `booking_foods`, `notifications`).
3. `docs/business-rules.md`: Bổ sung quy tắc tính tiền bắp nước `grandTotal`, quy tắc ghế đơn lẻ No Single Orphan Seat.
4. `docs/documentation-map.md`: Cập nhật cấu trúc thư mục, số lượng tests thực tế (572+), danh mục views và components mới.
5. `docs/use-cases/`: Cập nhật `booking.md`, `administration.md`, `reporting.md`.

---

## 5. Tiêu Chuẩn Nghiệm Thu Cho Từng Phase (Definition of Done)

Một Phase được coi là hoàn thành khi và chỉ khi:
- [ ] Tính năng hoạt động đúng theo đặc tả và các quyết định đã khóa (`DECISION-001` đến `DECISION-014`).
- [ ] Mọi thay đổi CSDL tuân thủ nguyên tắc Additive Only, Hibernate `ddl-auto: validate` kiểm tra thành công.
- [ ] Toàn bộ test suite backend (`.\mvnw.cmd test`) đạt kết quả 100% PASS, không gây hồi quy.
- [ ] Frontend build (`npm run build`) và typecheck (`npm run typecheck`) đạt kết quả 100% PASS (0 errors).
- [ ] Không có thông tin nhạy cảm, credentials hay API keys bị hardcode.
- [ ] Toàn bộ tài liệu liên quan trong `docs/` được cập nhật đồng bộ.
