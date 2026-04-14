CREATE TABLE queue (
    player_uuid      UUID        NOT NULL REFERENCES player(uuid),
    game_type        VARCHAR(50) NOT NULL REFERENCES game_types(name),
    ranked_season_id BIGINT      NOT NULL REFERENCES ranked_season(id),
    elo              INT         NOT NULL,
    queued_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (player_uuid, game_type)
);

CREATE INDEX idx_queue_game_type_elo ON queue(game_type, elo);
