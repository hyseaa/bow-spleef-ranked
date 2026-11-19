-- Spec 034 / ADR-0034: a ranked match rated by its games (OpenSkill by games).
ALTER TABLE ranked_season DROP CONSTRAINT ranked_season_rating_algorithm_check;
ALTER TABLE ranked_season
    ADD CONSTRAINT ranked_season_rating_algorithm_check
        CHECK (rating_algorithm IN ('CLASSIC_ELO', 'OPENSKILL', 'OPENSKILL_GAMES'));
ALTER TABLE ranked_season ALTER COLUMN rating_algorithm SET DEFAULT 'OPENSKILL_GAMES';

-- The seasons active at the deployment switch to it and are recomputed by the scheduler (ADR-0009, user 2026-11-19);
-- finished seasons keep their algorithm (ADR-0013).
UPDATE ranked_season
SET rating_algorithm = 'OPENSKILL_GAMES', elo_dirty = TRUE
WHERE active AND rating_algorithm = 'OPENSKILL';
