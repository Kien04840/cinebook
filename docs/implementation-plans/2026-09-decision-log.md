# CineBook — Sổ Ghi Nhận Quyết Định Kiến Trúc & Nghiệp Vụ (Decision Lock Log)
## DECISION-001 Đến DECISION-014 (Phiên Bản Đã Xác Minh Thực Tế)

> **Mục đích**: Tài liệu ghi nhận các quyết định kiến trúc, nghiệp vụ và kỹ thuật đã được phân tích, đối chiếu thực tế và khóa lại (Locked Decisions) cho 5 giai đoạn phát triển tiếp theo của CineBook.  
> **Nguyên tắc**: Dữ liệu và code thực tế là chân lý tối thượng. Không suy đoán, mọi quyết định đều căn cứ trên bằng chứng (evidence) từ mã nguồn và database.  
> **Ngày lập**: Tháng 09/2026  
> **Trạng thái**: LOCKED & APPROVED FOR PLANNING  

---

## Bảng Tra Cứu Nhanh 14 Quyết Định

| Mã Quyết Định | Tên Quyết Định | Phạm vi Phase | Trạng thái |
|---|---|---|---|
| **DECISION-001** | Chuẩn hóa danh mục Thành phố (City Normalization Strategy) | Phase 0.1 | **LOCKED** |
| **DECISION-002** | Ngữ nghĩa Hủy thanh toán VNPay & Chuyển đổi trạng thái Booking (5 phút TTL) | Phase 0.2 | **LOCKED** |
| **DECISION-003** | Quy tắc Ghế đơn lẻ (Seat Adjacency & Orphan Single Seat Validation) | Phase 0.4 | **LOCKED** |
| **DECISION-004** | Giá hiển thị Suất chiếu & Nguồn chân lý duy nhất tính giá (Pricing SSOT) | Phase 0.3 | **LOCKED** |
| **DECISION-005** | Phạm vi Cập nhật Người dùng dành cho Admin & Last-Admin Protection | Phase 1.1 | **LOCKED** |
| **DECISION-006** | Kiến trúc & Quy trình Xác thực Email (Non-blocking V1) | Phase 1.2 | **LOCKED** |
| **DECISION-007** | Quản lý Thể loại Phim & Đồng bộ TMDB trên Giao diện Admin | Phase 2.1 | **LOCKED** |
| **DECISION-008** | Thiết kế Phân hệ Bắp nước / Combo (F&B / Concession Domain Design) | Phase 3.1 | **LOCKED** |
| **DECISION-009** | Kiến trúc Hệ thống Thông báo Nội bộ (In-App Notification Architecture) | Phase 3.2 | **LOCKED** |
| **DECISION-010** | Tích hợp Xuất Báo cáo Excel XLSX (Apache POI) & CSV phía Admin | Phase 4.1 | **LOCKED** |
| **DECISION-011** | Chuẩn hóa & Tích hợp Các trang Chính sách Tĩnh (Refund >= 2 Giờ) | Phase 0.5 | **LOCKED** |
| **DECISION-012** | Chiến lược Database Migration An toàn & Quy Tắc Không Destructive Rollback | Toàn bộ Phase | **LOCKED** |
| **DECISION-013** | Chiến lược Kiểm thử Tự động Hướng Rủi ro (Audit 572 Tests Thực Tế) | Toàn bộ Phase | **LOCKED** |
| **DECISION-014** | Kế hoạch Đồng bộ Tài liệu & Triệt Tiêu Documentation Staleness | Toàn bộ Phase | **LOCKED** |

---

## Chi Tiết 14 Quyết Định Đã Khóa

### DECISION-001: Chuẩn Hóa Danh Mục Thành Phố (City Normalization Strategy)

