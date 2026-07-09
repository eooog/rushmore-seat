DROP INDEX IF EXISTS idx_reservation_hold_token;

ALTER TABLE reservation
    DROP COLUMN hold_token,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE reservation
    ALTER COLUMN version DROP DEFAULT;
