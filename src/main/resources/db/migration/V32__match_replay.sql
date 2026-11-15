-- The replay of a match of the custom server (spec 024, ADR-0026): the file the plugin recorded, opaque to the API,
-- one per match at most, kept for ever and deleted with its match. The list of the replays reads the other columns
-- only: map, duration and format version are given by the plugin when it sends the file.
CREATE TABLE match_replay (
    match_id       BIGINT      PRIMARY KEY REFERENCES match (id) ON DELETE CASCADE,
    map            VARCHAR(64) NOT NULL,
    duration_ticks INT         NOT NULL CHECK (duration_ticks > 0),
    format_version INT         NOT NULL CHECK (format_version > 0),
    size_bytes     INT         NOT NULL CHECK (size_bytes > 0),
    sha256         CHAR(64)    NOT NULL,
    data           BYTEA       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
