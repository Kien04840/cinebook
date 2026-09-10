-- ========================================================================
-- PHẦN: MIGRATION V1_2 (Phase 3.1: F&B / Food & Concessions)
-- File: src/main/resources/db/migration/V1_2__add_food_and_concessions.sql
-- ========================================================================

CREATE TABLE IF NOT EXISTS food_items (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    price DECIMAL(12, 2) NOT NULL,
    image_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT pk_food_items PRIMARY KEY (id),
    CONSTRAINT uk_food_items_name_deleted UNIQUE (name, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_food_items_status ON food_items (status);
CREATE INDEX idx_food_items_deleted ON food_items (deleted_at);

CREATE TABLE IF NOT EXISTS booking_foods (
    id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36) NOT NULL,
    food_item_id VARCHAR(36) NOT NULL,
    food_name VARCHAR(100) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(12, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_booking_foods PRIMARY KEY (id),
    CONSTRAINT fk_booking_foods_booking FOREIGN KEY (booking_id) 
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_foods_food_item FOREIGN KEY (food_item_id) 
        REFERENCES food_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_booking_foods_booking ON booking_foods (booking_id);
CREATE INDEX idx_booking_foods_food_item ON booking_foods (food_item_id);

-- Seed initial popular food & beverage items
INSERT INTO food_items (id, name, description, price, image_url, status)
VALUES
    ('food-001-popcorn-sweet', 'Bắp Rang Bơ Ngọt (Tiêu chuẩn)', 'Bắp rang bơ giòn rụm hương vị caramel ngọt ngào truyền thống', 45000.00, 'https://images.unsplash.com/photo-1578849278619-e73505e9610f?w=500&auto=format&fit=crop&q=60', 'ACTIVE'),
    ('food-002-popcorn-cheese', 'Bắp Rang Bơ Phô Mai', 'Bắp rang bơ lắc bột phô mai béo ngậy thơm lừng đậm vị', 55000.00, 'https://images.unsplash.com/photo-1585647347483-22b66260dfff?w=500&auto=format&fit=crop&q=60', 'ACTIVE'),
    ('food-003-popcorn-caramel', 'Bắp Rang Bơ Vị Socola', 'Bắp rang giòn tan phủ sốt socola đậm đà cao cấp', 55000.00, 'https://images.unsplash.com/photo-1505686994434-e3cc5abf1330?w=500&auto=format&fit=crop&q=60', 'ACTIVE'),
    ('food-004-drink-coke', 'Nước Ngọt Coca-Cola (22oz)', 'Nước ngọt Coca-Cola mát lạnh sảng khoái size lớn', 30000.00, 'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=500&auto=format&fit=crop&q=60', 'ACTIVE'),
    ('food-005-drink-sprite', 'Nước Ngọt Sprite (22oz)', 'Nước ngọt có ga hương chanh Sprite tươi mát sảng khoái', 30000.00, 'https://images.unsplash.com/photo-1625772299848-391b6a87d7b3?w=500&auto=format&fit=crop&q=60', 'ACTIVE'),
    ('food-006-drink-water', 'Nước Khoáng Dasani (500ml)', 'Nước khoáng tinh khiết đóng chai Dasani thanh mát tiện lợi', 20000.00, 'https://images.unsplash.com/photo-1548839140-29a749e1bc4e?w=500&auto=format&fit=crop&q=60', 'ACTIVE'),
    ('food-007-combo-solo', 'Combo Solo (1 Bắp + 1 Nước)', '1 Bắp rang bơ ngọt tiêu chuẩn + 1 Nước ngọt có ga tự chọn', 69000.00, 'https://images.unsplash.com/photo-1512149177596-f817c7ef5d4c?w=500&auto=format&fit=crop&q=60', 'ACTIVE'),
    ('food-008-combo-couple', 'Combo Couple (1 Bắp Lớn + 2 Nước)', '1 Bắp rang bơ lớn 2 vị tùy chọn + 2 Nước ngọt có ga 22oz', 99000.00, 'https://images.unsplash.com/photo-1585647347384-2593bc35786b?w=500&auto=format&fit=crop&q=60', 'ACTIVE')
ON DUPLICATE KEY UPDATE name=name;

