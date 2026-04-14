ALTER TABLE match
    ADD COLUMN ranked_season_id BIGINT REFERENCES ranked_season(id);
-- NULL = unranked, NOT NULL = ranked
