ALTER TABLE magic_shelves ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE magic_shelves ADD COLUMN share_token CHAR(36);
CREATE UNIQUE INDEX idx_magic_shelves_share_token ON magic_shelves(share_token);
