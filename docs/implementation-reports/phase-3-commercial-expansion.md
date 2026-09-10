# BÁO CÁO HOÀN THÀNH TRIỂN KHAI: PHASE 3 — COMMERCIAL EXPANSION (F&B & IN-APP NOTIFICATIONS)

Dự án: **CineBook — Hệ thống đặt vé xem phim trực tuyến**  
Giai đoạn: **PHASE 3 (Commercial Expansion: Food & Concessions & In-App Notifications)**  
Thời gian hoàn thành: `2026-09-10`  
Kiến trúc: **Monolithic Spring Boot 4.0.8 / Java 21 + MySQL 8 + Vue 3 / Vite / TypeScript + Tailwind CSS**

---

## 1. PHẠM VI TRIỂN KHAI (SCOPE)

Triển khai đầy đủ 2 phân hệ thương mại hóa và trải nghiệm người dùng theo kế hoạch:
1. **Phase 3.1 — Food & Concessions (F&B)**:
   - Cơ sở dữ liệu: Tạo bảng `food_items` và `booking_foods` với khóa ngoại, chỉ mục và ràng buộc toàn vẹn.
   - Backend: CRUD danh mục bắp nước (`FoodItemController`, `AdminFoodItemController`, `FoodItemServiceImpl`), tích hợp chọn bắp nước trong luồng đặt vé (`BookingServiceImpl.createBooking`, tính tổng tiền `ticketTotal + foodTotal - discountAmount`), hiển thị chi tiết bắp nước trong hóa đơn và vé xem phim.
   - Frontend: Giao diện quản trị bắp nước (`AdminFoodItemsView.vue`), modal chọn combo bắp nước trong luồng đặt vé (`FoodSelectionModal.vue`), cập nhật hiển thị trong `BookingView.vue`, `MyBookingsView.vue`.
2. **Phase 3.2 — In-App Notifications**:
   - Cơ sở dữ liệu: Tạo bảng `notifications` lưu trữ thông báo người dùng trong MySQL, khóa chính UUID, khóa ngoại `user_id` và `booking_id` (CASCADE), ràng buộc duy nhất `uk_notifications_booking_type (booking_id, type)`.
   - Backend: Domain enum `NotificationType` (`PAYMENT_SUCCESS`, `BOOKING_CANCELLED`, `REFUND_COMPLETED`), entity `Notification`, repository, service `NotificationServiceImpl` với cơ chế cô lập lỗi `Propagation.REQUIRES_NEW` và chống trùng lặp 2 tầng (Two-level idempotency).
   - Tích hợp luồng đơn hàng: Kích hoạt thông báo tự động khi thanh toán thành công (`confirmPaidBooking`), hủy đơn giữ chỗ (`cancelBooking`), và hoàn tiền thành công (`processBookingRefund`). Giữ nguyên `PENDING_PAYMENT` khi hủy giao dịch trên cổng VNPay (không sinh `BOOKING_CANCELLED`).
   - REST API: Bộ endpoint `/api/v1/notifications` với phân trang, đếm chưa đọc (`unread-count`), đánh dấu đã đọc (`PATCH {id}/read`, `PATCH read-all`).
   - Frontend: Component chuông thông báo `NotificationBell.vue` với polling 30 giây (tự hủy timer khi unmount), dropdown hiển thị thông báo phân loại theo icon/badge, điều hướng đến `/my-bookings` khi click, tích hợp vào cả `DefaultLayout.vue` và `AdminLayout.vue`.
   - Đa ngôn ngữ (i18n): Bổ sung từ điển `notifications` trong `vi.ts` và `en.ts` với 100% key parity.

---

## 2. NGUYÊN TẮC KIẾN TRÚC & RÀNG BUỘC ĐÃ TUÂN THỦ

1. **Database Migration Model**:
   - **Tuyệt đối KHÔNG sử dụng runtime migration**: Không tích hợp Flyway hoặc Liquibase vào runtime Spring Boot (`pom.xml` không chứa migration dependency).
   - Hibernate vận hành nghiêm ngặt ở chế độ `ddl-auto: validate`.
   - Tất cả thay đổi lược đồ cơ sở dữ liệu được lưu dưới dạng tập tin SQL có số hiệu phiên bản trong `src/main/resources/db/migration/`:
     - `V1_2__add_food_and_concessions.sql`: Bảng `food_items` và `booking_foods`.
     - `V1_3__add_user_notifications.sql`: Bảng `notifications`.
   - Áp dụng trực tiếp vào MySQL 8 cục bộ bằng DDL phi phá hủy (additive-only).
2. **Không phân tán / Không thêm hạ tầng phụ**:
   - Không sử dụng Redis để lưu trữ thông báo (MySQL là nguồn chân lý duy nhất).
   - Không sử dụng WebSocket, Server-Sent Events (SSE) hoặc Message Broker (Kafka / RabbitMQ).
   - Frontend thực hiện cơ chế polling định kỳ 30 giây thông qua chuẩn REST API.
