-- V5__add_index_on_idempotency_created_at.sql
-- Index on created_at for fast retention cleanup and range queries

CREATE INDEX IF NOT EXISTS idx_idempotency_created_at 
    ON idempotency_records(created_at);
