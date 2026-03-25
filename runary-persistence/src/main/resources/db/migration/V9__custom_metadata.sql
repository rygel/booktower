-- Custom metadata fields: user-defined key-value pairs per book
CREATE TABLE IF NOT EXISTS book_custom_fields (
    id         CHAR(36) PRIMARY KEY,
    user_id    CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    field_value TEXT,
    created_at VARCHAR(30) NOT NULL,
    updated_at VARCHAR(30) NOT NULL,
    UNIQUE (user_id, book_id, field_name)
);

CREATE INDEX IF NOT EXISTS idx_book_custom_fields_book ON book_custom_fields(book_id, user_id);

-- Custom field definitions: user's field templates (name, type, default)
CREATE TABLE IF NOT EXISTS custom_field_definitions (
    id         CHAR(36) PRIMARY KEY,
    user_id    CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    field_type VARCHAR(20) NOT NULL DEFAULT 'text',
    field_options TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at VARCHAR(30) NOT NULL,
    UNIQUE (user_id, field_name)
);

SELECT 1;
