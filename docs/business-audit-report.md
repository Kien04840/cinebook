# Báo Cáo Kiểm Toán Nghiệp Vụ & Xác Thực Vận Hành Rạp Chiếu Phim CineBook
**Business Logic Audit & Real-world Cinema Operation Report**

- **Dự án**: Hệ thống Đặt vé và Quản lý Rạp chiếu phim CineBook
- **Vai trò thực hiện**: Senior Software Architect & Business Analyst
- **Thời điểm kiểm toán**: Tháng 10/2026
- **Trạng thái**: Hoàn tất phân tích — Sẵn sàng áp dụng cải tiến nghiệp vụ

---

## 1. Tóm Tắt Tổng Quan (Executive Summary)

CineBook là hệ thống quản lý rạp chiếu phim nguyên khối (Monolith) phục vụ đồ án tốt nghiệp với các yêu cầu vận hành thực tế cao. Hệ thống đã triển khai tốt nhiều luồng lõi như:
- Concurrency giữ chỗ ghế bằng ràng buộc duy nhất MySQL (`uk_seat_holds_showtime_seat`).
- Máy trạng thái đơn đặt vé (Booking State Machine) và dọn dẹp giữ chỗ tự động qua cron job và lazy expiration.
- Tích hợp cổng thanh toán VNPay Sandbox với xác thực chữ ký HMAC-SHA512, đối soát số tiền và Idempotent Webhook IPN.
- Định giá động theo sức chứa (Capacity-Aware Dynamic Pricing) và snapshot bất biến giá vé.
- Thuật toán tự động xếp lịch chiếu đa phim (Multi-movie deficit-aware scheduling).

Tuy nhiên, qua rà soát từ góc độ **vận hành rạp chiếu phim thực tế (Real-world Cinema Operations)**, vẫn tồn tại một số lỗ hổng xác thực dữ liệu quan trọng liên quan đến tính toàn vẹn quan hệ cha-con (Cascading/Orphan integrity), quy trình đóng/xóa thực thể khi còn lịch chiếu tương lai, và các biện pháp bảo toàn dữ liệu lịch sử giao dịch.

Báo cáo này lập danh mục kiểm toán chi tiết cho 11 phân hệ nghiệp vụ, phân tích hiện trạng, rủi ro, và đề xuất giải pháp chuẩn kiến trúc.

---

## 2. Bảng Tổng Hợp Kiểm Toán 11 Phân Hệ Nghiệp Vụ

