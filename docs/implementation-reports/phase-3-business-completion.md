# Báo Cáo Triển Khai: Phase 3 — Hoàn Thiện Nghiệp Vụ, Khuyến Mãi, Hoàn Tiền & Quản Trị Hệ Thống

## 1. Phạm Vi Triển Khai (Scope)

Phase 3 hoàn thiện toàn diện các luồng nghiệp vụ tài chính, ưu đãi và quản trị vận hành rạp chiếu cho dự án tốt nghiệp CineBook:

1. **Promotion & Voucher System**:
   - Khách hàng kiểm tra & xem trước mã khuyến mãi (`GET /api/v1/promotions/validate`).
   - Khóa bi quan (`PESSIMISTIC_WRITE`) trên bản ghi khuyến mãi khi tạo đơn hàng (`POST /api/v1/bookings`).
   - Quản lý vòng đời số lượng sử dụng (`usedCount`): tăng khi giữ chỗ, hoàn trả an toàn nếu hết hạn hoặc khách hủy trước khi thanh toán; giữ nguyên hạn mức khi thanh toán `PAID` và khi hoàn tiền `REFUNDED`.
   - Snapshot bất biến thông tin giảm giá vào bảng `booking_promotions`.
   - Tích hợp ô nhập mã, áp dụng, gỡ mã và bóc tách giá (Tạm tính, Khuyến mãi, Tổng thanh toán) trên `BookingSummary.vue` & `BookingView.vue`.
2. **Customer & Admin Refund (Hoàn Tiền)**:
   - Khách hàng yêu cầu hoàn tiền tự phục vụ trên `MyBookingsView.vue` với điều kiện bảo mật nghiêm ngặt từ backend: `payment.status == SUCCESS`, `booking.status == PAID`, và thời gian bắt đầu suất chiếu $\ge 2\text{h}$.
   - Chuyển đổi trạng thái nguyên tử: `Refund -> SUCCESS`, `Payment -> REFUNDED`, `Booking -> REFUNDED`, `Tickets -> CANCELLED`, ghế giải phóng thành `AVAILABLE` ngay lập tức.
   - Idempotency nghiêm ngặt: Xử lý an toàn khi gửi nhiều request hoàn tiền đồng thời, trả về bản ghi hoàn tiền đã tồn tại với mã `REF-...` mà không nhân bản giao dịch.
   - Admin hoàn tiền can thiệp (`/api/v1/admin/refunds`, `/api/v1/admin/bookings/{id}/refund`) để xử lý ngoại lệ tài chính (khách đã bị trừ tiền VNPay nhưng đơn hàng quá hạn giữ chỗ).
3. **Admin Operations & Real Reports**:
   - **Quản lý Khuyến mãi (`/admin/promotions`)**: CRUD khuyến mãi, lọc trạng thái, theo dõi thanh tiến độ số lượng phát hành (`usedCount / usageLimit`), kích hoạt/tạm dừng.
   - **Quản lý Hoàn tiền (`/admin/refunds`)**: Tra cứu lịch sử hoàn tiền, lọc theo trạng thái, xem biên lai và thực hiện hoàn tiền ngoại lệ.
   - **Bảng giá & Loại ghế (`/admin/pricing`)**: Cấu hình phụ thu theo loại ghế (VIP, Standard, Couple).
   - **Bảng điều khiển (`/admin/dashboard`) & Báo cáo (`/admin/reports`)**: Kết nối trực tiếp số liệu từ DB qua `reportService` (100% dữ liệu thực tế, 0 fake data).
4. **Đa Ngôn Ngữ & UX**:
   - Cập nhật đầy đủ bản dịch tiếng Việt (`vi.ts`) và tiếng Anh (`en.ts`).

---

## 2. Kết Quả Kiểm Thử Toàn Diện (Verification Summary)

| Hạng mục kiểm tra | Công cụ / Lệnh | Kết quả | Ghi chú |
|---|---|---|---|
| **Backend Unit & Integration Tests** | `.\mvnw.cmd test` | **388 / 388 PASSED** (100%) | 0 failures, 0 errors, 0 skipped |
| **Frontend TypeScript Typecheck** | `npm run typecheck` (`vue-tsc --noEmit`) | **PASSED** (100%) | 0 errors |
| **Frontend Production Build** | `npm run build` (`vite build`) | **PASSED** (100%) | Build thành công trong 3.69s |
| **E2E HTTP Integration Flow** | `python e2e_phase3_test.py` | **13 / 13 PASSED** (100%) | Toàn bộ 13 bước xác thực nghiệp vụ thành công |

