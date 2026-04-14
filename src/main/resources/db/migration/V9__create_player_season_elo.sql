CREATE TABLE player_season_elo (
    player_uuid      UUID   NOT NULL REFERENCES player(uuid),
    ranked_season_id BIGINT NOT NULL REFERENCES ranked_season(id),
    elo              INT    NOT NULL DEFAULT 1000,
    matches_played   INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, ranked_season_id)
);

CREATE INDEX idx_player_season_elo_season ON player_season_elo(ranked_season_id, elo DESC);