- **Bối cảnh & Hiện trạng Code/Database**:
  - Database bảng `cinemas` chứa 29 bản ghi rạp. Cột `city` hiện tại chỉ lưu đúng 3 giá trị tiếng Anh: `'Hanoi'`, `'Da Nang'`, `'Ho Chi Minh City'`.
  - Tài liệu chuẩn `docs/use-cases/cinema.md` (dòng 36-40) nêu rõ: *"The system currently targets cinemas in three cities only: Hanoi, Da Nang, Ho Chi Minh City"*.
  - Backend: `Cinema.java` (`city varchar(100)`), `CinemaSpecification.hasCity` lọc theo `lower(city) = lower(param)`.
  - **Lỗi thực tế ở Frontend (`VERIFIED_BUG`)**: `frontend/src/views/admin/AdminCinemasView.vue` (dòng 150) đang hardcode mảng tiếng Việt: `['Hà Nội', 'TP. Hồ Chí Minh', 'Đà Nẵng', 'Hải Phòng', 'Cần Thơ', 'Nha Trang']`. Khi Admin chọn filter hoặc lưu rạp, chuỗi gửi lên là `'Hà Nội'` thay vì `'Hanoi'`, khiến rạp lọc không ra dữ liệu và dropdown edit không hiển thị được giá trị hiện tại.
- **Quyết định đã khóa**:
  1. Giữ nguyên cột `city` trong DB là canonical string: `'Hanoi'`, `'Da Nang'`, `'Ho Chi Minh City'`. Tuyệt đối không sửa đổi phá vỡ dữ liệu 29 rạp đang hoạt động. Không tạo bảng `cities` riêng biệt.
  2. Tạo danh sách hằng số dùng chung tại `frontend/src/utils/constants.ts` với cặp Value/Label đồng nhất:
     - Value: `'Hanoi'` → Label VI: `'Hà Nội'` | Label EN: `'Hanoi'`
     - Value: `'Da Nang'` → Label VI: `'Đà Nẵng'` | Label EN: `'Da Nang'`
     - Value: `'Ho Chi Minh City'` → Label VI: `'TP. Hồ Chí Minh'` | Label EN: `'Ho Chi Minh City'`
  3. Sửa `AdminCinemasView.vue` để dropdown lưu value là canonical DB string, chỉ hiển thị label tiếng Việt cho người dùng.

---

### DECISION-002: Ngữ Nghĩa Hủy Thanh Toán VNPay & Chuyển Đổi Trạng Thái Booking (5 Phút TTL)

- **Bối cảnh & Hiện trạng Code/Database**:
  - Trong `PaymentServiceImpl.java` (dòng 316-328 tại `processIpn` và dòng 413-426 tại `processReturn`):
    Khi khách hàng bấm hủy giao dịch trên cổng thanh toán VNPay (`vnp_ResponseCode = "24"`), backend cập nhật:
    `payment.setPaymentStatus(PaymentStatus.CANCELLED)`.
  - `Booking` **vẫn giữ nguyên trạng thái `PENDING_PAYMENT`**, và các ghế giữ chỗ trong bảng `seat_holds` **vẫn được bảo lưu** trong thời hạn giữ chỗ **5 phút** (`HOLD_DURATION_MINUTES = 5`).
  - **Lỗi giao diện (`PARTIALLY_IMPLEMENTED`)**: `PaymentResultView.vue` hiển thị thông báo giao dịch bị hủy nhưng thiếu nút "Thanh toán lại" và nút "Hủy đơn", khiến người dùng bị kẹt ghế không thể thử lại.
- **Quyết định đã khóa**:
  1. Giữ nguyên nguyên lý bất biến: Khi VNPay trả về mã `24` (Khách hủy trên cổng), chỉ có giao dịch `Payment` bị đánh dấu `CANCELLED`. Đơn hàng `Booking` **vẫn được bảo lưu ở trạng thái `PENDING_PAYMENT`** nếu thời hạn giữ chỗ 5 phút (`holdExpiresAt > now`) vẫn còn hiệu lực.
  2. Tại giao diện `PaymentResultView.vue`, khi thanh toán bị hủy hoặc thất bại mà thời gian giữ chỗ vẫn còn:
     - Hiển thị đồng hồ đếm ngược thời gian giữ chỗ 5 phút còn lại.
     - Cung cấp nút **"Thanh toán lại"**: Cho phép user tạo một phiên thanh toán VNPay mới (`POST /api/v1/payments/create-url`) mà không phải chọn lại ghế từ đầu và không tạo mới `Booking`.
     - Cung cấp nút **"Hủy đơn hàng"**: Gọi `POST /api/v1/bookings/{id}/cancel` để lập tức giải phóng ghế cho người khác và đưa `Booking` về `CANCELLED`.
  3. Khi hết hạn 5 phút: Booking tự động chuyển thành `EXPIRED` qua cơ chế Lazy Expiration hoặc Scheduled Task (`BookingCleanupTask`), ghế được trả về `AVAILABLE`.

