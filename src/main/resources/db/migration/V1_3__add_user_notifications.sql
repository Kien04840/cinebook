-- ========================================================================
-- PHẦN: MIGRATION V1_3 (Phase 3.2: In-App Notifications)
-- File: src/main/resources/db/migration/V1_3__add_user_notifications.sql
-- ========================================================================

CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) 
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_booking FOREIGN KEY (booking_id) 
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT uk_notifications_booking_type UNIQUE (booking_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read, created_at);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_booking ON notifications (booking_id);

