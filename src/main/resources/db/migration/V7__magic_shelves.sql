CREATE TABLE magic_shelves (
    id           VARCHAR(36)  PRIMARY KEY,
    user_id      VARCHAR(36)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    rule_type    VARCHAR(20)  NOT NULL,
    rule_value   VARCHAR(100),
    created_at   VARCHAR(30)  NOT NULL,
    CONSTRAINT fk_magic_shelves_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_magic_shelves_user ON magic_shelves(user_id);