| # | Phân hệ (Domain) | Hiện trạng (Current Behavior) | Vấn đề & Rủi ro thực tế (Problem) | Giải pháp chuẩn đề xuất (Proposed Solution) | Mức độ ưu tiên |
|---|---|---|---|---|---|
| **1** | **Quản lý Phim (Movie)** | `deleteMovie` thực hiện xóa mềm (`deletedAt = now`, `status = HIDDEN`) vô điều kiện, không kiểm tra suất chiếu tương lai. | Nếu admin xóa/ẩn phim đang có suất chiếu sắp tới (`SCHEDULED`, `startTime > now`), khách hàng vào xem phim sẽ bị lỗi hoặc suất chiếu bị mồ côi ngoài giao diện đặt vé. | Chặn xóa/ẩn phim nếu còn suất chiếu tương lai đang hoạt động. Chỉ cho phép ẩn/xóa mềm khi không còn suất chiếu tương lai nào. Bảo toàn bản ghi phim nếu đã có vé lịch sử. | **Cao (High)** |
| **2** | **Quản lý Cụm Rạp (Cinema)** | `deleteCinema` xóa mềm (`deletedAt = now`, `status = CLOSED`) vô điều kiện. `updateCinema` cho phép đổi status sang `CLOSED` không kiểm tra suất chiếu. | Rạp đóng cửa hoặc bị xóa trong khi các phòng chiếu vẫn còn lịch chiếu sắp diễn ra với vé khách đã mua. | Chặn xóa mềm hoặc đổi trạng thái rạp thành `CLOSED` nếu bất kỳ phòng chiếu nào trong rạp còn suất chiếu `SCHEDULED` trong tương lai. | **Cao (High)** |
| **3** | **Quản lý Phòng Chiếu (Auditorium)** | `deleteAuditorium` đã kiểm tra `existsByAuditoriumId`, nhưng `updateAuditorium` cho phép đổi status sang `MAINTENANCE` hoặc `DECOMMISSIONED` ngay cả khi có suất chiếu sắp chiếu. | Đưa phòng chiếu vào bảo trì trong khi khách hàng đã đặt vé xem phim ở phòng đó trong tương lai gần. | Trong `updateAuditorium`, chặn chuyển trạng thái sang `MAINTENANCE`/`DECOMMISSIONED` nếu phòng còn suất chiếu tương lai. Trong `deleteAuditorium`, bổ sung kiểm tra giao dịch `bookingRepository.existsByAuditoriumId`. | **Cao (High)** |
| **4** | **Quản lý Suất Chiếu (Showtime)** | Đã áp dụng lọc phía server cho khách (`startTime > now` & `SCHEDULED`). Đã chặn xóa suất chiếu có vé đã thanh toán (Case 3). Đã chặn sửa phim/phòng/giờ khi đã có vé đặt. | Cần củng cố bất biến: Giá vé cơ bản (`basePrice`) của suất chiếu đã có vé đã mua không được làm ảnh hưởng đến giá vé lịch sử của các vé đã bán trước đó (Snapshot bảo toàn). | Xác nhận snapshot giá vé trong `tickets.ticket_price` hoạt động độc lập và không bị tính toán lại khi cập nhật suất chiếu. | **Cao (High)** |
| **5** | **Quản lý Ghế & Giữ Chỗ (Seat Concurrency)** | Áp dụng unique constraint `uk_seat_holds_showtime_seat`, bắt `DataIntegrityViolationException` $\rightarrow$ `ConflictException`. Giữ chỗ 5 phút, giải phóng qua cron 10s. | Trùng khớp chuẩn thiết kế. Cần bảo vệ sơ đồ ghế không bị thay đổi cấu trúc khi phòng chiếu có suất chiếu trong tương lai. | Giữ nguyên cơ chế locking & constraints. Đảm bảo thao tác cấu hình ghế đôi / chuyển loại ghế bị từ chối nếu có suất chiếu tương lai hoặc giao dịch tồn tại. | **Trung bình (Medium)** |
| **6** | **Quản lý Đơn Đặt Vé (Booking State Machine)** | Máy trạng thái `PENDING_PAYMENT` $\rightarrow$ `PAID`, `EXPIRED`, `CANCELLED`, `REFUNDED`. Hủy đơn chỉ áp dụng cho đơn chưa thanh toán. Lưu vết ghế hủy sang `tickets` CANCELLED. | Khách thanh toán đơn đã hết hạn giữ chỗ: Được bảo vệ tại `initiatePayment` và IPN callback. Cần đảm bảo không bao giờ cho phép kích hoạt thanh toán lại cho đơn đã hủy hoặc đã hết hạn. | Đã triển khai đầy đủ. Ghi nhận và bổ sung tài liệu bảo vệ cho kỳ bảo vệ tốt nghiệp. | **Trung bình (Medium)** |
| **7** | **Quản lý Thanh Toán (Payment & VNPay)** | Xác thực HMAC-SHA512, mã TMN, số tiền, xử lý IPN Idempotent. Nếu thanh toán thành công nhưng đơn đã hết hạn: `Payment -> SUCCESS`, `Booking -> EXPIRED`, ghi log kiểm toán Admin hoàn tiền. | Tồn tại trường hợp khách hủy thanh toán trên cổng VNPay làm phát sinh hiểu nhầm về hủy đơn vé. | Đã áp dụng quy tắc: Hủy trên VNPay chỉ chuyển `Payment -> CANCELLED`, giữ nguyên `Booking` và giữ chỗ ghế còn hạn để khách thanh toán lại (Resume/Retry). | **Cao (High)** |
| **8** | **Quản lý Vé (Ticket Snapshot)** | Giá vé được snapshot vào `tickets.ticket_price` tại thời điểm xác nhận thanh toán. Quét mã QR một lần cho toàn bộ đơn qua `checkInCode` bí mật. | Đảm bảo tính bất biến tuyệt đối của vé và bảo vệ chống quét mã trùng (Duplicate Check-in). | Duy trì khóa bi quan (`findByIdWithLock`) khi check-in, cấm check-in đơn đã hủy/hoàn tiền hoặc đã dùng hết vé. | **Cao (High)** |
| **9** | **Quản lý Người Dùng (User Management)** | Không có API xóa cứng người dùng. Chỉ có cập nhật trạng thái `ACTIVE`, `INACTIVE`, `BLOCKED`. Có bảo vệ Admin không tự khóa, không tự hạ quyền, không khóa Admin cuối. | Nếu người dùng có lịch sử đặt vé/thanh toán yêu cầu xóa tài khoản, xóa vật lý sẽ làm gãy khóa ngoại `FKbookings722397`. | Cấm xóa vật lý người dùng có lịch sử giao dịch. Chỉ cho phép chuyển trạng thái `INACTIVE`/`BLOCKED` hoặc ẩn danh hóa thông tin cá nhân (Anonymization). | **Trung bình (Medium)** |
| **10** | **Định Giá Động (Dynamic Pricing)** | Công thức Capacity-Aware: $(Base \times Cap) + SeatMod + DayMod + TimeSlotMod$. Snapshot khi đặt và thanh toán. | Cần làm rõ sự khác biệt giữa giá tối thiểu hiển thị trên thẻ phim (`calculateMinimumTicketPrice`) và giá chi tiết từng ghế khi chọn. | Single Source of Truth tập trung tại `PricingService`. Tài liệu hóa công thức cho hội đồng chấm tốt nghiệp. | **Trung bình (Medium)** |
| **11** | **Tự Động Xếp Lịch (Showtime Scheduling)** | Thuật toán heuristic tham lam có tính tỷ lệ thiếu hụt quota, phạt chiếu liên tiếp, phạt phòng trùng lặp. Phân tách rõ `TurnaroundMinutes` và `SnapIntervalMinutes`. | Quản trị viên cần hiểu rõ tại sao quota là mục tiêu (target) chứ không phải bảo đảm tuyệt đối (guarantee) khi phòng chiếu kín lịch. | Giữ vững thuật toán, tài liệu hóa bảng điểm chấm slot và quy tắc ưu tiên phim cho thuyết trình đồ án. | **Cao (High)** |

