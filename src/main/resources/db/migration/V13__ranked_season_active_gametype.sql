ALTER TABLE ranked_season
    ADD COLUMN active     BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN game_type  VARCHAR(50)  NOT NULL REFERENCES game_types(name);

CREATE UNIQUE INDEX unique_active_season_per_game_type
    ON ranked_season (game_type)
    WHERE active = TRUE;
