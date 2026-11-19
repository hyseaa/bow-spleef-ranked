package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.dto.LeaderboardRow;
import net.bowspleef.ranked.dto.MatchHistoryRow;
import net.bowspleef.ranked.dto.OpponentRow;
import net.bowspleef.ranked.dto.PlayerEloSnapshot;
import net.bowspleef.ranked.model.EloHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.UUID;

@Mapper
public interface EloMapper {

    @Insert("INSERT INTO player_season_elo (player_uuid, ranked_season_id, elo, mu, sigma) "
            + "VALUES (#{playerUuid}, #{rankedSeasonId}, #{elo}, #{mu}, #{sigma}) "
            + "ON CONFLICT (player_uuid, ranked_season_id) DO NOTHING")
    void upsertPlayerSeasonElo(@Param("playerUuid") UUID playerUuid, @Param("rankedSeasonId") Long rankedSeasonId,
                               @Param("elo") int elo, @Param("mu") double mu, @Param("sigma") double sigma);

    @Update("UPDATE player_season_elo SET elo = #{elo}, mu = #{mu}, sigma = #{sigma}, matches_played = matches_played + 1 "
            + "WHERE player_uuid = #{playerUuid} AND ranked_season_id = #{rankedSeasonId}")
    void updateElo(@Param("playerUuid") UUID playerUuid, @Param("rankedSeasonId") Long rankedSeasonId,
                   @Param("elo") int elo, @Param("mu") double mu, @Param("sigma") double sigma);

    @Select("<script>"
            + "SELECT player_uuid, elo, mu, sigma, matches_played, rank_title FROM player_season_elo "
            + "WHERE ranked_season_id = #{rankedSeasonId} "
            + "AND player_uuid IN "
            + "<foreach item='uuid' collection='uuids' open='(' separator=',' close=')'>#{uuid}</foreach>"
            + "</script>")
    List<PlayerEloSnapshot> findEloByUuidsAndSeason(@Param("uuids") List<UUID> uuids, @Param("rankedSeasonId") Long rankedSeasonId);

    @Insert("INSERT INTO elo_history (player_uuid, match_id, ranked_season_id, elo_before, elo_after, elo_change) "
            + "VALUES (#{playerUuid}, #{matchId}, #{rankedSeasonId}, #{eloBefore}, #{eloAfter}, #{eloChange})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertHistory(EloHistory history);

    @Select("SELECT eh.match_id, m.played_at, eh.elo_change, eh.elo_after, eh.recorded_at "
            + "FROM elo_history eh "
            + "JOIN match m ON m.id = eh.match_id "
            + "WHERE eh.player_uuid = #{uuid} AND eh.ranked_season_id = #{rankedSeasonId} "
            + "ORDER BY m.played_at DESC "
            + "LIMIT #{size} OFFSET #{offset}")
    List<MatchHistoryRow> findMatchHistoryByPlayerAndSeason(
            @Param("uuid") UUID uuid,
            @Param("rankedSeasonId") Long rankedSeasonId,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("<script>"
            + "SELECT t.match_id, tp.player_uuid AS uuid "
            + "FROM team t "
            + "JOIN team_player tp ON tp.team_id = t.id "
            + "WHERE t.match_id IN "
            + "<foreach item='id' collection='matchIds' open='(' separator=',' close=')'>#{id}</foreach>"
            + " AND t.id NOT IN ("
            + "  SELECT tp2.team_id FROM team_player tp2 "
            + "  JOIN team t2 ON t2.id = tp2.team_id "
            + "  WHERE tp2.player_uuid = #{playerUuid} AND t2.match_id IN "
            + "  <foreach item='id' collection='matchIds' open='(' separator=',' close=')'>#{id}</foreach>"
            + ")"
            + "</script>")
    List<OpponentRow> findOpponentsByMatchIds(
            @Param("matchIds") List<Long> matchIds,
            @Param("playerUuid") UUID playerUuid);

    @Select("SELECT COUNT(*) FROM elo_history WHERE player_uuid = #{uuid} AND ranked_season_id = #{rankedSeasonId}")
    long countHistoryByPlayerAndSeason(@Param("uuid") UUID uuid, @Param("rankedSeasonId") Long rankedSeasonId);

    /** Matches won, games played and games won in the season, from the view of spec 035 (V35). */
    @Select("SELECT pse.player_uuid AS uuid, pse.elo, pse.matches_played, "
            + "COUNT(r.match_id) FILTER (WHERE r.result = 'WON') AS wins, "
            + "COALESCE(SUM(r.games_won + r.games_lost + r.games_drawn), 0) AS games_played, "
            + "COALESCE(SUM(r.games_won), 0) AS games_won, "
            + "pse.rank_title AS title "
            + "FROM player_season_elo pse "
            + "LEFT JOIN player_match_result r ON r.player_uuid = pse.player_uuid "
            + "AND r.ranked_season_id = pse.ranked_season_id "
            + "WHERE pse.ranked_season_id = #{rankedSeasonId} "
            + "GROUP BY pse.player_uuid, pse.elo, pse.matches_played, pse.rank_title "
            + "ORDER BY pse.elo DESC, pse.player_uuid ASC "
            + "LIMIT #{size} OFFSET #{offset}")
    List<LeaderboardRow> findLeaderboard(
            @Param("rankedSeasonId") Long rankedSeasonId,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM player_season_elo WHERE ranked_season_id = #{rankedSeasonId}")
    long countLeaderboard(@Param("rankedSeasonId") Long rankedSeasonId);

    @Delete("DELETE FROM elo_history WHERE ranked_season_id = #{seasonId}")
    void deleteHistoryBySeasonId(@Param("seasonId") Long seasonId);

    @Update("UPDATE player_season_elo SET elo = #{startingElo}, mu = #{mu}, sigma = #{sigma}, matches_played = 0 "
            + "WHERE ranked_season_id = #{seasonId}")
    void resetPlayerSeasonElos(@Param("seasonId") Long seasonId, @Param("startingElo") int startingElo,
                               @Param("mu") double mu, @Param("sigma") double sigma);

    @Select("SELECT player_uuid, elo, mu, sigma, matches_played, rank_title FROM player_season_elo WHERE ranked_season_id = #{seasonId}")
    List<PlayerEloSnapshot> findAllEloBySeasonId(@Param("seasonId") Long seasonId);

    @Update("WITH ranked AS ("
            + "SELECT player_uuid, PERCENT_RANK() OVER (ORDER BY elo) * 100 AS percentile "
            + "FROM player_season_elo WHERE ranked_season_id = #{seasonId}"
            + ") "
            + "UPDATE player_season_elo pse "
            + "SET rank_title = (SELECT rt.name FROM rank_title rt "
            + "  WHERE rt.min_percentile <= r.percentile "
            + "  ORDER BY rt.min_percentile DESC LIMIT 1) "
            + "FROM ranked r "
            + "WHERE pse.player_uuid = r.player_uuid AND pse.ranked_season_id = #{seasonId}")
    void updateRankTitlesBySeason(@Param("seasonId") Long seasonId);

    @Select("SELECT player_uuid FROM player_season_elo WHERE ranked_season_id = #{seasonId} AND matches_played = 0")
    List<UUID> findUuidsWithNoMatches(@Param("seasonId") Long seasonId);

    @Delete("DELETE FROM player_season_elo WHERE ranked_season_id = #{seasonId} AND matches_played = 0")
    void deletePlayersWithNoMatches(@Param("seasonId") Long seasonId);
}