---

## 3. Danh Sách Tệp Thay Đổi (Files Changed)

### Backend:
- `src/main/java/com/cinebook/mapper/RefundMapper.java`: Xử lý an toàn Hibernate proxy (`Booking#getId`, `Booking#getBookingCode`) tránh `LazyInitializationException` khi chuyển đổi DTO ngoài transaction.
- `src/main/java/com/cinebook/service/impl/PaymentServiceImpl.java`: Bổ sung `@Transactional(readOnly = true)` và hoàn thiện xử lý hoàn tiền idempotent.

### Frontend:
- `frontend/src/types/promotion.types.ts`: Định nghĩa TypeScript interfaces cho Khuyến mãi (`ValidatePromotionResponse`, `PromotionResponse`, `CreatePromotionRequest`, v.v.).
- `frontend/src/services/promotion.service.ts`: API client cho Customer validation và Admin promotion management.
- `frontend/src/types/refund.types.ts`: Định nghĩa TypeScript interfaces cho Hoàn tiền (`RefundResponse`, `RefundRequest`, `RefundStatus`).
- `frontend/src/services/payment.service.ts`: Cập nhật các phương thức gọi API hoàn tiền khách hàng và hoàn tiền admin.
- `frontend/src/types/report.types.ts`: Định nghĩa TypeScript interfaces cho Dashboard KPI, Doanh thu, Phim, Cụm rạp và Lấp đầy suất chiếu.
- `frontend/src/services/report.service.ts`: API client kết nối các endpoint báo cáo quản trị.
- `frontend/src/types/seatType.types.ts` & `frontend/src/services/seatType.service.ts`: API client quản lý loại ghế và bảng giá phụ thu.
- `frontend/src/components/booking/BookingSummary.vue`: Tích hợp ô nhập mã giảm giá, kiểm tra điều kiện, xem trước mức giảm và hiển thị bóc tách giá.
- `frontend/src/views/customer/BookingView.vue`: Truyền mã khuyến mãi vào `createBooking` khi tạo đơn giữ chỗ.
- `frontend/src/components/payment/RefundModal.vue`: Modal xác nhận yêu cầu hoàn tiền cho khách hàng kèm cảnh báo điều kiện 2 giờ.
- `frontend/src/components/payment/RefundDetailModal.vue`: Modal xem biên lai hoàn tiền chính thức (`REF-...`).
- `frontend/src/views/customer/MyBookingsView.vue`: Tích hợp nút "Yêu cầu hoàn tiền" cho vé đủ điều kiện và "Xem biên lai" cho vé đã hoàn tiền.
- `frontend/src/views/admin/AdminPromotionsView.vue`: Màn hình quản lý khuyến mãi, lọc trạng thái, tạo/sửa modal và thanh tiến độ quota.
- `frontend/src/views/admin/AdminRefundsView.vue`: Màn hình tra cứu hoàn tiền và thực hiện hoàn tiền ngoại lệ.
- `frontend/src/views/admin/AdminPricingView.vue`: Màn hình cấu hình loại ghế và giá phụ thu.
- `frontend/src/views/admin/AdminDashboardView.vue`: Bảng điều khiển kết nối trực tiếp dữ liệu KPI tài chính và vận hành từ backend.
- `frontend/src/views/admin/AdminReportsView.vue`: Báo cáo chuyên sâu doanh thu theo ngày/tháng/năm, hiệu suất phim và cụm rạp.
- `frontend/src/layouts/AdminLayout.vue`: Bổ sung điều hướng sidebar cho `Hoàn tiền & GD` và `Bảng giá & Ghế`.
- `frontend/src/router/index.ts`: Đăng ký các route quản trị mới (`/admin/promotions`, `/admin/refunds`, `/admin/pricing`).
- `frontend/src/locales/vi.ts` & `frontend/src/locales/en.ts`: Bổ sung đầy đủ nhãn ngôn ngữ cho Promotion, Refund, Admin và Reports.

