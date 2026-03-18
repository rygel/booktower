-- BookTower consolidated schema (V1–V42 merged into a single migration)
-- Compatible with both H2 (PostgreSQL mode) and PostgreSQL

-------------------------------------------------------------------
-- Users & authentication
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id                     CHAR(36)     PRIMARY KEY,
    username               VARCHAR(50)  NOT NULL UNIQUE,
    email                  VARCHAR(100) NOT NULL UNIQUE,
    password_hash          VARCHAR(255) NOT NULL,
    created_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_admin               BOOLEAN      NOT NULL DEFAULT FALSE,
    oidc_sub               VARCHAR(255),
    is_library_restricted  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email    ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_oidc_sub ON users(oidc_sub);

-- Per-user granular permissions
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id                     CHAR(36)  PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    can_manage_libraries        BOOLEAN   NOT NULL DEFAULT TRUE,
    can_upload_books            BOOLEAN   NOT NULL DEFAULT TRUE,
    can_download_books          BOOLEAN   NOT NULL DEFAULT TRUE,
    can_delete_books            BOOLEAN   NOT NULL DEFAULT FALSE,
    can_edit_metadata           BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_bookmarks        BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_annotations      BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_reading_progress BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_shelves          BOOLEAN   NOT NULL DEFAULT TRUE,
    can_export_books            BOOLEAN   NOT NULL DEFAULT TRUE,
    can_send_to_device          BOOLEAN   NOT NULL DEFAULT TRUE,
    can_use_kobo_sync           BOOLEAN   NOT NULL DEFAULT TRUE,
    can_use_koreader_sync       BOOLEAN   NOT NULL DEFAULT TRUE,
    can_use_opds                BOOLEAN   NOT NULL DEFAULT TRUE,
    can_use_api_tokens          BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_journal          BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_reading_sessions BOOLEAN   NOT NULL DEFAULT TRUE,
    can_view_stats              BOOLEAN   NOT NULL DEFAULT TRUE,
    can_edit_profile            BOOLEAN   NOT NULL DEFAULT TRUE,
    can_change_password         BOOLEAN   NOT NULL DEFAULT TRUE,
    can_change_email            BOOLEAN   NOT NULL DEFAULT TRUE,
    can_use_search_filters      BOOLEAN   NOT NULL DEFAULT TRUE,
    can_view_audit_log          BOOLEAN   NOT NULL DEFAULT FALSE,
    can_access_komga_api        BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_notebooks        BOOLEAN   NOT NULL DEFAULT TRUE,
    can_manage_fonts            BOOLEAN   NOT NULL DEFAULT TRUE,
    can_access_admin_panel      BOOLEAN   NOT NULL DEFAULT FALSE,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User settings (key-value pairs)
CREATE TABLE IF NOT EXISTS user_settings (
    id            CHAR(36)    PRIMARY KEY,
    user_id       CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    setting_key   VARCHAR(50) NOT NULL,
    setting_value TEXT,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, setting_key)
);

