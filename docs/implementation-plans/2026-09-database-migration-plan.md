# CineBook — Kế Hoạch Migration Cơ Sở Dữ Liệu An Toàn (Database Migration Plan)
## Chiến Lược Mở Rộng Schema Additive-Only & Quản Trị Rủi Ro Dữ Liệu

> **Mục tiêu**: Định nghĩa quy trình, ma trận migration và phương án an toàn dữ liệu tuyệt đối cho tất cả các thay đổi cơ sở dữ liệu MySQL 8 trong 5 Phase tiếp theo.  
> **Nguyên tắc bất biến**: MySQL là Nguồn chân lý duy nhất. **ADDITIVE ONLY**. Không DROP table/column, không TRUNCATE, không sửa đổi kiểu dữ liệu cột hiện có trên database thật.  
> **Trạng thái Hibernate**: `spring.jpa.hibernate.ddl-auto: validate` (Bắt buộc JPA entity phải khớp 100% với schema DB thực tế trước khi ứng dụng khởi động thành công).  
> **Lưu ý đặc biệt**: Các đoạn mã SQL trong tài liệu này là **TÀI LIỆU THIẾT KẾ MẪU**. Tuyệt đối **CHƯA ĐƯỢC THỰC THI** trong task Planning này.  

---

## 1. Ma Trận Migration Tổng Thể (Migration Matrix)

| Phase | Mã Migration | Bảng Mới | Cột Mới | Ràng Buộc Khóa Ngoại (FK) | Chỉ Mục (Index) | Tác Động Dữ Liệu Hiện Có | Chiến Lược Rollback |
|---|---|---|---|---|---|---|---|
| **Phase 1** | `V1_1__add_email_verification_tokens.sql` | `email_verification_tokens` | Không | `fk_email_verification_tokens_user` $\rightarrow$ `users(id)` | `idx_email_verification_tokens_user`, `idx_email_verification_tokens_expires` | **Không tác động** (Bảng mới độc lập, 0 rows cũ bị ảnh hưởng) | **Logical Rollback**: Tắt flow verify, không gửi mail. Bảng tồn tại không gây hại. |
| **Phase 3.1** | `V1_2__add_food_and_concessions.sql` | `food_items`, `booking_foods` | `bookings.total_food_amount` (`DECIMAL(12,2) DEFAULT 0.00`) | `fk_booking_foods_booking` $\rightarrow$ `bookings(id)`, `fk_booking_foods_food_item` $\rightarrow$ `food_items(id)` | `idx_food_items_category_active`, `idx_booking_foods_booking_id` | **An toàn tuyệt đối** (Mọi booking cũ tự động nhận `total_food_amount = 0.00`, không làm đổi tổng tiền đơn cũ) | **Forward-Fix**: Ẩn bước chọn bắp nước trên UI, các booking cũ vẫn hợp lệ. |
| **Phase 3.2** | `V1_3__add_user_notifications.sql` | `notifications` | Không | `fk_notifications_user` $\rightarrow$ `users(id)` | `idx_notifications_user_read_created` | **Không tác động** (Bảng mới hoàn toàn) | **Logical Rollback**: Ẩn icon chuông trên Navbar, vô hiệu hóa trigger tạo notification. |

---

## 2. Trả Lời 11 Tiêu Chí Kiểm Tra Nghiệp Vụ Cho Từng Migration

### Migration 1: Xác Thực Email (`V1_1__add_email_verification_tokens.sql`)
1. **Có cần migration không?**: CÓ, cần lưu token xác thực email có thời hạn (TTL 24 giờ).
2. **Có table mới không?**: CÓ, bảng `email_verification_tokens`.
3. **Có column mới không?**: KHÔNG thêm column vào bảng hiện có. Cột `email_verified` đã có sẵn trong bảng `users`.
4. **Default value là gì?**: `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`.
5. **Existing rows xử lý thế nào?**: Không ảnh hưởng đến người dùng hiện tại; người dùng cũ vẫn giữ nguyên trạng thái `email_verified` của họ.
6. **Hibernate entity mapping thay đổi thế nào?**: Tạo mới entity `EmailVerificationToken.java` ánh xạ bảng `email_verification_tokens`.
7. **`ddl-auto=validate` sẽ validate gì?**: Kiểm tra sự tồn tại của bảng, tên cột, kiểu dữ liệu `VARCHAR(36)`, `VARCHAR(100)`, và khóa chính `pk_email_verification_tokens`.
8. **Có cần seed data không?**: KHÔNG. Token chỉ sinh ra khi có sự kiện đăng ký hoặc yêu cầu gửi lại email.
9. **Seed có idempotent không?**: N/A.
10. **Backup/restore strategy?**: Sao lưu bảng `users` trước khi triển khai (dù migration không tác động vào cấu trúc bảng `users`).
11. **Nếu deployment thất bại giữa chừng thì xử lý thế nào?**: Nếu tạo bảng thất bại, Spring Boot không khởi động được do `ddl-auto: validate` kiểm tra thấy thiếu bảng. Khắc phục bằng cách chạy lại DDL hoặc xóa bảng chưa hoàn chỉnh để thử lại.

