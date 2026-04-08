CREATE TABLE match (
    id        BIGSERIAL   PRIMARY KEY,
    game_type VARCHAR(50) NOT NULL,
    played_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE team (
    id       BIGSERIAL   PRIMARY KEY,
    match_id BIGINT      NOT NULL REFERENCES match(id) ON DELETE CASCADE,
    color    VARCHAR(20),
    score    INT         NOT NULL DEFAULT 0
);

CREATE TABLE team_player (
    id          BIGSERIAL PRIMARY KEY,
    team_id     BIGINT    NOT NULL REFERENCES team(id) ON DELETE CASCADE,
    player_uuid UUID      NOT NULL REFERENCES player(uuid),
    CONSTRAINT uq_team_player UNIQUE (team_id, player_uuid)
);

CREATE TABLE match_player_stat (
    team_player_id BIGINT PRIMARY KEY REFERENCES team_player(id) ON DELETE CASCADE,
    stats          JSONB  NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_team_match_id           ON team(match_id);
CREATE INDEX idx_team_player_team_id     ON team_player(team_id);
CREATE INDEX idx_team_player_player_uuid ON team_player(player_uuid);
