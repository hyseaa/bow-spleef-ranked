ALTER TABLE game_types
    ADD COLUMN team_size INT NOT NULL DEFAULT 1 CHECK (team_size >= 1);
