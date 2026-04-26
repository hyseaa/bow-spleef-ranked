CREATE TABLE duel_challenge (
    id                 BIGSERIAL    PRIMARY KEY,
    challenger_uuid    UUID         NOT NULL REFERENCES player(uuid),
    challenged_uuid    UUID         NOT NULL REFERENCES player(uuid),
    game_type          VARCHAR(50)  NOT NULL REFERENCES game_types(name),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reporter_uuid      UUID         REFERENCES player(uuid),
    score_challenger   INT,
    score_challenged   INT,
    match_id           BIGINT       REFERENCES match(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_duel_challenge_active
    ON duel_challenge(status)
    WHERE status IN ('PENDING', 'ACTIVE', 'REPORTED');
