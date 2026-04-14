CREATE TABLE ranked_season (
    id        BIGSERIAL   PRIMARY KEY,
    name      TEXT        NOT NULL,
    starts_at TIMESTAMPTZ,
    ends_at   TIMESTAMPTZ
);
