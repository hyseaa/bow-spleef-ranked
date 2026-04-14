CREATE TABLE elo_history (
    id               BIGSERIAL   PRIMARY KEY,
    player_uuid      UUID        NOT NULL REFERENCES player(uuid),
    match_id         BIGINT      NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    ranked_season_id BIGINT      NOT NULL REFERENCES ranked_season(id),
    elo_before       INT         NOT NULL,
    elo_after        INT         NOT NULL,
    elo_change       INT         NOT NULL,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_elo_history_player ON elo_history(player_uuid, ranked_season_id, recorded_at DESC);
CREATE INDEX idx_elo_history_match  ON elo_history(match_id);
