WITH ranked AS (
    SELECT
        player_uuid,
        ranked_season_id,
        PERCENT_RANK() OVER (PARTITION BY ranked_season_id ORDER BY elo) * 100 AS percentile
    FROM player_season_elo
)
UPDATE player_season_elo pse
SET rank_title = (
    SELECT rt.name
    FROM rank_title rt
    WHERE rt.min_percentile <= r.percentile
    ORDER BY rt.min_percentile DESC
    LIMIT 1
)
FROM ranked r
WHERE pse.player_uuid = r.player_uuid AND pse.ranked_season_id = r.ranked_season_id;