---

### DECISION-003: Quy Tắc Ghế Đơn Lẻ (Seat Adjacency & Orphan Single Seat Validation)

- **Bối cảnh & Hiện trạng Code/Database**:
  - Hiện tại CineBook chưa có bất kỳ logic nào kiểm tra khoảng trống ghế đơn độc (orphan single seat) trong `seatGrid.ts`, `SeatMap.vue` lẫn `BookingServiceImpl.java` (`VERIFIED_MISSING`).
  - Khách hàng có thể cố tình đặt ghế cách 1 ghế trống so với người khác, để lại các ghế trống đơn lẻ không bán được.
- **Quyết định đã khóa**:
  1. Triển khai quy tắc kiểm tra ghế đơn độc (**No Single Orphan Seat Rule**) cho các hàng ghế Standard và VIP:
     - *"Không được phép để lại đúng 1 ghế trống duy nhất giữa các ghế đã chọn/đã đặt, hoặc giữa ghế đã chọn với đầu/cuối của hàng ghế hợp lệ."*
  2. Thuật toán chi tiết:
     - Xét theo từng hàng ghế (Row-by-Row).
     - Chỉ xét các ghế vật lý có trạng thái `ACTIVE` (bỏ qua ghế `BROKEN`, các ô lối đi / spacer trống).
     - Ghế đôi Couple có `capacity = 2` (chiếm 2 cột) được coi là một khối thống nhất, không tính là ghế đơn lẻ.
     - **Quy tắc bỏ qua (Exemption)**: Không chặn nếu hàng ghế đó vốn dĩ đã có sẵn ghế đơn lẻ từ trước (pre-existing orphan seats) do các giao dịch trước đó hoặc do kiến trúc phòng chiếu.
  3. Triển khai 2 tầng:
     - **Tầng Client (Frontend - `seatAdjacency.ts` & `SeatMap.vue`)**: Tính toán ngay khi user click chọn ghế. Nếu vi phạm, hiển thị cảnh báo toast và disable nút "Tiếp tục".
     - **Tầng Server (Backend - `BookingServiceImpl.createBooking`)**: Chốt chặn nghiệp vụ bắt buộc. Nếu vi phạm, quăng `BadRequestException("Lựa chọn ghế của bạn để lại ghế trống đơn lẻ ở hàng...")`.

---

### DECISION-004: Giá Hiển Thị Suất Chiếu & Nguồn Chân Lý Duy Nhất Tính Giá (Pricing SSOT)

- **Bối cảnh & Hiện trạng Code/Database**:
  - `ShowtimeSummaryResponse.java:38` hiện chỉ chứa trường `private BigDecimal basePrice`.
  - Frontend `ShowtimeBrowser.vue:316` và `ShowtimesView.vue:505` hiển thị: `{{ formatCurrency(st.basePrice) }}`.
  - Tuy nhiên, backend đã có hệ thống tính giá động hoàn chỉnh tại `PricingServiceImpl.java:25-28`:
    `Ticket Price = Showtime Base Price + Seat Type Modifier + Day Modifier + Time Slot Modifier`.
  - Hậu quả: Một suất chiếu tối thứ 7 có phụ thu cuối tuần (+15.000₫) và khung giờ tối (+10.000₫) thì giá vé thực tế của ghế Standard thấp nhất là 115.000₫. Nhưng khách hàng nhìn ở danh sách lịch chiếu bên ngoài chỉ thấy 90.000₫ (`basePrice`), tạo cảm giác bị đội giá khi vào chọn ghế.
