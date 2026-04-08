CREATE TABLE player (
    uuid          UUID        PRIMARY KEY,
    username      VARCHAR(16) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
