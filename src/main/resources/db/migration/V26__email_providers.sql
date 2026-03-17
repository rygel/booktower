CREATE TABLE email_providers (
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
