package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.dto.PlayerGameTypeStatsRow;
import net.bowspleef.ranked.dto.PlayerRankedStatsResponse;
import net.bowspleef.ranked.dto.PlayerSeasonEloRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PlayerStatsMapper {

    /** The view of spec 035 summed over a player's matches: their results, then their games (V35). */
    String SEASON_RESULTS = "LEFT JOIN LATERAL (SELECT "
            + "COUNT(*) FILTER (WHERE r.result = 'WON') AS wins, "
            + "COUNT(*) FILTER (WHERE r.result = 'LOST') AS losses, "
            + "COUNT(*) FILTER (WHERE r.result = 'DRAWN') AS draws, "
            + "COALESCE(SUM(r.games_won), 0) AS games_won, "
            + "COALESCE(SUM(r.games_lost), 0) AS games_lost, "
            + "COALESCE(SUM(r.games_drawn), 0) AS games_drawn "
            + "FROM player_match_result r "
            + "WHERE r.player_uuid = pse.player_uuid AND r.ranked_season_id = rs.id) r ON TRUE ";

    @Select("SELECT g.name AS game_type, g.display_name AS game_type_display_name, "
            + "COUNT(*) AS matches_played, "
            + "COUNT(*) FILTER (WHERE r.result = 'WON') AS wins, "
            // a draw: the player's team shares the best score
            + "COUNT(*) FILTER (WHERE r.result = 'DRAWN') AS draws, "
            + "SUM(r.games_won) AS games_won, SUM(r.games_lost) AS games_lost, SUM(r.games_drawn) AS games_drawn "
            + "FROM player_match_result r "
            + "JOIN game_types g ON g.name = r.game_type AND g.ranked = FALSE "
            + "WHERE r.player_uuid = #{playerUuid} "
            + "GROUP BY g.name, g.display_name "
            + "ORDER BY matches_played DESC, g.name")
    List<PlayerGameTypeStatsRow> findNonRankedStats(UUID playerUuid);

    @Select("SELECT rs.id AS season_id, rs.name AS season_name, rs.game_type, "
            + "pse.elo, pse.matches_played, r.wins, r.losses, r.draws, "
            + "r.games_won + r.games_lost + r.games_drawn AS games_played, "
            + "r.games_won, r.games_lost, r.games_drawn, "
            + "pse.rank_title AS title "
            + "FROM player_season_elo pse "
            + "JOIN ranked_season rs ON rs.id = pse.ranked_season_id AND rs.active = TRUE "
            + SEASON_RESULTS
            + "WHERE pse.player_uuid = #{playerUuid}")
    List<PlayerRankedStatsResponse> findActiveRankedStats(UUID playerUuid);

    @Select("SELECT pse.elo, pse.matches_played, "
            + "r.games_won + r.games_lost + r.games_drawn AS games_played, r.games_won, r.games_lost, "
            + "rs.id AS season_id, rs.name AS season_name, rs.game_type, "
            + "pse.rank_title AS title "
            + "FROM player_season_elo pse "
            + "JOIN ranked_season rs ON rs.id = pse.ranked_season_id "
            + SEASON_RESULTS
            + "WHERE pse.player_uuid = #{playerUuid} AND pse.ranked_season_id = #{seasonId}")
    Optional<PlayerSeasonEloRow> findSeasonElo(@Param("playerUuid") UUID playerUuid, @Param("seasonId") Long seasonId);
}
