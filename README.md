# CineBook — Hệ Thống Đặt Vé Xem Phim Trực Tuyến

> **Đồ Án Tốt Nghiệp** — Nền tảng đặt vé, quản lý rạp chiếu phim, thanh toán trực tuyến và soát vé tự động.

---

## 1. Giới Thiệu & Kiến Trúc Tổng Thể

CineBook được xây dựng theo kiến trúc **Monolith Layered Architecture** chuẩn mực (Controller → Service → Repository → MySQL 8), đảm bảo tính nhất quán dữ liệu, hiệu năng cao và dễ bảo trì.

```text
               +-------------------------------------------------+
               |              Vue 3 SPA (Vite + TS)              |
               |       Pinia + Vue Router + Tailwind CSS         |
               +-----------------------+-------------------------+
                                       | HTTP / REST (JWT Auth)
                                       v
               +-------------------------------------------------+
               |          Spring Boot 3 (Java 21 Monolith)       |
               |  Controller  →  Service  →  Repository / JPA   |
               +-----------+--------------------+----------------+
                           |                    |
             Pessimistic   |                    | HTTP Webhook (IPN)
               Locking     v                    v
               +------------------+     +------------------------+
               |  MySQL 8 Database|     | VNPay Sandbox Gateway  |
               +------------------+     +------------------------+
```

---

## 2. Công Nghệ Sử Dụng (Tech Stack)

### Backend
- **Ngôn ngữ & Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 3.x, Spring MVC, Spring Data JPA, Spring Security (Stateless JWT)
- **Database**: MySQL 8.x (Single source of truth, Pessimistic Locking chống trùng ghế và double check-in)
- **Thanh toán**: Cổng thanh toán VNPay Sandbox (Tích hợp IPN & Return URL có chữ ký số HMAC-SHA512)
- **Tài liệu API**: OpenAPI 3 / Swagger UI (`/swagger-ui.html`)
- **Quản lý Build**: Apache Maven (Wrapper `./mvnw.cmd`)

### Frontend
- **Framework**: Vue 3 (Composition API với `<script setup>`)
- **Ngôn ngữ & Build Tool**: TypeScript + Vite
- **Quản lý State & Routing**: Pinia + Vue Router (RBAC Route Guards)
- **UI & Styling**: Tailwind CSS, Lucide Icons, Responsive Mobile/Desktop
- **HTTP Client**: Axios (Tự động đính kèm Bearer JWT và xử lý Refresh Token)

---

## 3. Các Tính Năng Đã Hoàn Thiện

### Khách Hàng (Customer)
1. **Xác thực & Tài khoản**: Đăng ký, đăng nhập, JWT access token (15m) + refresh token (7d), đổi mật khẩu, cập nhật hồ sơ cá nhân.
2. **Khám phá phim & Lịch chiếu**:
   - Danh sách phim đang chiếu / sắp chiếu, chi tiết phim, trailer Youtube, thể loại, phân loại độ tuổi.
   - Gợi ý phim thông minh dựa trên lịch sử đặt vé và độ phổ biến.
   - Tra cứu cụm rạp (`/cinemas`), xem địa chỉ, tiện ích và lịch chiếu theo rạp.
   - Trang khuyến mãi & voucher (`/promotions`), sao chép mã 1-click.
3. **Đặt vé & Giữ chỗ (Seat Hold)**:
   - Sơ đồ ghế trực quan thời gian thực (Standard, VIP, Sweetbox).
   - Cơ chế giữ chỗ 5 phút với khóa bi quan (Pessimistic Locking) chống double-booking tuyệt đối.
   - Áp dụng mã giảm giá voucher (kiểm tra hạn mức, giá trị đơn tối thiểu, số lượng còn lại).
4. **Thanh toán VNPay Sandbox**:
   - Chuyển hướng thanh toán an toàn, xác thực chữ ký số HMAC-SHA512.
   - Xử lý Webhook IPN tự động xác nhận đơn và phát hành vé điện tử.
5. **Vé Điện Tử & Quản Lý Đơn Hàng**:
   - Trang "Vé đã mua" (`/my-bookings`) với mã QR điện tử cho từng ghế.
   - Yêu cầu hoàn vé tự động (nếu trước giờ chiếu >= 2 tiếng) theo quy định rạp.

### Quản Trị Viên & Nhân Viên (Admin & Box Office)
1. **Bảng Điều Khiển (Admin Dashboard)**: Thống kê KPI doanh thu, số vé bán, tỷ lệ lấp đầy phòng chiếu.
2. **Quản Lý Phim (Movies CRUD)**: Thêm, sửa, soft-delete phim, gắn thể loại, nhập dữ liệu từ TMDB API.
3. **Quản Lý Cụm Rạp & Phòng Chiếu (Cinemas & Auditoriums)**:
   - Thêm cụm rạp, thông tin hotline, giờ mở cửa.
   - Thêm phòng chiếu với cơ chế **tự động sinh ma trận ghế** (Rows x Seats).