- **Quyết định đã khóa**:
  1. Bảo vệ nguyên tắc Single Source of Truth của `PricingService`: Không tính giá phân tán ở Controller hay Frontend.
  2. Bổ sung trường `minPrice` vào `ShowtimeSummaryResponse`:
     - Giá trị này được tính toán bởi `PricingService.calculateShowtimeBaseBreakdown(showtime)` đại diện cho giá khởi điểm thực tế thấp nhất có thể mua của suất chiếu (`basePrice + dayModifier + timeSlotModifier`).
  3. Frontend cập nhật hiển thị: `Từ {{ formatCurrency(st.minPrice || st.basePrice) }}`. Khách hàng nhìn thấy giá minh bạch ngay từ màn hình lịch chiếu.

---

### DECISION-005: Phạm Vi Cập Nhật Người Dùng Phía Admin & Last-Admin Protection

- **Bối cảnh & Hiện trạng Code/Database**:
  - `AdminUserController.java` (dòng 1-46) hiện chỉ có 2 endpoint:
    - `GET /api/v1/admin/users`: Tìm kiếm và phân trang người dùng.
    - `PATCH /api/v1/admin/users/{id}/status`: Cập nhật trạng thái `ACTIVE`, `INACTIVE`, `BLOCKED`.
  - Phía Admin chưa có endpoint cập nhật thông tin cá nhân (họ tên, số điện thoại) hay phân quyền (Role) cho User (`PARTIALLY_IMPLEMENTED`).
- **Quyết định đã khóa**:
  1. Mở rộng `AdminUserController` với endpoint: `PUT /api/v1/admin/users/{id}`.
  2. Phạm vi các trường Admin được phép cập nhật:
     - `fullName` (Họ và tên, tối đa 100 ký tự).
     - `phone` (Số điện thoại, duy nhất).
     - `status` (`ACTIVE`, `INACTIVE`, `BLOCKED`).
     - `roles` (Gán danh sách Role: `ROLE_CUSTOMER`, `ROLE_ADMIN`).
  3. **Ràng buộc an toàn tuyệt đối**:
     - Không cho phép cập nhật mật khẩu (`passwordHash`), token hay secrets qua API này.
     - **Chặn tự hạ quyền/tự khóa**: Admin không thể tự đổi status của chính mình thành `BLOCKED`/`INACTIVE` và không thể tự tước quyền `ROLE_ADMIN` của chính mình (`id != SecurityUtils.getCurrentUserId()`).
     - **Last-Admin Protection**: Nếu hệ thống chỉ còn đúng 1 Admin duy nhất ở trạng thái `ACTIVE`, cấm mọi thao tác khóa tài khoản hoặc tước quyền tài khoản này nhằm ngăn chặn tình trạng hệ thống mồ côi quản trị viên.

---

### DECISION-006: Kiến Trúc & Quy Trình Xác Thực Email (Non-Blocking V1)

- **Bối cảnh & Hiện trạng Code/Database**:
  - Entity `User.java` (dòng 50-51) và bảng `users` **ĐÃ CÓ CỘT** `email_verified boolean not null`.
  - Trong `AuthServiceImpl.java:126`, khi người dùng đăng ký mới: `user.setEmailVerified(false)`.
  - Hệ thống chưa có bảng lưu mã xác thực, `EmailService.java` chưa có phương thức gửi email xác thực tài khoản, và chưa có endpoint xác thực token (`PARTIALLY_IMPLEMENTED`).
