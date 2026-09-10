# CineBook — Kế Hoạch Kiểm Thử Toàn Diện (Comprehensive Test Plan)
## Chiến Lược Kiểm Thử Hướng Rủi Ro & Bộ Tiêu Chuẩn Nghiệm Thu

> **Mục tiêu**: Định nghĩa ma trận kiểm thử, danh mục test case chi tiết và quy trình xác minh tính toàn vẹn hệ thống cho 5 Phase tiếp theo của CineBook.  
> **Hiện trạng bộ test thực tế (Audit tháng 09/2026)**:  
> - Backend: **572 tests** (570 PASS, 2 failures thuộc về Live Integration Test trên dữ liệu phòng chiếu DB local).  
> - Frontend Typecheck: `npm run typecheck` (`vue-tsc --noEmit`) đạt **100% PASS** (0 errors).  
> - Frontend Production Build: `npm run build` đạt **100% PASS** (build thành công trong 4.34s).  

---

## 1. Ma Trận Kiểm Thử Chi Tiết (Comprehensive Test Matrix)

| Tính Năng / Khu Vực | Backend Unit (Mockito) | Backend Integration (@SpringBootTest) | Controller (MockMvc) | Frontend (Component/Spec) | Manual / E2E Flow | Mức Độ Rủi Ro |
|---|---|---|---|---|---|---|
| **0.1 City Normalization** | Kiểm tra `CinemaSpecification` với canonical city `'Hanoi'` | Kiểm tra truy vấn rạp theo city | MockMvc `AdminCinemaControllerTest` | Kiểm tra constants và Dropdown binding | Lọc rạp trên Admin và trang chủ | **TRUNG BÌNH** |
| **0.2 VNPay Cancel & Re-pay** | `PaymentServiceImplTest`: code 24 $\rightarrow$ `Payment=CANCELLED`, `Booking=PENDING_PAYMENT` | `BookingPaymentResumeIntegrationTest`: Tạo URL thanh toán mới cùng booking | `PaymentControllerTest`: Xử lý return và IPN khi hủy | `PaymentResultView.vue`: Countdown 5 phút, nút Re-pay và Hủy | Thử hủy giao dịch trên VNPay Sandbox và bấm "Thanh toán lại" | **CAO (Tài chính/Ghế)** |
| **0.3 Pricing SSOT (minPrice)** | `PricingServiceImplTest`: Tính breakdown và `minPrice` chuẩn xác | Kiểm tra query showtime kèm pricing rules | `ShowtimeControllerTest`: DTO chứa `minPrice` | `ShowtimesView.vue`: Hiển thị "Từ ...₫" | Xem danh sách suất chiếu ngày cuối tuần và giờ tối | **TRUNG BÌNH** |
| **0.4 Seat Adjacency Rule** | `BookingServiceSeatAdjacencyTest`: Bắt lỗi để lại 1 ghế trống đơn lẻ | Kiểm tra giao dịch đặt vé song song có check adjacency | MockMvc `BookingControllerTest`: Quăng 400 Bad Request | `SeatMap.vue`: Toast cảnh báo và disable nút "Tiếp tục" | Chọn thử ghế lẻ cách 1 khoảng trống trên sơ đồ rạp | **CAO (Nghiệp vụ)** |
| **0.5 Static Policy Pages** | N/A (Static client-side) | N/A | N/A | Render test 5 view tĩnh; Router test không còn 404 | Bấm các liên kết chân trang footer | **THẤP** |
| **1.1 Admin User Management** | `UserServiceImplTest`: Cập nhật fullName, phone, roles, status | Kiểm tra bảo vệ Last-Admin | `AdminUserControllerTest`: Chặn tự hạ quyền, chặn tự khóa | `AdminUsersView.vue`: Modal Edit User | Admin cập nhật thông tin Customer; thử tự hạ quyền Admin | **CAO (Bảo mật)** |
| **1.2 Email Verification** | `EmailVerificationServiceTest`: Sinh token, hết hạn 24h, rate limit 60s | Kiểm tra lưu token vào CSDL và cập nhật `email_verified` | `AuthControllerTest`: Endpoints verify và resend | `VerifyEmailView.vue`: Tiếp nhận token từ URL | Đăng ký tài khoản mới và click link kích hoạt | **TRUNG BÌNH** |
| **2.1 Genre Management** | ĐÃ CÓ: `GenreServiceImplTest` (PASS) | ĐÃ CÓ: `GenreRepository` | ĐÃ CÓ: `AdminGenreControllerTest` (PASS) | `AdminGenresView.vue`: Danh sách, Thêm/Sửa/Xóa | Admin xem bảng thể loại và bấm nút đồng bộ TMDB | **THẤP** |
| **3.1 Bắp Nước & Combo F&B** | `FoodItemServiceTest`, `BookingServiceFoodTest`: Tính `grandTotal` | Kiểm tra snapshot giá bắp nước vào `booking_foods` | `FoodItemControllerTest`, `AdminFoodItemControllerTest` | `BookingFoodSelection.vue`: Chọn combo; `BookingSummary.vue` | Đặt vé kèm 1 bắp rang + 1 nước ngọt, thanh toán VNPay | **CỰC KỲ CAO (Tài chính)** |
| **3.2 In-App Notifications** | `NotificationServiceTest`: Tạo thông báo, mark read, unread count | Kiểm tra transaction trigger khi thanh toán thành công | `NotificationControllerTest`: REST endpoints | `NotificationBell.vue`: Badge số lượng và Popover | Đặt vé thành công và kiểm tra chuông thông báo | **TRUNG BÌNH** |
| **4.1 Excel POI & CSV Export** | ĐÃ CÓ: `ReportServiceImplTest` (PASS) | Kiểm tra dữ liệu báo cáo thật | `AdminReportControllerTest`: Quyền ADMIN export | `AdminReportsView.vue`: Nút xuất XLSX/CSV và tải file blob | Admin xuất file Excel doanh thu và mở bằng Microsoft Excel | **TRUNG BÌNH** |