---

## 3. Kế Hoạch Triển Khai Chi Tiết (Implementation Roadmap)

1. **Bước 1 — Bổ sung các phương thức truy vấn kiểm tra quan hệ tại Repository**:
   - `ShowtimeRepository`:
     - `existsByMovieIdAndStatusAndStartTimeAfter`: Kiểm tra phim còn suất chiếu tương lai đang hoạt động.
     - `existsByAuditoriumCinemaIdAndStatusAndStartTimeAfter`: Kiểm tra rạp chiếu còn suất chiếu tương lai đang hoạt động.
     - `existsByAuditoriumIdAndStatusAndStartTimeAfter`: Kiểm tra phòng chiếu còn suất chiếu tương lai đang hoạt động.
   - `BookingRepository`:
     - `existsByUserId`: Kiểm tra người dùng có dữ liệu lịch sử đặt vé.

2. **Bước 2 — Củng cố ràng buộc nghiệp vụ tại Service Layer**:
   - `MovieServiceImpl.deleteMovie`: Chặn xóa nếu còn suất chiếu tương lai.
   - `CinemaServiceImpl.deleteCinema` & `updateCinema`: Chặn xóa mềm hoặc chuyển sang `CLOSED` nếu rạp còn suất chiếu tương lai.
   - `AuditoriumServiceImpl.updateAuditorium`: Chặn chuyển sang `MAINTENANCE` hoặc `DECOMMISSIONED` nếu phòng còn suất chiếu tương lai. Bổ sung kiểm tra `bookingRepository.existsByAuditoriumId` khi xóa.
   - `SeatServiceImpl`: Bổ sung kiểm tra suất chiếu tương lai khi thay đổi loại ghế hoặc sơ đồ ghế.

3. **Bước 3 — Biên soạn Tài liệu Bảo Vệ Đồ Án Tốt Nghiệp (`docs/business-defense-guide.md`)**:
   - Phần 1: Nguyên tắc thiết kế nghiệp vụ CineBook (Tại sao không xóa dữ liệu giao dịch, bảo toàn tính bất biến).
   - Phần 2: Danh sách nghiệp vụ và ngoại lệ của 11 phân hệ (Luồng chuẩn, Ngoại lệ, Cách xử lý).
   - Phần 3: Bộ câu hỏi phản biện của Hội đồng & Câu trả lời chuẩn kiến trúc.
   - Phần 4: Bảng tổng kết quyết định thiết kế nghiệp vụ.

4. **Bước 4 — Kiểm thử và xác nhận chất lượng (Verification)**:
   - Chạy toàn bộ backend unit/integration tests (`.\mvnw.cmd test`).
   - Kiểm tra typecheck và build frontend.