- **Quyết định đã khóa**:
  1. Tạo bảng mới `email_verification_tokens` (id, user_id, token varchar(100) unique, expires_at, created_at) với TTL = 24 giờ.
  2. Bổ sung vào `EmailService.java`: `void sendVerificationEmail(String toEmail, String token)`.
  3. Xây dựng API:
     - `POST /api/v1/auth/verify-email?token=...`: Xác thực token hợp lệ, gán `user.emailVerified = true`, xóa token đã dùng.
     - `POST /api/v1/auth/resend-verification`: Gửi lại email xác thực (có Rate Limit tối thiểu 60 giây/lần).
  4. **Chính sách nghiệp vụ (V1 Non-blocking)**: Để không làm gián đoạn trải nghiệm người dùng trong đồ án tốt nghiệp, người dùng chưa xác thực email **vẫn được phép đăng nhập và đặt vé bình thường**, nhưng hệ thống sẽ hiển thị một thông báo nhẹ (Notification banner) trên trang cá nhân nhắc nhở xác thực để nhận vé qua email.

---

### DECISION-007: Quản Lý Thể Loại Phim & Đồng Bộ TMDB Trên Giao Diện Admin

- **Bối cảnh & Hiện trạng Code/Database**:
  - **Backend đã hoàn chỉnh 100% (`VERIFIED_EXISTING`)**:
    - `Genre.java`, `MovieGenre.java`, `GenreRepository.java`, `GenreServiceImpl.java`.
    - `AdminGenreController.java` (dòng 1-64): Đầy đủ CRUD `GET`, `POST`, `PUT`, `DELETE /api/v1/admin/genres`.
    - `AdminTmdbController.java` (dòng 35-37): Đã có API `POST /api/v1/admin/tmdb/genres/sync` gọi `tmdbImportService.syncGenres()`.
  - **Frontend hoàn toàn chưa có gì (`VERIFIED_MISSING`)**: Chưa có `genre.service.ts`, `genre.types.ts`, chưa có trang `AdminGenresView.vue`. Trong form tạo phim ở `AdminMoviesView.vue`, trường chọn thể loại chưa được kết nối với API genres.
- **Quyết định đã khóa**:
  1. Giữ nguyên 100% Backend API hiện có, không viết lại hay thay đổi contract.
  2. Xây dựng phía Frontend:
     - Tạo `genre.service.ts` và `genre.types.ts`.
     - Xây dựng giao diện `AdminGenresView.vue` với danh sách bảng thể loại, modal thêm/sửa, nút xóa, và nút **"Đồng bộ thể loại từ TMDB"** (gọi `/api/v1/admin/tmdb/genres/sync`).
     - Đăng ký route `/admin/genres` trong `router/index.ts` và thêm menu item "Thể loại phim" vào Sidebar `AdminLayout.vue`.
     - Tích hợp chọn Multi-select Thể loại trong Modal Tạo/Sửa phim của `AdminMoviesView.vue`.

---

### DECISION-008: Thiết Kế Phân Hệ Bắp Nước / Combo (F&B Domain Design)

- **Bối cảnh & Hiện trạng Code/Database**:
  - Toàn bộ cơ sở dữ liệu và source code CineBook hiện tại chưa có bất kỳ bảng hoặc entity nào về bắp nước / thức ăn nhẹ (`VERIFIED_MISSING`).
- **Quyết định đã khóa**:
  1. Thiết kế cơ sở dữ liệu mở rộng (Non-destructive):
     - Bảng `food_items` (id, name, description, price, image_url, category, is_active, display_order, created_at, updated_at).
     - Bảng `booking_foods` (id, booking_id, food_item_id, food_name snapshot, quantity, unit_price snapshot, total_price, created_at).
     - Thêm cột `total_food_amount decimal(12,2) NOT NULL DEFAULT 0.00` vào bảng `bookings`.
  2. Công thức tính tiền tổng:
     $$\text{grossAmount} = \text{totalTicketAmount} + \text{totalFoodAmount}$$
     $$\text{grandTotal} = \max(0, \text{grossAmount} - \text{discountAmount})$$
  3. Bảo toàn các nguyên tắc bất biến:
     - Giá bắp nước được snapshot bất biến vào `booking_foods.unit_price`. Khi Admin đổi giá món sau này, các đơn hàng cũ không bị ảnh hưởng.
     - Các quy tắc Promotion hiện có (min order, max discount, one promotion per booking) được bảo toàn trọn vẹn.
     - Cổng VNPay chỉ thanh toán cho giá trị cuối cùng `booking.totalAmount`.
     - **Giới hạn phạm vi V1**: Không xây dựng hệ thống quản lý kho, bếp, vận chuyển hay xuất nhập tồn.

