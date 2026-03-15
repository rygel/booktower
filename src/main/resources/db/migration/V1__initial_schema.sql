-- BookTower full schema (consolidated from V1-V10)

CREATE TABLE users (
    id            CHAR(36)     PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_admin      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);

CREATE TABLE libraries (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    path       VARCHAR(500) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_libraries_user_id ON libraries(user_id);

CREATE TABLE books (
    id             CHAR(36)     PRIMARY KEY,
    library_id     CHAR(36)     NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    title          VARCHAR(255) NOT NULL,
    author         VARCHAR(255),
    description    TEXT,
    isbn           VARCHAR(20),
    publisher      VARCHAR(255),
    published_date DATE,
    file_path      VARCHAR(500) NOT NULL,
    file_size      BIGINT       NOT NULL,
    file_hash      VARCHAR(64),
    page_count     INT,
    cover_path     VARCHAR(500),
    series         VARCHAR(255),
    series_index   REAL,
    added_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_books_library_id ON books(library_id);
CREATE INDEX idx_books_title      ON books(title);
CREATE INDEX idx_books_author     ON books(author);
CREATE INDEX idx_books_series     ON books(series);

CREATE TABLE reading_progress (
    id           CHAR(36)      PRIMARY KEY,
    user_id      CHAR(36)      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id      CHAR(36)      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    current_page INT           NOT NULL DEFAULT 1,
    total_pages  INT,
    percentage   DECIMAL(5, 2),
    last_read_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_id)
);

CREATE INDEX idx_progress_user_id ON reading_progress(user_id);
CREATE INDEX idx_progress_book_id ON reading_progress(book_id);

CREATE TABLE bookmarks (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page       INT          NOT NULL,
    title      VARCHAR(255),
    note       TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bookmarks_user_book ON bookmarks(user_id, book_id);

CREATE TABLE user_settings (
    id            CHAR(36)    PRIMARY KEY,
    user_id       CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    setting_key   VARCHAR(50) NOT NULL,
    setting_value TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, setting_key)
);

CREATE TABLE sessions (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_user_id    ON sessions(user_id);
CREATE INDEX idx_sessions_token_hash ON sessions(token_hash);

CREATE TABLE reading_daily (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    date       VARCHAR(10) NOT NULL,
    pages_read INT         NOT NULL DEFAULT 0,
    UNIQUE (user_id, book_id, date)
);

CREATE INDEX idx_reading_daily_user_date ON reading_daily(user_id, date);

CREATE TABLE book_status (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_id)
);

CREATE INDEX idx_book_status_user ON book_status(user_id);

CREATE TABLE book_ratings (
    id         CHAR(36)  PRIMARY KEY,
    user_id    CHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)  NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    rating     SMALLINT  NOT NULL CHECK (rating BETWEEN 1 AND 5),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_id)
);

CREATE INDEX idx_book_ratings_user ON book_ratings(user_id);

CREATE TABLE book_tags (
    id      CHAR(36)    PRIMARY KEY,
    user_id CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    tag     VARCHAR(50) NOT NULL,
    UNIQUE (user_id, book_id, tag)
);

CREATE INDEX idx_book_tags_user_book ON book_tags(user_id, book_id);
CREATE INDEX idx_book_tags_user_tag  ON book_tags(user_id, tag);

CREATE TABLE book_annotations (
    id            CHAR(36)    PRIMARY KEY,
    user_id       CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id       CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page          INT         NOT NULL,
    selected_text TEXT        NOT NULL,
    color         VARCHAR(20) NOT NULL DEFAULT 'yellow',
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_book_annotations_user_book ON book_annotations(user_id, book_id);

CREATE TABLE magic_shelves (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    rule_type  VARCHAR(20)  NOT NULL,
    rule_value VARCHAR(100),
    created_at VARCHAR(30)  NOT NULL
);

CREATE INDEX idx_magic_shelves_user ON magic_shelves(user_id);

CREATE TABLE password_reset_tokens (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at VARCHAR(30) NOT NULL,
    used_at    VARCHAR(30),
    created_at VARCHAR(30) NOT NULL
);

CREATE INDEX        idx_prt_token ON password_reset_tokens(token_hash);
CREATE INDEX        idx_prt_user  ON password_reset_tokens(user_id);

CREATE TABLE api_tokens (
    id           CHAR(36)     PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    created_at   VARCHAR(30)  NOT NULL,
    last_used_at VARCHAR(30)
);

CREATE UNIQUE INDEX idx_api_tokens_hash ON api_tokens(token_hash);
CREATE INDEX        idx_api_tokens_user ON api_tokens(user_id);

CREATE TABLE reading_sessions (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    start_page INT         NOT NULL DEFAULT 0,
    end_page   INT         NOT NULL,
    pages_read INT         NOT NULL DEFAULT 0,
    session_at VARCHAR(30) NOT NULL
);

CREATE INDEX idx_reading_sessions_user_id   ON reading_sessions(user_id);
CREATE INDEX idx_reading_sessions_book_id   ON reading_sessions(book_id);
CREATE INDEX idx_reading_sessions_user_date ON reading_sessions(user_id, session_at);
