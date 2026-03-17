-- Flag on user: when true, user only sees explicitly granted libraries
ALTER TABLE users ADD COLUMN is_library_restricted BOOLEAN NOT NULL DEFAULT FALSE;

-- Explicit grant: (user_id, library_id) pairs
CREATE TABLE user_library_access (
    user_id    CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    library_id CHAR(36) NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, library_id)
);