---

### DECISION-009: Kiến Trúc Hệ Thống Thông Báo Nội Bộ (In-App Notification Architecture)

- **Bối cảnh & Hiện trạng Code/Database**:
  - CineBook hiện tại chưa có bảng thông báo trong hệ thống (`VERIFIED_MISSING`).
- **Quyết định đã khóa**:
  1. Thiết kế bảng `notifications` (id, user_id, title, message, type, is_read, reference_type, reference_id, created_at).
  2. Cơ chế hoạt động: REST Polling định kỳ từ Client (không dùng WebSocket, không dùng Redis hay RabbitMQ để tối ưu tài nguyên theo nguyên tắc Monolith).
  3. Kích hoạt thông báo tự động tại các mốc giao dịch chính:
     - Khi đặt vé thành công (`confirmPaidBooking`).
     - Khi đơn vé bị hủy hoặc hoàn tiền (`processRefund`).
  4. Giao diện người dùng: Component `NotificationBell.vue` trên Navbar với badge hiển thị số lượng chưa đọc, popover danh sách thông báo và nút "Đánh dấu tất cả đã đọc".

---

### DECISION-010: Tích Hợp Xuất Báo Cáo Excel XLSX (Apache POI) & CSV Phía Admin

- **Bối cảnh & Hiện trạng Code/Database**:
  - Thư viện `org.apache.poi:poi-ooxml:5.4.0` **ĐÃ CÓ SẴN** trong `pom.xml` (dòng 78-81).
  - Backend `ReportServiceImpl.java` (dòng 642-772) **ĐÃ IMPLEMENT HOÀN CHỈNH** hàm xuất Excel XLSX bằng POI và hàm xuất CSV UTF-8.
  - `AdminReportController.java` (dòng 156-183) **ĐÃ CÓ** endpoint `GET /api/v1/admin/reports/export`.
  - **Lỗi nhỏ ở Frontend (`PARTIALLY_IMPLEMENTED`)**: `report.service.ts:79` truyền query param là `type` trong khi backend chờ `reportType`. Giao diện `AdminReportsView.vue` thiếu nút bấm gọi export.
- **Quyết định đã khóa**:
  1. **Tuyệt đối không thêm dependency Apache POI mới vào `pom.xml`**.
  2. Sửa `report.service.ts` khớp query param `reportType: type`.
  3. Thêm Dropdown nút bấm "Xuất dữ liệu" trên giao diện `AdminReportsView.vue` hỗ trợ cả hai định dạng: "Xuất Excel (.xlsx)" và "Xuất CSV (.csv)".
  4. Hỗ trợ đầy đủ cho 4 loại báo cáo: Doanh thu theo thời gian, Báo cáo phim, Báo cáo cụm rạp, Tỷ lệ lấp đầy suất chiếu.

---

### DECISION-011: Chuẩn Hóa & Tích Hợp Các Trang Chính Sách Tĩnh (Refund >= 2 Giờ)

- **Bối cảnh & Hiện trạng Code/Database**:
  - Trong `frontend/src/layouts/DefaultLayout.vue` (dòng 357-360), phần footer có 4 liên kết `/terms`, `/privacy`, `/refund`, `/faq`.
  - Router `frontend/src/router/index.ts` **chưa đăng ký** các route này, dẫn đến việc người dùng click vào footer sẽ bị văng ra trang **404 Not Found (`VERIFIED_BUG`)**.
- **Quyết định đã khóa**:
  1. Tạo các Vue views tĩnh tại `frontend/src/views/static/`:
     - `TermsOfUseView.vue`
     - `PrivacyPolicyView.vue`
     - `RefundPolicyView.vue`
     - `FaqView.vue`
     - `AboutUsView.vue`
  2. Đăng ký các route trên trong `frontend/src/router/index.ts` dưới nhóm `DefaultLayout` (public).
  3. Nội dung chính sách bám sát 100% nghiệp vụ thực tế của CineBook:
     - **Thời hạn giữ chỗ vé**: Đúng 5 phút.
     - **Chính sách hoàn vé khách hàng**: Bắt buộc yêu cầu trước giờ chiếu **ít nhất 2 tiếng (>= 2 hours)**.
     - Không phóng đại các cam kết bảo mật ngoài phạm vi hệ thống.