### Test & Automation Scripts:
- `scratch/e2e_phase3_test.py`: Script kiểm thử tự động toàn bộ vertical slice Phase 3 qua HTTP API.

---

## 4. Chi Tiết Logic Nghiệp Vụ & An Toàn Dữ Liệu (Business Correctness)

### A. Vòng Đời Khuyến Mãi (Promotion Lifecycle)
1. **Preview Validation**: Chỉ kiểm tra điều kiện hiệu lực, đơn tối thiểu và tính mức giảm dự kiến. Tuyệt đối không thay đổi `usedCount`.
2. **Authoritative Creation**: Khi gọi `POST /api/v1/bookings`, hệ thống nạp lại thông tin khuyến mãi, kích hoạt khóa bi quan (`PESSIMISTIC_WRITE`), kiểm tra lại hạn mức và tăng `usedCount` lên 1. Mức giảm được snapshot cố định vào `booking_promotions`.
3. **Rollback & Hold Release**: Nếu đơn hàng ở trạng thái `PENDING_PAYMENT` bị hết hạn hoặc khách hàng tự hủy, cron task / cancel handler sẽ giảm `usedCount` xuống 1 (với cận dưới an toàn `max(0, usedCount - 1)`).
4. **Paid & Refunded**: Khi đơn đã chuyển sang `PAID`, hạn mức được giữ nguyên. Khi hoàn tiền `REFUNDED`, hạn mức không hoàn lại và snapshot khuyến mãi được bảo toàn nguyên vẹn.

### B. Quy Trình Hoàn Tiền (Refund State Machine)
- **Quy tắc 2 giờ**: Backend kiểm tra `showtime.startTime >= now + 2h`. Nếu không thỏa mãn, request bị từ chối với lỗi 400 (`BadRequestException`), ngăn chặn can thiệp từ phía client.
- **Tính nguyên tử**: Bản ghi hoàn tiền được ghi nhận `SUCCESS`, trạng thái giao dịch đổi thành `REFUNDED`, trạng thái đơn hàng đổi thành `REFUNDED`, toàn bộ vé đổi thành `CANCELLED`, và ghế được giải phóng thành `AVAILABLE` ngay lập tức.
- **Tính Idempotent**: Nếu khách hàng hoặc client gửi lại yêu cầu hoàn tiền cho giao dịch đã hoàn tất thành công, hệ thống trả về kết quả 200 OK với thông tin `RefundResponse` hiện có mà không phát sinh thêm giao dịch mới.

### C. Xử Lý Ngoại Lệ Tài Chính (Orphan Successful Payment)
- Trường hợp khách hàng bị trừ tiền trên VNPay nhưng webhook IPN đến muộn sau khi đơn hàng giữ chỗ đã bị hết hạn (`Booking EXPIRED`, `Payment SUCCESS`, `Tickets = 0`):
  - Hệ thống **không** tự ý chuyển đơn hết hạn sang `PAID` để tránh bán trùng ghế (Double-booking invariant).
  - Giao dịch được lưu giữ an toàn cho mục đích đối soát.
  - Quản trị viên (Admin) có quyền thực hiện hoàn tiền can thiệp thông qua API `POST /api/v1/admin/bookings/{id}/refund` hoặc giao diện `/admin/refunds`.

---

## 5. Đề Xuất Cho Giai Đoạn Tiếp Theo (Recommended Next Phase)

Hệ thống Core Backend và Customer/Admin Frontend hiện đã hoàn chỉnh 100% các tính năng nghiệp vụ cốt lõi từ Authentication, Movie/Showtime Discovery, Seat Map Selection, VNPay Payment, Electronic Ticket, My Bookings, Promotion/Voucher, Customer/Admin Refund, đến Admin Pricing/Dashboard/Reports.

Các hạng mục giá trị cao tiếp theo đề xuất:
1. **Email / SMS Notification Service**: Tự động gửi email xác nhận đặt vé kèm QR code sau khi thanh toán thành công và email thông báo khi hoàn tiền.
2. **Staff / Box Office Portal**: Giao diện quét QR code vé tại cửa rạp và soát vé trực tiếp dành cho nhân viên rạp chiếu.
3. **Deployment / CI-CD Packaging**: Hoàn thiện Dockerfile và kịch bản deploy phục vụ buổi bảo vệ đồ án tốt nghiệp.

