-- Spec 001 / ADR-0013: each season keeps the rating algorithm it was played with.
ALTER TABLE ranked_season
    ADD COLUMN rating_algorithm VARCHAR(20) NOT NULL DEFAULT 'OPENSKILL'
        CHECK (rating_algorithm IN ('CLASSIC_ELO', 'OPENSKILL'));

-- Existing seasons: classic Elo never wrote mu/sigma (they kept V22's defaults),
-- OpenSkill changes them on every rated match.
UPDATE ranked_season rs
SET rating_algorithm = 'CLASSIC_ELO'
WHERE EXISTS (SELECT 1 FROM player_season_elo p WHERE p.ranked_season_id = rs.id)
  AND NOT EXISTS (
        SELECT 1 FROM player_season_elo p
        WHERE p.ranked_season_id = rs.id
          AND (ABS(p.mu - 25.0) > 1e-9 OR ABS(p.sigma - 25.0 / 3) > 1e-9));