---

### DECISION-012: Chiến Lược Database Migration An Toàn & Quy Tắc Không Destructive Rollback

- **Bối cảnh & Hiện trạng Code/Database**:
  - Dự án cấu hình Hibernate `ddl-auto: validate` trong `application.yml`. Cơ sở dữ liệu MySQL 8 cục bộ là nguồn chân lý duy nhất.
- **Quyết định đã khóa**:
  1. Toàn bộ thay đổi schema là **ADDITIVE ONLY**: Chỉ tạo bảng mới hoặc thêm cột mới có `DEFAULT` an toàn.
  2. **Quy tắc Rollback trên Database thật**:
     - Tuyệt đối không thực thi các lệnh destructive như `DROP TABLE` hay `DROP COLUMN` trên môi trường database chứa dữ liệu thật.
     - Phân định rõ 3 chiến lược:
       - **Logical Rollback**: Tắt feature flags hoặc ẩn UI để ngừng sử dụng bảng/cột mới mà không xóa dữ liệu.
       - **Database Restore**: Phục hồi từ bản sao lưu (Backup snapshot) tạo trước khi chạy migration.
       - **Forward-Fix Migration**: Tạo script migration tiếp theo để sửa đổi hoặc đánh dấu deprecated một cách an toàn.

---

### DECISION-013: Chiến Lược Kiểm Thử Tự Động Hướng Rủi Ro (Audit 572 Tests Thực Tế)

- **Bối cảnh & Hiện trạng Code/Database**:
  - Kiểm tra thực tế bằng lệnh `.\mvnw.cmd test` ghi nhận hệ thống có **572 tests** (570 PASS, 2 failures do lệch dữ liệu phòng chiếu trên DB local).
  - Frontend typecheck (`npm run typecheck`) và build (`npm run build`) đạt kết quả 100% PASS.
- **Quyết định đã khóa**:
  1. Mọi tính năng mới trong 5 phase đều phải có bộ test tương ứng (Service Unit Tests với Mockito, Controller Tests với MockMvc).
  2. Tập trung kiểm thử các điểm nóng rủi ro:
     - Tính đúng đắn của công thức `grandTotal` khi có bắp nước và mã giảm giá.
     - Kiểm tra thuật toán bắt lỗi quy tắc ghế đơn lẻ (Seat Adjacency Validation).
     - Kiểm tra phân quyền cập nhật người dùng của Admin (chặn tự hạ quyền, Last-Admin Protection).
     - Kiểm tra tính hợp lệ và hết hạn của Token xác thực Email.
  3. Giải quyết dứt điểm 2 test failures của `AuditoriumNormalizationLiveIntegrationTest` tại Phase 0 bằng cách chuẩn hóa fixture hoặc dữ liệu phòng chiếu local.

---

### DECISION-014: Kế Hoạch Đồng Bộ Tài Liệu & Triệt Tiêu Documentation Staleness

- **Bối cảnh & Hiện trạng Code/Database**:
  - Các tài liệu cũ trong `docs/` chứa một số thông tin lệch pha (ví dụ ghi nhầm hold 10 phút, refund 60 phút, số lượng test 490/530).
- **Quyết định đã khóa**:
  1. Ngay sau khi mỗi Phase hoàn thành, bắt buộc rà soát và cập nhật 5 tài liệu trung tâm: `api.md`, `database.md`, `business-rules.md`, `documentation-map.md`, và các file `use-cases/`.
  2. Cập nhật các thông số đã xác minh: Hold TTL = 5 phút, Refund time = 2 giờ, Test count = 572 tests.
  3. Không để tồn tại bất kỳ tài liệu nào mô tả sai lệch so với hành vi thực tế của mã nguồn.
