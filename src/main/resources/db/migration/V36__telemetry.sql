CREATE TABLE telemetry_events (
    id         CHAR(36)    PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload    TEXT,
    recorded_at VARCHAR(30) NOT NULL
);

CREATE INDEX idx_telemetry_event_type ON telemetry_events(event_type);
CREATE INDEX idx_telemetry_recorded_at ON telemetry_events(recorded_at);
