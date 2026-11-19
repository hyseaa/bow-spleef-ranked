-- Spec 038 (ADR-0036): a private game of a custom server's party is recorded and marked private; it is left out of
-- the players' stats and the wins leaderboard, which read the view player_match_result (V35, spec 035).
ALTER TABLE match ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE;

DROP VIEW player_match_result;

-- The view of V35, without the private matches.
CREATE VIEW player_match_result AS
SELECT m.id AS match_id,
       tp.player_uuid,
       m.game_type,
       m.ranked_season_id,
       s.team_count,
       CASE WHEN s.score < s.best_score THEN 'LOST'
            WHEN s.best_count = 1 THEN 'WON'
            ELSE 'DRAWN' END AS result,
       CASE WHEN s.team_count = 2 AND s.total_score >= 1 THEN s.score
            WHEN s.score = s.best_score AND s.best_count = 1 THEN 1
            ELSE 0 END AS games_won,
       CASE WHEN s.team_count = 2 AND s.total_score >= 1 THEN s.total_score - s.score
            WHEN s.score < s.best_score THEN 1
            ELSE 0 END AS games_lost,
       CASE WHEN s.team_count = 2 AND s.total_score >= 1 THEN 0
            WHEN s.score = s.best_score AND s.best_count > 1 THEN 1
            ELSE 0 END AS games_drawn
FROM (
    SELECT b.*,
           SUM(CASE WHEN b.score = b.best_score THEN 1 ELSE 0 END) OVER (PARTITION BY b.match_id) AS best_count
    FROM (
        SELECT t.id AS team_id, t.match_id, t.score,
               COUNT(*) OVER (PARTITION BY t.match_id) AS team_count,
               SUM(t.score) OVER (PARTITION BY t.match_id) AS total_score,
               MAX(t.score) OVER (PARTITION BY t.match_id) AS best_score
        FROM team t
    ) b
) s
JOIN match m ON m.id = s.match_id
JOIN team_player tp ON tp.team_id = s.team_id
WHERE NOT m.is_private;
