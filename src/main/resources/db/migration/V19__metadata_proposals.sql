-- V19: metadata proposal / review workflow

CREATE TABLE metadata_proposals (
    id           CHAR(36)     PRIMARY KEY,
    book_id      CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    user_id      CHAR(36)     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source       VARCHAR(50)  NOT NULL,
    data_json    TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    proposed_at  VARCHAR(30)  NOT NULL,
    reviewed_at  VARCHAR(30)
);

CREATE INDEX idx_metadata_proposals_book ON metadata_proposals(book_id);
CREATE INDEX idx_metadata_proposals_user ON metadata_proposals(user_id);
