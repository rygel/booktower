CREATE TABLE scheduled_tasks (
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

CREATE TABLE task_history (
    id                 CHAR(36)    PRIMARY KEY,
    scheduled_task_id  CHAR(36)    NOT NULL REFERENCES scheduled_tasks(id) ON DELETE CASCADE,
    started_at         VARCHAR(30) NOT NULL,
    finished_at        VARCHAR(30),
    status             VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    message            TEXT
);
