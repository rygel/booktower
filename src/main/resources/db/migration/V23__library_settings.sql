ALTER TABLE libraries ADD COLUMN format_allowlist VARCHAR(500);
ALTER TABLE libraries ADD COLUMN metadata_source VARCHAR(50);
ALTER TABLE libraries ADD COLUMN default_sort VARCHAR(50);

CREATE TABLE library_paths (
    id         CHAR(36)     PRIMARY KEY,
    library_id CHAR(36)     NOT NULL REFERENCES libraries(id) ON DELETE CASCADE,
    path       VARCHAR(500) NOT NULL,
    added_at   VARCHAR(30)  NOT NULL
);
