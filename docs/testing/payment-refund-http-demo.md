# Báo cáo Kiểm thử Thực tế E2E HTTP API & Demo Flow: Payment V2 + Refund

## 1. Môi trường kiểm thử (Environment)

* **Base URL**: `http://localhost:8080`
* **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
* **OpenAPI Docs**: `http://localhost:8080/v3/api-docs`
* **Gateway Mode**: `MOCK` (`cinebook.payment.gateway: mock`, `cinebook.payment.mock.refund-result: SUCCESS`)
* **Kiểm thử thuần HTTP**: 100% qua REST API, không kết nối/truy cập trực tiếp MySQL database.

---

## 2. Dữ liệu Demo (Demo Fixtures & Identifiers)

* **Tài khoản Customer**: `kien231204@gmail.com`
* **Tài khoản Admin**: `admin@cinebook.com`
* **Showtime ID**: `037feb37-f44c-44c8-b16b-164bb856d74a` (Thời gian chiếu: `2026-09-02T19:00:00`, $\ge 2$ giờ trong tương lai)
* **Seat**: Ghế `A7` (ID: `88eec2d4-57e1-45c2-b115-971b7bc187ae`)
* **Booking ID**: `59b392e3-1479-4729-9352-cd643c201b43` (Tổng tiền: `100,000 VND`)
* **Payment ID**: `430010dd-841e-4134-9ed2-734da95e2621` (Mã giao dịch: `PAY-20260831-Y7B5B121`)
* **Ticket ID**: `ea9d30f7-710e-4930-aeb4-c5e7984ddd69`
* **Refund ID**: `9c970e11-332e-454c-a4fc-89d5a6860113` (Mã hoàn tiền: `REF-20260831-V7SNO1CP`)
* **Gateway Refund ID**: `MOCK-REF-XXXXXXXX`

---

## 3. Sơ đồ trạng thái nghiệp vụ (State Transition Flow)

```text
               Tạo đơn đặt vé
                     ↓
               [Booking: PENDING_PAYMENT]
                     ↓ Khởi tạo thanh toán
               [Payment: PENDING]
                     ↓ VNPay IPN Success (Webhook)
               [Payment: SUCCESS]
                     ↓
               [Booking: PAID]
                     ↓
               [Ticket: VALID] & [Seat: SOLD]
                     ↓ Khách hàng yêu cầu hoàn tiền (POST /api/v1/payments/{id}/refund)
               [Refund: PENDING] (Tx1 Commit)
                     ↓ Mock Gateway xử lý (Non-Tx Call → code: "00")
               [Refund: SUCCESS] (Tx2 Commit)
                     ↓
               [Payment: REFUNDED]
                     ↓
               [Booking: REFUNDED]
                     ↓
               [Ticket: CANCELLED]
                     ↓
               [Seat: AVAILABLE] (Khôi phục ghế ngay lập tức)
```

---

## 4. Bằng chứng kiểm thử chi tiết qua HTTP API (HTTP Evidence)