4. **Quản Lý Lịch Chiếu (Showtimes CRUD)**: Lên lịch chiếu, tự động tính giờ kết thúc dựa trên thời lượng phim, kiểm tra xung đột khung giờ.
5. **Quản Lý Đặt Vé & Đơn Hàng (Bookings)**: Tra cứu đơn hàng toàn hệ thống, xem chi tiết ghế/vé/giao dịch, hủy đơn chờ thanh toán.
6. **Quản Lý Khuyến Mãi (Promotions CRUD)**: Tạo voucher giảm theo %, giảm tiền cố định, giới hạn lượt dùng và thời gian.
7. **Quản Lý Hoàn Tiền (Refunds)**: Tra cứu lịch sử hoàn tiền, đối soát giao dịch VNPay.
8. **Soát Vé Tại Quầy (Box Office / Ticket Validation)**:
   - Quét mã QR / Nhập mã vé để kiểm tra tính hợp lệ.
   - Check-in vào rạp (chuyển trạng thái `VALID` → `USED`).
   - Chống soát vé trùng (Duplicate Check-in Prevention trả về 409 Conflict ngay lập tức).
9. **Báo Cáo & Thống Kê (Reports)**: Báo cáo doanh thu theo phim, rạp, khoảng thời gian.
10. **Quản Lý Người Dùng (Users)**: Danh sách tài khoản, phân quyền, khóa/mở khóa tài khoản.

---

## 4. Hướng Dẫn Cài Đặt & Chạy Ứng Dụng

### Yêu Cầu Môi Trường
- **Java**: JDK 21+
- **Node.js**: Node 18+ (khuyên dùng Node 20+)
- **MySQL**: MySQL 8.x (chạy trên cổng 3306)

### Bước 1: Cấu Hình Cơ Sở Dữ Liệu
Tạo cơ sở dữ liệu `cinebook` trong MySQL:
```sql
CREATE DATABASE cinebook CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Bước 2: Cấu Hình File Môi Trường
Sao chép `src/main/resources/application-local.yml.example` thành `application-local.yml` và điền credentials của bạn:
```yaml
jwt:
  secret: CineBook-Local-Dev-JWT-Secret-2026-Strong-Key

vnpay:
  tmn-code: YOUR_VNPAY_TMN_CODE
  hash-secret: YOUR_VNPAY_HASH_SECRET
  payment-url: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  return-url: ${VNPAY_RETURN_URL:https://silencer-gimmick-uplifted.ngrok-free.dev/payment/result}
cinebook:
  payment:
    gateway: vnpay
```

### Bước 3: Khởi Chạy Nhanh (One-Command Dev Launcher)
Chạy script PowerShell duy nhất để khởi động toàn bộ stack (Spring Boot + Vite Proxy + ngrok Tunnel):
```powershell
.\dev.ps1
```

Script sẽ tự động:
1. Khởi động **Spring Boot Backend (:8080)** với profile `local`.
2. Khởi động **Vite Frontend (:5173)** với reverse proxy `/api/*` tới `:8080`.
3. Khởi động **ngrok Public Tunnel** chuyển tiếp tới `http://localhost:5173` (static domain: `silencer-gimmick-uplifted.ngrok-free.dev`).
4. Thực hiện health-check và hiển thị bảng điều khiển tập trung.
- **Frontend App**: `http://localhost:5173` hoặc `https://silencer-gimmick-uplifted.ngrok-free.dev`
- **Backend API Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **VNPay IPN Webhook**: `https://silencer-gimmick-uplifted.ngrok-free.dev/api/v1/payments/vnpay/ipn`

---

Hệ thống tự động khởi tạo dữ liệu mẫu khi khởi động lần đầu (`DataInitializer.java`):

| Vai trò | Email | Mật khẩu | Quyền hạn |
|---|---|---|---|
| **Quản trị viên (Admin)** | `admin@cinebook.com` | `Password123@` | Toàn quyền hệ thống & Quầy vé |
| **Khách hàng mẫu** | `customer@cinebook.com` | `Password123@` | Đặt vé, thanh toán, xem vé cá nhân |

*(Hoặc đăng ký tài khoản khách hàng mới trực tiếp trên giao diện)*

---

## 6. Chạy Kiểm Thử (Testing & Quality Gate)

### Backend Tests (JUnit 5 + Mockito)
```powershell
.\mvnw.cmd test
```
*Kết quả: 404/404 unit & integration tests PASS 100%.*

### Frontend Typecheck & Production Build
```powershell
cd frontend
npm run typecheck
npm run build
```

### End-to-End Test Suite (Python)
```powershell
python scratch/e2e_phase4_test.py
python scratch/e2e_admin_and_public_test.py
```
*Kết quả: Toàn bộ quy trình Auth → Đặt vé → VNPay IPN → Soát vé Box Office → Báo cáo đạt 100% PASS.*

---

## 7. Triển Khai Docker (Containerization)

CineBook hỗ trợ đóng gói Docker toàn diện:
```powershell
docker-compose up --build -d
```
- MySQL Database: `localhost:3306`
- Spring Boot App: `localhost:8080`
- Vue 3 Frontend (Nginx): `localhost:80`