---

## 2. Kịch Bản Kiểm Thử Trọng Yếu Bắt Buộc (Critical Test Cases)

### Kịch bản 1: VNPay Cancel và Khả Năng Thanh Toán Lại (5 Phút Hold)
- **Mục tiêu**: Đảm bảo khách hàng hủy thanh toán trên cổng VNPay không bị mất ghế và có thể thực hiện thanh toán lại nếu trong thời hạn 5 phút.
- **Dữ liệu kiểm thử**: Booking ID `B-001`, gồm ghế `A1, A2`, tạo lúc 10:00:00, `holdExpiresAt` = 10:05:00.
- **Các bước thực hiện**:
  1. Giả lập VNPay trả về `vnp_ResponseCode = "24"` lúc 10:02:00.
  2. Kiểm tra bảng `payments`: Bản ghi chuyển thành `PaymentStatus.CANCELLED`.
  3. Kiểm tra bảng `bookings`: `bookingStatus` **VẪN LÀ `PENDING_PAYMENT`**.
  4. Kiểm tra bảng `seat_holds`: 2 ghế `A1, A2` **VẪN ĐANG BỊ GIỮ CHỖ**.
  5. Người dùng bấm nút "Thanh toán lại" trên `PaymentResultView.vue`:
     - Gọi `POST /api/v1/bookings/B-001/payments` $\rightarrow$ Backend tạo bản ghi Payment mới ở trạng thái `PENDING` và trả về URL thanh toán VNPay mới.
  6. Sau 10:05:00 (quá 5 phút), nếu chưa thanh toán thành công $\rightarrow$ Hệ thống tự động chuyển `Booking` sang `EXPIRED` và xóa `seat_holds`.