| Step | Thao tác / Mục tiêu | Endpoint & Method | HTTP Status | Response Fields Quan Trọng | Kết Quả |
| :---: | :--- | :--- | :---: | :--- | :---: |
| **1** | Customer Login | `POST /api/v1/auth/login` | `200` | `accessToken`: `<JWT_CUSTOMER>` | **PASS** |
| **2** | Tìm suất chiếu hợp lệ $\ge 2$h | `GET /api/v1/showtimes` | `200` | `startTime`: `"2026-09-02T19:00:00"`, `status`: `"SCHEDULED"` | **PASS** |
| **3** | Kiểm tra sơ đồ ghế trước khi đặt | `GET /api/v1/showtimes/{id}/seats` | `200` | Ghế `A7`: `availabilityStatus`: `"AVAILABLE"` | **PASS** |
| **4** | Tạo Booking (Giữ ghế) | `POST /api/v1/bookings` | `201` | `bookingStatus`: `"PENDING_PAYMENT"`, `totalAmount`: `100000.0` | **PASS** |
| **5** | Khởi tạo giao dịch thanh toán | `POST /api/v1/bookings/{id}/payments` | `200` | `paymentStatus`: `"PENDING"`, `paymentUrl`: `"https://..."` | **PASS** |
| **6** | Mô phỏng IPN Webhook thành công | `GET /api/v1/payments/vnpay/ipn` | `200` | `RspCode`: `"00"`, `Message`: `"Confirm Success"` | **PASS** |
| **7** | Xác nhận Payment SUCCESS | `GET /api/v1/payments/{paymentId}` | `200` | `paymentStatus`: `"SUCCESS"`, `amount`: `100000.0` | **PASS** |
| **8** | Xác nhận Booking PAID | `GET /api/v1/bookings/{bookingId}` | `200` | `bookingStatus`: `"PAID"` | **PASS** |
| **9** | Xác nhận Ticket phát hành VALID | `GET /api/v1/bookings/{bookingId}` | `200` | `tickets[0].ticketStatus`: `"VALID"` | **PASS** |
| **10** | Xác nhận ghế đã chuyển sang SOLD | `GET /api/v1/showtimes/{id}/seats` | `200` | Ghế `A7`: `availabilityStatus`: `"SOLD"` | **PASS** |
| **11** | Customer gửi yêu cầu hoàn tiền | `POST /api/v1/payments/{id}/refund` | `200` | `refundStatus`: `"SUCCESS"`, `amount`: `100000.0` | **PASS** |
| **12** | Tính toàn vẹn số tiền hoàn (Amount) | Payload đối chiếu | `200` | `Refund.amount (100k) == Payment.amount (100k) == Booking.total (100k)` | **PASS** |
| **13** | Xác nhận chi tiết bản ghi hoàn tiền | `GET /api/v1/payments/{id}/refund` | `200` | `refundStatus`: `"SUCCESS"`, `processedAt`: `"2026-08-31T23:22:11"` | **PASS** |
| **14** | Xác nhận Payment REFUNDED | `GET /api/v1/payments/{id}` | `200` | `paymentStatus`: `"REFUNDED"` | **PASS** |
| **15** | Xác nhận Booking REFUNDED | `GET /api/v1/bookings/{id}` | `200` | `bookingStatus`: `"REFUNDED"`, `cancelledReason`: `"Khách hàng..."` | **PASS** |
| **16** | Xác nhận Ticket CANCELLED | `GET /api/v1/bookings/{id}` | `200` | `tickets[0].ticketStatus`: `"CANCELLED"` | **PASS** |
| **17** | Xác nhận ghế trở lại AVAILABLE | `GET /api/v1/showtimes/{id}/seats` | `200` | Ghế `A7`: `availabilityStatus`: `"AVAILABLE"` | **PASS** |
| **18** | Kiểm tra tính Idempotent khi retry | `POST /api/v1/payments/{id}/refund` | `200` | Trả về `RefundResponse` cũ, không tạo refund mới | **PASS** |
| **19** | Admin tra cứu chi tiết Payment | `GET /api/v1/admin/payments/{id}` | `200` | `paymentStatus`: `"REFUNDED"`, `refund.status`: `"SUCCESS"` | **PASS** |
| **20** | Admin xem danh sách Refund (All) | `GET /api/v1/admin/refunds` | `200` | `totalElements`: `8` bản ghi | **PASS** |
| **21** | Admin lọc Refund SUCCESS | `GET /api/v1/admin/refunds?status=SUCCESS` | `200` | Lọc chính xác các bản ghi hoàn tiền thành công | **PASS** |
| **22** | Admin lọc Refund FAILED | `GET /api/v1/admin/refunds?status=FAILED` | `200` | `totalElements`: `0` | **PASS** |
| **23** | Admin lọc Refund PENDING | `GET /api/v1/admin/refunds?status=PENDING` | `200` | Lọc chính xác các bản ghi đang chờ xử lý | **PASS** |

---

## 5. Bằng chứng tính Idempotent (Idempotency Evidence)

* **Lần gọi hoàn tiền 1**:
  * `refundId`: `9c970e11-332e-454c-a4fc-89d5a6860113`
  * `refundCode`: `REF-20260831-V7SNO1CP`
  * `refundStatus`: `SUCCESS`
* **Lần gọi hoàn tiền 2 (Retry)**:
  * `refundId`: `9c970e11-332e-454c-a4fc-89d5a6860113` *(Trùng khớp 100%)*
  * `refundCode`: `REF-20260831-V7SNO1CP` *(Trùng khớp 100%)*
  * `refundStatus`: `SUCCESS`
  * Hệ thống ghi log: `Payment ... was already refunded. Returning existing refund.` và không phát sinh request mới sang cổng thanh toán.

---

## 6. Bằng chứng Negative & Security Tests

| Test Case | Thao tác | Expected | Kết quả thực tế qua API | Trạng thái |
| :--- | :--- | :---: | :--- | :---: |
| **N1: Refund non-SUCCESS Payment** | Thử hoàn tiền Payment đang `PENDING` | HTTP `400` | Message: *"Chỉ có thể hoàn tiền cho giao dịch thanh toán thành công (SUCCESS). Trạng thái hiện tại: PENDING"* | **PASS** |
| **N2: Customer Refund < 2h** | Thử hoàn tiền suất chiếu trong vòng 60 phút | HTTP `400` | Message: *"Khách hàng chỉ có thể yêu cầu hoàn tiền trước giờ chiếu ít nhất 2 tiếng."* | **PASS** |
| **Admin Bypass 2h** | Admin gọi hoàn tiền suất chiếu < 2h | Bỏ qua 2h | Vượt qua validation 2 tiếng, gửi lệnh hoàn tiền tới cổng thanh toán | **PASS** |
| **N3: Ownership Check** | Customer B hoàn tiền Payment của Customer A | HTTP `403` | Message: *"Bạn không có quyền thao tác với thanh toán của đơn đặt vé này."* | **PASS** |
| **N4: Anonymous Access** | Không gửi Authorization header | HTTP `401` | Message: *"Full authentication is required to access this resource"* | **PASS** |
| **N5: RBAC Authorization** | Customer token gọi `/api/v1/admin/**` | HTTP `403` | Message: *"Access is denied: insufficient permissions"* | **PASS** |

---

## 7. Kết luận

Toàn bộ flow nghiệp vụ **Payment V2 + Refund** đã được kiểm thử thực tế và xác nhận hoạt động 100% chính xác, bảo đảm tính toàn vẹn tài chính, cô lập ranh giới giao dịch cơ sở dữ liệu và tuân thủ các quy tắc bảo mật của hệ thống.

