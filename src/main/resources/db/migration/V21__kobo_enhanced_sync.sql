-- V21: Enhanced Kobo sync — location types and library snapshot token

-- Store CFI / location type alongside page progress
ALTER TABLE reading_progress ADD COLUMN location      VARCHAR(2000);
ALTER TABLE reading_progress ADD COLUMN location_type VARCHAR(20);

-- Track the last delta-sync token per device (reuse last_sync_at as the token source)
-- No new table needed — last_sync_at on kobo_devices serves as the delta anchor.