### Kịch bản 2: Bắt Lỗi Quy Tắc Ghế Đơn Lẻ (Seat Adjacency Rule)
- **Mục tiêu**: Chặn tuyệt đối việc đặt ghế để lại 1 ghế trống đơn độc không bán được.
- **Các trường hợp kiểm thử**:
  - **Case 1 (Giữa hai ghế đã chọn/đặt)**: Hàng C có các ghế từ 1 đến 10. Ghế C1 đã bán, ghế C3 được chọn $\rightarrow$ Ghế C2 bị bỏ trống đơn độc $\rightarrow$ **CHẶN** (Quăng `BadRequestException`).
  - **Case 2 (Đầu hoặc cuối hàng)**: Hàng D bắt đầu từ ghế D1. Khách chọn ghế D2 $\rightarrow$ Ghế D1 bị bỏ trống đơn độc $\rightarrow$ **CHẶN**.
  - **Case 3 (Khoảng trống hợp lệ)**: Khách chọn D3 và D4, để lại D1 và D2 trống (2 ghế trống) $\rightarrow$ **CHO PHÉP**.
  - **Case 4 (Ghế đôi Couple)**: Hàng cuối có ghế Couple CP1 (chiếm cột 1-2), khách chọn CP2 (chiếm cột 3-4) $\rightarrow$ Không có ghế đơn lẻ nào $\rightarrow$ **CHO PHÉP**.
  - **Case 5 (Ghế đơn lẻ có sẵn từ trước)**: Hàng E vốn dĩ đã có 1 ghế lẻ E5 do sơ đồ thiết kế hoặc do giao dịch từ tuần trước $\rightarrow$ Khách đặt ghế E1, E2 ở phía xa $\rightarrow$ **CHO PHÉP** (Không chặn vì ghế đơn lẻ không phải do giao dịch này tạo ra).

### Kịch bản 3: Tính Toán Tài Chính Toàn Vẹn Khi Có Bắp Nước & Mã Giảm Giá
- **Mục tiêu**: Đảm bảo công thức tính `grandTotal` khớp 100% giữa Backend, Cổng thanh toán VNPay và Hóa đơn.
- **Công thức bất biến**:
  $$\text{grossAmount} = \text{totalTicketAmount} + \text{totalFoodAmount}$$
  $$\text{grandTotal} = \max(0, \text{grossAmount} - \text{discountAmount})$$
- **Dữ liệu kiểm thử**:
  - 2 vé VIP: $2 \times 110,000 = 220,000$ ₫
  - 1 Combo Solo: $75,000$ ₫ + 1 Nước ngọt: $30,000$ ₫ $\implies \text{totalFoodAmount} = 105,000$ ₫
  - Tổng gộp: $\text{grossAmount} = 220,000 + 105,000 = 325,000$ ₫
  - Mã giảm giá 10% (tối đa 50,000 ₫): Giảm $32,500$ ₫
  - Số tiền thanh toán: $\text{grandTotal} = 325,000 - 32,500 = 292,500$ ₫
- **Kiểm tra**:
  - `bookings.total_ticket_amount` = `220000.00`
  - `bookings.total_food_amount` = `105000.00`
  - `bookings.total_amount` = `292500.00`
  - Giá trị gửi sang VNPay: `vnp_Amount = 29250000` (nhân 100 đơn vị minor units).

### Kịch bản 4: Phân Quyền & Bảo Vệ Tài Khoản Quản Trị Viên (Last-Admin Protection)
- **Mục tiêu**: Ngăn chặn việc Admin vô tình hoặc cố ý tước quyền quản trị của chính mình hoặc làm hệ thống không còn Admin nào.
- **Kiểm tra**:
  - Admin A đăng nhập và gửi `PUT /api/v1/admin/users/{id-of-A}` với role chỉ có `ROLE_CUSTOMER` $\rightarrow$ Backend lập tức quăng `BadRequestException("Admin không thể tự hạ quyền quản trị của chính mình.")`.
  - Admin A gửi lệnh khóa tài khoản của chính mình $\rightarrow$ Quăng `BadRequestException("Admin không thể tự vô hiệu hóa tài khoản của chính mình.")`.
  - Hệ thống chỉ còn 1 tài khoản Admin duy nhất có status `ACTIVE`, Admin khác cố tình khóa tài khoản này $\rightarrow$ Bị chặn bởi cơ chế Last-Admin Protection.

---

## 3. Quy Trình Kiểm Thử Hồi Quy (Regression Commands)

Mọi Phase sau khi hoàn thành bắt buộc phải thực thi và vượt qua toàn bộ các lệnh kiểm thử sau:

1. **Backend Verification**:
   ```powershell
   .\mvnw.cmd clean test
   ```
   *Yêu cầu*: 0 compilation errors, 100% unit tests PASS.
2. **Frontend Typecheck**:
   ```powershell
   cd frontend
   npm run typecheck
   ```
   *Yêu cầu*: `vue-tsc --noEmit` hoàn thành với mã thoát 0, không có type error nào.
3. **Frontend Production Build**:
   ```powershell
   cd frontend
   npm run build
   ```
   *Yêu cầu*: Vite đóng gói production thành công (tạo đầy đủ bundles trong `frontend/dist/`).
