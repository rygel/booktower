CREATE TABLE filter_presets (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    filters    TEXT         NOT NULL,
    created_at VARCHAR(30)  NOT NULL,
    updated_at VARCHAR(30)  NOT NULL
);

CREATE INDEX idx_filter_presets_user_id ON filter_presets(user_id);
