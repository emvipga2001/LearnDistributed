-- V2__seed_sample_data.sql
-- Seed sample inventory items for testing

INSERT INTO inventory_items (id, item_code, item_name, total_capacity, reserved_capacity, allocated_pos_buffer, version)
VALUES 
    (1, 'SEAT-VIP-A101', 'Ghe VIP Hang A So 101', 1, 0, 0, 0),
    (2, 'SEAT-VIP-A102', 'Ghe VIP Hang A So 102', 1, 0, 0, 0),
    (3, 'TICKET-GA-ZONE-B', 'Ve dung Khu B (Flash Sale)', 100, 0, 0, 0)
ON CONFLICT (id) DO NOTHING;

SELECT setval('inventory_items_id_seq', (SELECT GREATEST(MAX(id), 1) FROM inventory_items));
