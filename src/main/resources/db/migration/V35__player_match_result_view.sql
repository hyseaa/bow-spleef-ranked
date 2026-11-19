-- Spec 035: what a match gives each of its players, in one place for every stats query.
-- result: the match itself (the best score wins it, a shared best score is a draw).
-- games_*: a match of exactly two teams with a + b >= 1 is a + b games (the player's team won a, lost b, as the
-- rating counts it since spec 034, ADR-0034); any other match (a 0-0, three teams or more) is one game, as its result.
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
JOIN team_player tp ON tp.team_id = s.team_id;
