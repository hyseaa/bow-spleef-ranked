ALTER TABLE match
    ADD CONSTRAINT fk_match_game_type
    FOREIGN KEY (game_type) REFERENCES game_types(name);
