CREATE TABLE audit_log (
    id          CHAR(36)     PRIMARY KEY,
    actor_id    CHAR(36)     NOT NULL,
    actor_name  VARCHAR(100) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id   VARCHAR(100),
    detail      TEXT,
    ip_address  VARCHAR(45),
    occurred_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_actor_id    ON audit_log(actor_id);
CREATE INDEX idx_audit_log_occurred_at ON audit_log(occurred_at);
