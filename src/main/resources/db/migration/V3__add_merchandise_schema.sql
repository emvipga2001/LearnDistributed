-- V3__add_merchandise_schema.sql
-- Merchandise Inventory and Reservation Attachment (Banners, Lightsticks)

-- 1. Merchandise Items (Physical goods: Lightsticks, Banners, T-shirts)
CREATE TABLE IF NOT EXISTS merchandise_items (
    id BIGSERIAL PRIMARY KEY,
    item_code VARCHAR(64) NOT NULL UNIQUE,
    item_name VARCHAR(255) NOT NULL,
    total_stock INT NOT NULL CHECK (total_stock >= 0),
    reserved_stock INT NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_merchandise_stock_capacity CHECK (reserved_stock <= total_stock)
);

-- 2. Reservation Merchandises (Items attached to a reservation)
CREATE TABLE IF NOT EXISTS reservation_merchandises (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    merchandise_id BIGINT NOT NULL REFERENCES merchandise_items(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reservation_merchandises_res_id ON reservation_merchandises(reservation_id);
CREATE INDEX idx_reservation_merchandises_merch_id ON reservation_merchandises(merchandise_id);

-- 3. Seed Sample Merchandises (Banner & Lightstick)
INSERT INTO merchandise_items (id, item_code, item_name, total_stock, reserved_stock, unit_price, version)
VALUES 
    (1, 'LIGHTSTICK-VER-2', 'Lightstick Official Version 2', 500, 0, 850000.00, 0),
    (2, 'BANNER-SPECIAL-EDITION', 'Banner Co Vu Special Edition 2026', 1000, 0, 120000.00, 0)
ON CONFLICT (id) DO NOTHING;

SELECT setval('merchandise_items_id_seq', (SELECT GREATEST(MAX(id), 1) FROM merchandise_items));
