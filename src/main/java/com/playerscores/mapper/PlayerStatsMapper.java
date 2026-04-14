package com.playerscores.mapper;

import com.playerscores.dto.PlayerCasualStatsRow;
import com.playerscores.dto.PlayerRankedStatsResponse;
import com.playerscores.dto.PlayerSeasonEloRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PlayerStatsMapper {

    @Select("SELECT COUNT(DISTINCT m.id) AS matches_played, "
            + "COUNT(CASE WHEN t.score = (SELECT MAX(t2.score) FROM team t2 WHERE t2.match_id = m.id) THEN 1 END) AS wins "
            + "FROM match m "
            + "JOIN game_types g ON g.name = m.game_type AND g.ranked = FALSE "
            + "JOIN team t ON t.match_id = m.id "
            + "JOIN team_player tp ON tp.team_id = t.id "
            + "WHERE tp.player_uuid = #{playerUuid}")
    PlayerCasualStatsRow findCasualStats(UUID playerUuid);

    @Select("SELECT rs.id AS season_id, rs.name AS season_name, rs.game_type, "
            + "pse.elo, pse.matches_played, "
            + "(SELECT COUNT(*) FROM match m "
            + " JOIN team t ON t.match_id = m.id "
            + " JOIN team_player tp ON tp.team_id = t.id "
            + " WHERE tp.player_uuid = #{playerUuid} "
            + " AND m.ranked_season_id = rs.id "
            + " AND t.score = (SELECT MAX(t2.score) FROM team t2 WHERE t2.match_id = m.id)) AS wins, "
            + "(SELECT name FROM rank_title WHERE min_elo <= pse.elo ORDER BY min_elo DESC LIMIT 1) AS title "
            + "FROM player_season_elo pse "
            + "JOIN ranked_season rs ON rs.id = pse.ranked_season_id AND rs.active = TRUE "
            + "WHERE pse.player_uuid = #{playerUuid}")
    List<PlayerRankedStatsResponse> findActiveRankedStats(UUID playerUuid);

    @Select("SELECT pse.elo, pse.matches_played, "
            + "rs.id AS season_id, rs.name AS season_name, rs.game_type, "
            + "(SELECT name FROM rank_title WHERE min_elo <= pse.elo ORDER BY min_elo DESC LIMIT 1) AS title "
            + "FROM player_season_elo pse "
            + "JOIN ranked_season rs ON rs.id = pse.ranked_season_id "
            + "WHERE pse.player_uuid = #{playerUuid} AND pse.ranked_season_id = #{seasonId}")
    Optional<PlayerSeasonEloRow> findSeasonElo(@Param("playerUuid") UUID playerUuid, @Param("seasonId") Long seasonId);
}
