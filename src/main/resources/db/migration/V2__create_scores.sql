CREATE TABLE scores (
    id         BIGSERIAL PRIMARY KEY,
    player_id  BIGINT NOT NULL REFERENCES players(id),
    value      INTEGER NOT NULL,
    game       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scores_player_id ON scores(player_id);
CREATE INDEX idx_scores_game ON scores(game);