3. **Chính sách cô lập lỗi thông báo (Failure Isolation)**:
   - Phương thức tạo thông báo chạy trong giao dịch riêng biệt: `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   - Toàn bộ ngoại lệ phát sinh trong quá trình lưu thông báo được bắt và ghi log cảnh báo (`log.warn`), tuyệt đối không làm gián đoạn hay rollback giao dịch tài chính chính (thanh toán, hủy đơn, hoàn tiền).
4. **Bảo vệ chống trùng lặp 2 tầng (Two-Level Idempotency)**:
   - **Tầng ứng dụng**: Kiểm tra `existsByBookingIdAndType(bookingId, type)` trước khi insert.
   - **Tầng cơ sở dữ liệu**: Ràng buộc duy nhất `uk_notifications_booking_type (booking_id, type)` bảo vệ hệ thống trước tình huống xử lý đồng thời (race condition) từ các webhook lặp lại.
5. **Tương thích ngược (Backwards Compatibility)**:
   - Lớp `BookingServiceImpl` duy trì đầy đủ các constructor quá tải (13, 14, 17 tham số) nối tiếp đến constructor 18 tham số (`@Autowired`), đảm bảo toàn bộ các test suite hiện hữu không bị phá vỡ.

---

## 3. DANH SÁCH TẬP TIN THAY ĐỔI & TẠO MỚI

### Database & Migration:
- **[NEW]** `src/main/resources/db/migration/V1_2__add_food_and_concessions.sql`
- **[NEW]** `src/main/resources/db/migration/V1_3__add_user_notifications.sql`

### Backend Java:
- **[NEW]** `src/main/java/com/cinebook/entity/FoodItem.java`
- **[NEW]** `src/main/java/com/cinebook/entity/BookingFood.java`
- **[NEW]** `src/main/java/com/cinebook/entity/Notification.java`
- **[NEW]** `src/main/java/com/cinebook/enums/NotificationType.java`
- **[NEW]** `src/main/java/com/cinebook/repository/FoodItemRepository.java`
- **[NEW]** `src/main/java/com/cinebook/repository/BookingFoodRepository.java`
- **[NEW]** `src/main/java/com/cinebook/repository/NotificationRepository.java`
- **[NEW]** `src/main/java/com/cinebook/dto/request/CreateFoodItemRequest.java`
- **[NEW]** `src/main/java/com/cinebook/dto/request/UpdateFoodItemRequest.java`
- **[NEW]** `src/main/java/com/cinebook/dto/request/BookingFoodItemRequest.java`
- **[NEW]** `src/main/java/com/cinebook/dto/response/FoodItemResponse.java`
- **[NEW]** `src/main/java/com/cinebook/dto/response/BookingFoodResponse.java`
- **[NEW]** `src/main/java/com/cinebook/dto/response/NotificationResponse.java`
- **[NEW]** `src/main/java/com/cinebook/dto/response/UnreadCountResponse.java`
- **[NEW]** `src/main/java/com/cinebook/mapper/FoodItemMapper.java`
- **[NEW]** `src/main/java/com/cinebook/mapper/NotificationMapper.java`
- **[NEW]** `src/main/java/com/cinebook/service/FoodItemService.java`
- **[NEW]** `src/main/java/com/cinebook/service/impl/FoodItemServiceImpl.java`
- **[NEW]** `src/main/java/com/cinebook/service/NotificationService.java`
- **[NEW]** `src/main/java/com/cinebook/service/impl/NotificationServiceImpl.java`
- **[NEW]** `src/main/java/com/cinebook/controller/FoodItemController.java`
- **[NEW]** `src/main/java/com/cinebook/controller/AdminFoodItemController.java`
- **[NEW]** `src/main/java/com/cinebook/controller/NotificationController.java`
- **[MODIFY]** `src/main/java/com/cinebook/dto/request/CreateBookingRequest.java` (thêm danh sách `foods`)
- **[MODIFY]** `src/main/java/com/cinebook/dto/response/BookingDetailResponse.java` (thêm danh sách `foods`, `foodTotal`)
- **[MODIFY]** `src/main/java/com/cinebook/mapper/BookingMapper.java` (ánh xạ `foods` và `foodTotal`)
- **[MODIFY]** `src/main/java/com/cinebook/service/impl/BookingServiceImpl.java` (tính tiền bắp nước, lưu `booking_foods`, tích hợp dispatch thông báo, bảo lưu constructor quá tải)

### Tests:
- **[NEW]** `src/test/java/com/cinebook/service/FoodItemServiceTest.java`
- **[NEW]** `src/test/java/com/cinebook/controller/FoodItemControllerTest.java`
- **[NEW]** `src/test/java/com/cinebook/controller/AdminFoodItemControllerTest.java`
- **[NEW]** `src/test/java/com/cinebook/service/NotificationServiceTest.java` (11 unit tests)
- **[NEW]** `src/test/java/com/cinebook/controller/NotificationControllerTest.java` (5 unit tests)
- **[NEW]** `src/test/java/com/cinebook/service/BookingNotificationIntegrationTest.java` (6 integration tests)
- **[MODIFY]** `src/test/java/com/cinebook/service/BookingServiceTest.java` (Cập nhật stubbing cho BookingMapper)

### Frontend:
- **[NEW]** `frontend/src/types/food.types.ts`
- **[NEW]** `frontend/src/types/notification.types.ts`
- **[NEW]** `frontend/src/services/food.service.ts`
- **[NEW]** `frontend/src/services/notification.service.ts`
- **[NEW]** `frontend/src/components/booking/FoodSelectionModal.vue`
- **[NEW]** `frontend/src/components/common/NotificationBell.vue`
- **[NEW]** `frontend/src/views/admin/AdminFoodItemsView.vue`
- **[MODIFY]** `frontend/src/views/customer/BookingView.vue` (Tích hợp bước chọn combo bắp nước)
- **[MODIFY]** `frontend/src/views/customer/MyBookingsView.vue` (Hiển thị chi tiết bắp nước trong vé & đơn hàng)
- **[MODIFY]** `frontend/src/layouts/DefaultLayout.vue` (Gắn NotificationBell vào thanh điều hướng khách hàng)
- **[MODIFY]** `frontend/src/layouts/AdminLayout.vue` (Gắn NotificationBell vào thanh tiêu đề admin & thêm mục Quản lý F&B)
- **[MODIFY]** `frontend/src/router/index.ts` (Thêm route `/admin/foods`)
- **[MODIFY]** `frontend/src/locales/vi.ts` (Bổ sung từ điển `food` và `notifications`)
- **[MODIFY]** `frontend/src/locales/en.ts` (Bổ sung từ điển `food` và `notifications` - 100% key parity)

### Documentation:
- **[MODIFY]** `docs/database.md`: Cập nhật lược đồ bảng `food_items`, `booking_foods` (§3.9) và `notifications` (§3.10).
- **[MODIFY]** `docs/api.md`: Thêm hợp đồng API F&B (§23) và Notifications (§24).
- **[MODIFY]** `docs/business-rules.md`: Bổ sung quy tắc nghiệp vụ F&B (§14) và In-App Notifications (§15).
- **[MODIFY]** `docs/documentation-map.md`: Cập nhật Domain 19 và Domain 20, tổng số 29 controllers, 27 views, và các sắc thái kỹ thuật.
- **[NEW]** `docs/implementation-reports/phase-3-commercial-expansion.md`: Báo cáo nghiệm thu hoàn thành này.

---

## 4. KẾT QUẢ KIỂM THỬ VÀ XÁC MINH (VERIFICATION RESULTS)

### 4.1. Backend Automated Tests:
- `NotificationServiceTest`: 11/11 PASS (Bao gồm kiểm tra `existsByBookingIdAndType`, xử lý ngoại lệ an toàn, đánh dấu đã đọc, phân quyền người dùng).
- `NotificationControllerTest`: 5/5 PASS (Kiểm tra phân trang, đếm chưa đọc, đánh dấu đã đọc 1 và tất cả).
- `BookingNotificationIntegrationTest`: 6/6 PASS (Kiểm tra kích hoạt `PAYMENT_SUCCESS`, `BOOKING_CANCELLED`, `REFUND_COMPLETED`, bỏ qua khi hủy VNPay, và nuốt ngoại lệ an toàn).
- `BookingServiceTest`: 83/83 PASS.
- `PaymentServiceTest` + `DemoPaymentIntegrationTest` + `PaymentRefundIntegrationTest`: 46/46 PASS.
- **Full Backend Suite (`.\mvnw.cmd test`)**: **640/642 PASS** (Bảo lưu chính xác 2 ca kiểm thử tích hợp dữ liệu cơ sở dữ liệu cục bộ đã biết trước trong `AuditoriumNormalizationLiveIntegrationTest`).

### 4.2. Frontend Build Verification:
- Chạy lệnh `npm run build` tại thư mục `frontend`:
  - `vue-tsc -b && vite build` hoàn thành không có bất kỳ lỗi TypeScript hay cảnh báo cú pháp nào.
  - Mã nguồn client đóng gói thành công (`dist/index.html`, assets tối ưu hóa).

### 4.3. Database Integrity:
- Bảng `notifications`, `food_items`, `booking_foods` đã được tạo và kích hoạt trên MySQL `cinebook` cục bộ.
- Khởi động Spring Boot với cấu hình `ddl-auto: validate` xác nhận 100% các trường, kiểu dữ liệu, khóa chính và ràng buộc khóa ngoại khớp tuyệt đối với các JPA Entity.

---

## 5. KẾT LUẬN

Giai đoạn **Phase 3 — Commercial Expansion (F&B & In-App Notifications)** đã hoàn thành toàn diện, bảo đảm mọi tiêu chí nghiệp vụ, bảo toàn tính tương thích của hệ thống, và giữ vững các chuẩn mực kiến trúc đơn khối Layered Architecture của CineBook.