-------------------------------------------------------------------
-- Sessions, tokens & authentication
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sessions (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id    ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    token        CHAR(36)  PRIMARY KEY,
    user_id      CHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMP NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at VARCHAR(30) NOT NULL,
    used_at    VARCHAR(30),
    created_at VARCHAR(30) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_prt_token ON password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_prt_user  ON password_reset_tokens(user_id);

CREATE TABLE IF NOT EXISTS api_tokens (
    id           CHAR(36)     PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    created_at   VARCHAR(30)  NOT NULL,
    last_used_at VARCHAR(30)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_api_tokens_hash ON api_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_api_tokens_user ON api_tokens(user_id);

-- OPDS-specific separate credentials
CREATE TABLE IF NOT EXISTS opds_credentials (
    user_id       CHAR(36)     PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    opds_username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    VARCHAR(30)  NOT NULL,
    updated_at    VARCHAR(30)  NOT NULL,
    UNIQUE (opds_username)
);

-------------------------------------------------------------------
-- Libraries
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS libraries (
    id                  CHAR(36)     PRIMARY KEY,
    user_id             CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    path                VARCHAR(500) NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    format_allowlist    VARCHAR(500),
    metadata_source     VARCHAR(50),
    default_sort        VARCHAR(50),
    file_naming_pattern VARCHAR(500),
    icon_path           VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_libraries_user_id ON libraries(user_id);

-- Additional library paths (multi-path support)
CREATE TABLE IF NOT EXISTS library_paths (
    id         CHAR(36)     PRIMARY KEY,
    library_id CHAR(36)     NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    path       VARCHAR(500) NOT NULL,
    added_at   VARCHAR(30)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_library_paths_library_id ON library_paths(library_id);

-- Per-user library access control
CREATE TABLE IF NOT EXISTS user_library_access (
    user_id    CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    library_id CHAR(36) NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, library_id)
);

-------------------------------------------------------------------
-- Books
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS books (
    id                          CHAR(36)     PRIMARY KEY,
    library_id                  CHAR(36)     NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    title                       VARCHAR(255) NOT NULL,
    author                      VARCHAR(255),
    description                 TEXT,
    isbn                        VARCHAR(20),
    publisher                   VARCHAR(255),
    published_date              DATE,
    file_path                   VARCHAR(500),
    file_size                   BIGINT,
    file_hash                   VARCHAR(64),
    page_count                  INT,
    cover_path                  VARCHAR(500),
    series                      VARCHAR(255),
    series_index                REAL,
    added_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_missing                BOOLEAN      NOT NULL DEFAULT FALSE,
    reading_direction           VARCHAR(3)   DEFAULT 'ltr',
    subtitle                    VARCHAR(500),
    language                    VARCHAR(10),
    content_rating              VARCHAR(20),
    age_rating                  VARCHAR(20),
    goodreads_id                VARCHAR(50),
    hardcover_id                VARCHAR(50),
    comicvine_id                VARCHAR(50),
    openlibrary_id              VARCHAR(50),
    google_books_id             VARCHAR(50),
    amazon_id                   VARCHAR(50),
    audible_id                  VARCHAR(50),
    issue_number                VARCHAR(20),
    volume_number               VARCHAR(20),
    comic_series                VARCHAR(200),
    cover_date                  VARCHAR(30),
    story_arc                   VARCHAR(200),
    book_format                 VARCHAR(20)  NOT NULL DEFAULT 'EBOOK',
    community_rating            REAL,
    community_rating_count      INT,
    community_rating_source     VARCHAR(50),
    community_rating_fetched_at VARCHAR(30)
);

CREATE INDEX IF NOT EXISTS idx_books_library_id ON books(library_id);
CREATE INDEX IF NOT EXISTS idx_books_title      ON books(title);
CREATE INDEX IF NOT EXISTS idx_books_author     ON books(author);
CREATE INDEX IF NOT EXISTS idx_books_series     ON books(series);
CREATE INDEX IF NOT EXISTS idx_books_format     ON books(book_format);

-- Multi-author support (many-to-many)
CREATE TABLE IF NOT EXISTS book_authors (
    id           CHAR(36)     PRIMARY KEY,
    book_id      CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    author_name  VARCHAR(255) NOT NULL,
    author_order INT          NOT NULL DEFAULT 0,
    UNIQUE (book_id, author_name)
);

CREATE INDEX IF NOT EXISTS idx_book_authors_book_id ON book_authors(book_id);

-- Multi-file audiobook chapter support
CREATE TABLE IF NOT EXISTS book_files (
    id           CHAR(36)     PRIMARY KEY,
    book_id      CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    track_index  INT          NOT NULL,
    title        VARCHAR(500),
    file_path    VARCHAR(500) NOT NULL,
    file_size    BIGINT       NOT NULL DEFAULT 0,
    duration_sec INT,
    added_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (book_id, track_index)
);

CREATE INDEX IF NOT EXISTS idx_book_files_book_id ON book_files(book_id);

-- Alternative formats and supplementary files per book
CREATE TABLE IF NOT EXISTS book_formats (
    id         CHAR(36)     PRIMARY KEY,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    file_path  VARCHAR(500) NOT NULL,
    file_size  BIGINT       NOT NULL DEFAULT 0,
    format     VARCHAR(20)  NOT NULL,
    is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
    label      VARCHAR(100),
    added_at   VARCHAR(30)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_book_formats_book_id ON book_formats(book_id);

-- Per-field metadata locking
CREATE TABLE IF NOT EXISTS book_metadata_locks (
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    field_name VARCHAR(50) NOT NULL,
    locked_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (book_id, field_name)
);

-- Audiobook-specific metadata (narrator, abridged flag, cover)
CREATE TABLE IF NOT EXISTS book_audiobook_meta (
    book_id      CHAR(36)     PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    narrator     VARCHAR(255),
    abridged     BOOLEAN      NOT NULL DEFAULT FALSE,
    audio_cover  VARCHAR(500),
    duration_sec INT,
    updated_at   VARCHAR(30)  NOT NULL
);

-- Comic metadata: characters, teams, locations
CREATE TABLE IF NOT EXISTS book_characters (
    book_id CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name    VARCHAR(200) NOT NULL,
    PRIMARY KEY (book_id, name)
);

CREATE TABLE IF NOT EXISTS book_teams (
    book_id CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name    VARCHAR(200) NOT NULL,
    PRIMARY KEY (book_id, name)
);

CREATE TABLE IF NOT EXISTS book_locations (
    book_id CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name    VARCHAR(200) NOT NULL,
    PRIMARY KEY (book_id, name)
);

-- Full-text search content store and indexing queue
-- PostgreSQL-specific columns (tsvector, GIN index, trigger) are applied
-- programmatically by FtsService.initialize() only when FTS is enabled.
CREATE TABLE IF NOT EXISTS book_content (
    book_id    CHAR(36)     PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    content    TEXT,
    status     VARCHAR(10)  NOT NULL DEFAULT 'pending',
    indexed_at TIMESTAMP,
    error_msg  VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_book_content_status ON book_content(status);

-- Comic page hashes for duplicate detection
CREATE TABLE IF NOT EXISTS comic_hash_queue (
    book_id    CHAR(36)    PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    status     VARCHAR(10) NOT NULL DEFAULT 'pending',
    queued_at  TIMESTAMP   NOT NULL,
    indexed_at TIMESTAMP,
    error_msg  VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_comic_hash_queue_status ON comic_hash_queue(status);

CREATE TABLE IF NOT EXISTS comic_page_hashes (
    book_id    CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page_index INT      NOT NULL,
    phash      BIGINT   NOT NULL,
    PRIMARY KEY (book_id, page_index)
);

CREATE INDEX IF NOT EXISTS idx_comic_page_hashes_phash ON comic_page_hashes(phash);

-------------------------------------------------------------------
-- Reading progress & sessions
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS reading_progress (
    id            CHAR(36)      PRIMARY KEY,
    user_id       CHAR(36)      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id       CHAR(36)      NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    current_page  INT           NOT NULL DEFAULT 1,
    total_pages   INT,
    percentage    DECIMAL(5, 2),
    last_read_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    location      VARCHAR(2000),
    location_type VARCHAR(20),
    UNIQUE (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_progress_user_id ON reading_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_progress_book_id ON reading_progress(book_id);

CREATE TABLE IF NOT EXISTS reading_daily (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    date       VARCHAR(10) NOT NULL,
    pages_read INT         NOT NULL DEFAULT 0,
    UNIQUE (user_id, book_id, date)
);

CREATE INDEX IF NOT EXISTS idx_reading_daily_user_date ON reading_daily(user_id, date);

CREATE TABLE IF NOT EXISTS reading_sessions (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    start_page INT         NOT NULL DEFAULT 0,
    end_page   INT         NOT NULL,
    pages_read INT         NOT NULL DEFAULT 0,
    session_at VARCHAR(30) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reading_sessions_user_id   ON reading_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_reading_sessions_book_id   ON reading_sessions(book_id);
CREATE INDEX IF NOT EXISTS idx_reading_sessions_user_date ON reading_sessions(user_id, session_at);

-- Audiobook listening sessions
CREATE TABLE IF NOT EXISTS listen_sessions (
    id               CHAR(36)    PRIMARY KEY,
    user_id          CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id          CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    start_pos_sec    INT         NOT NULL DEFAULT 0,
    end_pos_sec      INT         NOT NULL DEFAULT 0,
    seconds_listened INT         NOT NULL DEFAULT 0,
    session_at       VARCHAR(30) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_listen_sessions_user_id   ON listen_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_listen_sessions_book_id   ON listen_sessions(book_id);
CREATE INDEX IF NOT EXISTS idx_listen_sessions_user_date ON listen_sessions(user_id, session_at);

-- Audiobook playback position per user/book
CREATE TABLE IF NOT EXISTS listen_progress (
    user_id      CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id      CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    position_sec INT         NOT NULL DEFAULT 0,
    total_sec    INT,
    updated_at   VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, book_id)
);

-------------------------------------------------------------------
-- User book data: status, ratings, tags, annotations, bookmarks
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS book_status (
    id         CHAR(36)    PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_book_status_user ON book_status(user_id);

CREATE TABLE IF NOT EXISTS book_ratings (
    id         CHAR(36)  PRIMARY KEY,
    user_id    CHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)  NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    rating     SMALLINT  NOT NULL CHECK (rating BETWEEN 1 AND 5),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_book_ratings_user ON book_ratings(user_id);

CREATE TABLE IF NOT EXISTS book_tags (
    id      CHAR(36)    PRIMARY KEY,
    user_id CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    tag     VARCHAR(50) NOT NULL,
    UNIQUE (user_id, book_id, tag)
);

CREATE INDEX IF NOT EXISTS idx_book_tags_user_book ON book_tags(user_id, book_id);
CREATE INDEX IF NOT EXISTS idx_book_tags_user_tag  ON book_tags(user_id, tag);

CREATE TABLE IF NOT EXISTS book_categories (
    id       CHAR(36)     PRIMARY KEY,
    user_id  CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id  CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    UNIQUE (user_id, book_id, category)
);

CREATE INDEX IF NOT EXISTS idx_book_categories_book_id ON book_categories(book_id);
CREATE INDEX IF NOT EXISTS idx_book_categories_user_id ON book_categories(user_id);

CREATE TABLE IF NOT EXISTS book_moods (
    id      CHAR(36)    PRIMARY KEY,
    user_id CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    mood    VARCHAR(50) NOT NULL,
    UNIQUE (user_id, book_id, mood)
);

CREATE INDEX IF NOT EXISTS idx_book_moods_book_id ON book_moods(book_id);
CREATE INDEX IF NOT EXISTS idx_book_moods_user_id ON book_moods(user_id);

CREATE TABLE IF NOT EXISTS book_annotations (
    id            CHAR(36)    PRIMARY KEY,
    user_id       CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id       CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page          INT         NOT NULL,
    selected_text TEXT        NOT NULL,
    color         VARCHAR(20) NOT NULL DEFAULT 'yellow',
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_book_annotations_user_book ON book_annotations(user_id, book_id);

CREATE TABLE IF NOT EXISTS bookmarks (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    page       INT          NOT NULL,
    title      VARCHAR(255),
    note       TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bookmarks_user_book ON bookmarks(user_id, book_id);

-------------------------------------------------------------------
-- Reviews, journals, notebooks
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS book_reviews (
    id         CHAR(36)    PRIMARY KEY,
    book_id    CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id    CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating     INT,
    title      VARCHAR(200),
    body       TEXT        NOT NULL,
    spoiler    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at VARCHAR(30) NOT NULL,
    updated_at VARCHAR(30) NOT NULL,
    UNIQUE (book_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_book_reviews_book_id ON book_reviews(book_id);
CREATE INDEX IF NOT EXISTS idx_book_reviews_user_id ON book_reviews(user_id);

CREATE TABLE IF NOT EXISTS book_journal_entries (
    id         CHAR(36)  PRIMARY KEY,
    user_id    CHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id    CHAR(36)  NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    title      VARCHAR(255),
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_journal_user_book ON book_journal_entries(user_id, book_id);
CREATE INDEX IF NOT EXISTS idx_journal_user      ON book_journal_entries(user_id);

CREATE TABLE IF NOT EXISTS book_notebooks (
    id         CHAR(36)     PRIMARY KEY,
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL DEFAULT '',
    created_at VARCHAR(30)  NOT NULL,
    updated_at VARCHAR(30)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_book_notebooks_book_id ON book_notebooks(book_id);
CREATE INDEX IF NOT EXISTS idx_book_notebooks_user_id ON book_notebooks(user_id);

-------------------------------------------------------------------
-- Shelves
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS magic_shelves (
    id          CHAR(36)     PRIMARY KEY,
    user_id     CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    rule_type   VARCHAR(20)  NOT NULL,
    rule_value  VARCHAR(100),
    created_at  VARCHAR(30)  NOT NULL,
    is_public   BOOLEAN      NOT NULL DEFAULT FALSE,
    share_token CHAR(36)
);

CREATE INDEX IF NOT EXISTS idx_magic_shelves_user ON magic_shelves(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_magic_shelves_share_token ON magic_shelves(share_token);

-------------------------------------------------------------------
-- Device sync: Kobo, KOReader
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS kobo_devices (
    token        CHAR(36)     PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name  VARCHAR(100),
    last_sync_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kobo_devices_user_id ON kobo_devices(user_id);

CREATE TABLE IF NOT EXISTS koreader_devices (
    token        CHAR(36)     PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name  VARCHAR(100),
    last_sync_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_koreader_devices_user_id ON koreader_devices(user_id);

-- Hardcover.app book ID mappings per user
CREATE TABLE IF NOT EXISTS hardcover_book_mappings (
    user_id              CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id              CHAR(36) NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    hardcover_book_id    INT      NOT NULL,
    hardcover_edition_id INT,
    last_synced_at       VARCHAR(30),
    PRIMARY KEY (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_hardcover_mappings_user ON hardcover_book_mappings(user_id);

-------------------------------------------------------------------
-- Email delivery
-------------------------------------------------------------------

-- Saved email recipients (e.g. Kindle addresses)
CREATE TABLE IF NOT EXISTS email_recipients (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label      VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, email)
);

CREATE INDEX IF NOT EXISTS idx_email_recipients_user_id ON email_recipients(user_id);

-- Multiple email provider configurations
CREATE TABLE IF NOT EXISTS email_providers (
    id           CHAR(36)     PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    host         VARCHAR(255) NOT NULL,
    port         INT          NOT NULL DEFAULT 587,
    username     VARCHAR(255) NOT NULL,
    password     VARCHAR(500) NOT NULL,
    from_address VARCHAR(255) NOT NULL,
    use_tls      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   VARCHAR(30)  NOT NULL,
    updated_at   VARCHAR(30)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_email_providers_default ON email_providers(is_default);

-------------------------------------------------------------------
-- Custom user fonts for EPUB reader
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS user_fonts (
    id            CHAR(36)     PRIMARY KEY,
    user_id       CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename      VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size     BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, filename)
);

CREATE INDEX IF NOT EXISTS idx_user_fonts_user_id ON user_fonts(user_id);

-------------------------------------------------------------------
-- Metadata proposals (review workflow)
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS metadata_proposals (
    id          CHAR(36)    PRIMARY KEY,
    book_id     CHAR(36)    NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id     CHAR(36)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source      VARCHAR(50) NOT NULL,
    data_json   TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    proposed_at VARCHAR(30) NOT NULL,
    reviewed_at VARCHAR(30)
);

CREATE INDEX IF NOT EXISTS idx_metadata_proposals_book ON metadata_proposals(book_id);
CREATE INDEX IF NOT EXISTS idx_metadata_proposals_user ON metadata_proposals(user_id);

-------------------------------------------------------------------
-- Audit log
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_log (
    id           CHAR(36)     PRIMARY KEY,
    actor_id     CHAR(36)     NOT NULL,
    actor_name   VARCHAR(100) NOT NULL,
    action       VARCHAR(100) NOT NULL,
    target_type  VARCHAR(50),
    target_id    VARCHAR(100),
    detail       TEXT,
    ip_address   VARCHAR(45),
    occurred_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    country_code VARCHAR(2),
    country_name VARCHAR(100),
    city         VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor_id    ON audit_log(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_occurred_at ON audit_log(occurred_at);

-------------------------------------------------------------------
-- Scheduled tasks & task history
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS scheduled_tasks (
    id              CHAR(36)     PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    task_type       VARCHAR(50)  NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_run_at     VARCHAR(30),
    next_run_at     VARCHAR(30),
    created_at      VARCHAR(30)  NOT NULL,
    updated_at      VARCHAR(30)  NOT NULL
);

CREATE TABLE IF NOT EXISTS task_history (
    id                CHAR(36)    PRIMARY KEY,
    scheduled_task_id CHAR(36)    NOT NULL REFERENCES scheduled_tasks(id) ON DELETE CASCADE,
    started_at        VARCHAR(30) NOT NULL,
    finished_at       VARCHAR(30),
    status            VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    message           TEXT
);

-------------------------------------------------------------------
-- Notifications
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS notifications (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at VARCHAR(30)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);

-------------------------------------------------------------------
-- Saved filter presets
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS filter_presets (
    id         CHAR(36)     PRIMARY KEY,
    user_id    CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    filters    TEXT         NOT NULL,
    created_at VARCHAR(30)  NOT NULL,
    updated_at VARCHAR(30)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_filter_presets_user_id ON filter_presets(user_id);

-------------------------------------------------------------------
-- Telemetry (opt-in anonymous usage stats)
-------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS telemetry_events (
    id          CHAR(36)     PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    payload     TEXT,
    recorded_at VARCHAR(30)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_event_type  ON telemetry_events(event_type);
CREATE INDEX IF NOT EXISTS idx_telemetry_recorded_at ON telemetry_events(recorded_at);
