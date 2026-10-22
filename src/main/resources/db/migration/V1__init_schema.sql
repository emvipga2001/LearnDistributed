-- V1__init_schema.sql
-- Omnichannel Booking & Inventory Reservation Engine - Baseline Schema

-- 1. Inventory Items (Rooms, Seats, Tickets)
CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGSERIAL PRIMARY KEY,
    item_code VARCHAR(64) NOT NULL UNIQUE,
    item_name VARCHAR(255) NOT NULL,
    total_capacity INT NOT NULL CHECK (total_capacity >= 0),
    reserved_capacity INT NOT NULL DEFAULT 0 CHECK (reserved_capacity >= 0),
    allocated_pos_buffer INT NOT NULL DEFAULT 0 CHECK (allocated_pos_buffer >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Constraint: Reserved + POS Buffer must not exceed Total Capacity
ALTER TABLE inventory_items 
    ADD CONSTRAINT chk_inventory_capacity 
    CHECK (reserved_capacity + allocated_pos_buffer <= total_capacity);

-- 2. Reservations
CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    reservation_code VARCHAR(64) NOT NULL UNIQUE,
    channel VARCHAR(32) NOT NULL, -- ONLINE, POS_OFFLINE, POS_ONLINE
    status VARCHAR(32) NOT NULL,  -- PENDING, CONFIRMED, CANCELLED, EXPIRED
    customer_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reservations_status_expires ON reservations(status, expires_at);

-- 3. Reservation Items
CREATE TABLE IF NOT EXISTS reservation_items (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    inventory_item_id BIGINT NOT NULL REFERENCES inventory_items(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reservation_items_inventory ON reservation_items(inventory_item_id);

-- 4. Idempotency Records
CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
    handler_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL, -- PROCESSING, COMPLETED, FAILED
    response_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Transactional Outbox Events
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
