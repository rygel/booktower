ALTER TABLE books ADD COLUMN issue_number VARCHAR(20);
ALTER TABLE books ADD COLUMN volume_number VARCHAR(20);
ALTER TABLE books ADD COLUMN comic_series VARCHAR(200);
ALTER TABLE books ADD COLUMN cover_date VARCHAR(30);
ALTER TABLE books ADD COLUMN story_arc VARCHAR(200);

CREATE TABLE book_characters (
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    PRIMARY KEY (book_id, name)
);

CREATE TABLE book_teams (
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    PRIMARY KEY (book_id, name)
);

CREATE TABLE book_locations (
    book_id    CHAR(36)     NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    PRIMARY KEY (book_id, name)
);