---

### Migration 2: Bắp Nước & Combo F&B (`V1_2__add_food_and_concessions.sql`)
1. **Có cần migration không?**: CÓ, đây là tính năng thương mại mở rộng cốt lõi của Phase 3.
2. **Có table mới không?**: CÓ, 2 bảng: `food_items` (danh mục món ăn) và `booking_foods` (chi tiết món theo đơn).
3. **Có column mới không?**: CÓ, thêm cột `total_food_amount` vào bảng `bookings`.
4. **Default value là gì?**: `total_food_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00`.
5. **Existing rows xử lý thế nào?**: Tất cả các đơn hàng lịch sử trong bảng `bookings` sẽ tự động nhận giá trị `0.00`, đảm bảo công thức tài chính $\text{grandTotal} = \text{totalTicketAmount} + 0.00 - \text{discountAmount}$ không bị sai lệch một xu nào.
6. **Hibernate entity mapping thay đổi thế nào?**:
   - Tạo mới `FoodItem.java`, `BookingFood.java`.
   - Cập nhật `Booking.java`: Thêm `private BigDecimal totalFoodAmount;` và `@OneToMany List<BookingFood> bookingFoods`.
7. **`ddl-auto=validate` sẽ validate gì?**: Kiểm tra kiểu dữ liệu `DECIMAL(12,2)` của cột `total_food_amount` trong `bookings`, các bảng mới và các quan hệ Foreign Key.
8. **Có cần seed data không?**: CÓ, cần khởi tạo 6 món bắp nước / combo mẫu để hệ thống có thể chạy thử ngay.
9. **Seed có idempotent không?**: CÓ, sử dụng mệnh đề `INSERT INTO food_items ... ON DUPLICATE KEY UPDATE` hoặc kiểm tra sự tồn tại của `name` trước khi chèn.
10. **Backup/restore strategy?**: Bắt buộc tạo snapshot backup toàn bộ bảng `bookings` trước khi thực thi lệnh `ALTER TABLE`.
11. **Nếu deployment thất bại giữa chừng thì xử lý thế nào?**: Nếu cột `total_food_amount` đã được thêm nhưng code backend chưa deploy, ứng dụng cũ vẫn hoạt động bình thường vì cột mới có default value và không bắt buộc truy vấn.

---

### Migration 3: Thông Báo Nội Bộ (`V1_3__add_user_notifications.sql`)
1. **Có cần migration không?**: CÓ, để lưu trữ thông báo người dùng trên hệ thống.
2. **Có table mới không?**: CÓ, bảng `notifications`.
3. **Có column mới không?**: KHÔNG.
4. **Default value là gì?**: `is_read BOOLEAN NOT NULL DEFAULT FALSE`, `created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`.
5. **Existing rows xử lý thế nào?**: Bảng mới, không ảnh hưởng dữ liệu cũ.
6. **Hibernate entity mapping thay đổi thế nào?**: Tạo mới entity `Notification.java`.
7. **`ddl-auto=validate` sẽ validate gì?**: Kiểm tra cấu trúc bảng `notifications`, index kết hợp `idx_notifications_user_read_created (user_id, is_read, created_at)`.
8. **Có cần seed data không?**: KHÔNG.
9. **Seed có idempotent không?**: N/A.
10. **Backup/restore strategy?**: Không cần backup riêng cho bảng này vì đây là bảng tạo mới.
11. **Nếu deployment thất bại giữa chừng thì xử lý thế nào?**: Xóa bảng rỗng và thực thi lại DDL.

