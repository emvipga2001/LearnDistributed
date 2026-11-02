-- V4__add_request_hash_to_idempotency.sql
-- Add request_hash to idempotency_records to prevent payload tampering/mismatch

ALTER TABLE idempotency_records 
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);

CREATE INDEX idx_idempotency_created_at ON idempotency_records(created_at);