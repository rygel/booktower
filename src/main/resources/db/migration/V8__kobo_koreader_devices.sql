-- Kobo device tokens for sync
CREATE TABLE kobo_devices (
    token       CHAR(36)     PRIMARY KEY,
    user_id     CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(100),
    last_sync_at TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kobo_devices_user_id ON kobo_devices(user_id);

-- KOReader device tokens for sync
CREATE TABLE koreader_devices (
    token       CHAR(36)     PRIMARY KEY,
    user_id     CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(100),
    last_sync_at TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_koreader_devices_user_id ON koreader_devices(user_id);