---

## 3. Thiết Kế Chi Tiết DDL Migration (Thiết Kế Mẫu — Không Thực Thi)

```sql
-- ========================================================================
-- PHẦN 1: MIGRATION V1_1 (Phase 1: Email Verification)
-- File: src/main/resources/db/migration/V1_1__add_email_verification_tokens.sql
-- ========================================================================
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(100) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_tokens_token UNIQUE (token),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) 
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_tokens_expires ON email_verification_tokens (expires_at);

-- ========================================================================
-- PHẦN 2: MIGRATION V1_2 (Phase 3: Bắp nước / Combo F&B)
-- File: src/main/resources/db/migration/V1_2__add_food_and_concessions.sql
-- ========================================================================
CREATE TABLE IF NOT EXISTS food_items (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(12, 2) NOT NULL,
    image_url VARCHAR(500),
    category VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_food_items PRIMARY KEY (id),
    CONSTRAINT uk_food_items_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_food_items_category_active ON food_items (category, is_active);

-- Thêm cột total_food_amount vào bookings với DEFAULT 0.00 an toàn tuyệt đối
ALTER TABLE bookings 
    ADD COLUMN IF NOT EXISTS total_food_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER total_ticket_amount;

CREATE TABLE IF NOT EXISTS booking_foods (
    id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36) NOT NULL,
    food_item_id VARCHAR(36) NOT NULL,
    food_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    total_price DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_booking_foods PRIMARY KEY (id),
    CONSTRAINT fk_booking_foods_booking FOREIGN KEY (booking_id) 
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_foods_food_item FOREIGN KEY (food_item_id) 
        REFERENCES food_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_booking_foods_booking_id ON booking_foods (booking_id);

-- ========================================================================
-- PHẦN 3: MIGRATION V1_3 (Phase 3: Thông báo nội bộ)
-- File: src/main/resources/db/migration/V1_3__add_user_notifications.sql
-- ========================================================================
CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    reference_type VARCHAR(50),
    reference_id VARCHAR(36),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) 
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notifications_user_read_created ON notifications (user_id, is_read, created_at);
```

---

## 4. Chiến Lược Rollback An Toàn (Tuyệt Đối Không Destructive Trên Production)

### A. Nguyên Tắc Cốt Lõi
- **CẤM TUYỆT ĐỐI**: Không bao giờ chạy các lệnh `DROP TABLE` hoặc `DROP COLUMN` trên cơ sở dữ liệu thật đã đi vào vận hành.
- Nếu một tính năng gặp sự cố nghiêm trọng sau khi triển khai migration, áp dụng 3 cấp độ phòng ngừa sau:

### B. Ba Cấp Độ Xử Lý Sự Cố (Rollback Strategy)
1. **Cấp độ 1: Logical Rollback (Hoàn tác mềm / Khuyến nghị ưu tiên)**:
   - Thay vì xóa bảng hay xóa cột trong CSDL:
     - Tắt cờ tính năng (Feature Flag) hoặc ẩn giao diện người dùng tương ứng (ví dụ: ẩn bước chọn bắp nước, ẩn chuông thông báo).
     - Hệ thống backend tiếp tục hoạt động bình thường, các cột mới (`total_food_amount = 0.00`) không gây ảnh hưởng đến luồng đặt vé truyền thống.
2. **Cấp độ 2: Forward-Fix Migration (Sửa chữa tiến tới)**:
   - Nếu có lỗi về kiểu dữ liệu hoặc chỉ mục, tạo migration tiếp theo (ví dụ `V1_4__fix_food_index.sql`) để sửa đổi hoặc bổ sung một cách an toàn mà không làm mất dữ liệu đã phát sinh.
3. **Cấp độ 3: Database Restore (Phục hồi từ bản sao lưu)**:
   - Áp dụng trong trường hợp xảy ra sự cố hỏng hóc CSDL ở mức độ nghiêm trọng:
     - Trước mỗi đợt chạy migration, quản trị viên thực hiện xuất snapshot:  
       `mysqldump -u root -p cinebook > backup_pre_migration_YYYYMMDD.sql`
     - Khi có sự cố thảm họa, đưa hệ thống về chế độ bảo trì và phục hồi từ bản snapshot này.
