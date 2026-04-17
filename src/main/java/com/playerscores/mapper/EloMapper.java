package com.playerscores.mapper;

import com.playerscores.dto.LeaderboardRow;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.model.EloHistory;
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

    @Insert("INSERT INTO player_season_elo (player_uuid, ranked_season_id) "
            + "VALUES (#{playerUuid}, #{rankedSeasonId}) "
            + "ON CONFLICT (player_uuid, ranked_season_id) DO NOTHING")
    void upsertPlayerSeasonElo(@Param("playerUuid") UUID playerUuid, @Param("rankedSeasonId") Long rankedSeasonId);

    @Update("UPDATE player_season_elo SET elo = #{elo}, matches_played = matches_played + 1 "
            + "WHERE player_uuid = #{playerUuid} AND ranked_season_id = #{rankedSeasonId}")
    void updateElo(@Param("playerUuid") UUID playerUuid, @Param("rankedSeasonId") Long rankedSeasonId, @Param("elo") int elo);

    @Select("<script>"
            + "SELECT player_uuid, elo, matches_played FROM player_season_elo "
            + "WHERE ranked_season_id = #{rankedSeasonId} "
            + "AND player_uuid IN "
            + "<foreach item='uuid' collection='uuids' open='(' separator=',' close=')'>#{uuid}</foreach>"
            + "</script>")
    List<PlayerEloSnapshot> findEloByUuidsAndSeason(@Param("uuids") List<UUID> uuids, @Param("rankedSeasonId") Long rankedSeasonId);

    @Insert("INSERT INTO elo_history (player_uuid, match_id, ranked_season_id, elo_before, elo_after, elo_change) "
            + "VALUES (#{playerUuid}, #{matchId}, #{rankedSeasonId}, #{eloBefore}, #{eloAfter}, #{eloChange})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertHistory(EloHistory history);

    @Select("SELECT match_id, elo_before, elo_after, elo_change, recorded_at "
            + "FROM elo_history "
            + "WHERE player_uuid = #{uuid} AND ranked_season_id = #{rankedSeasonId} "
            + "ORDER BY recorded_at DESC "
            + "LIMIT #{size} OFFSET #{offset}")
    List<EloHistory> findHistoryByPlayerAndSeason(
            @Param("uuid") UUID uuid,
            @Param("rankedSeasonId") Long rankedSeasonId,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM elo_history WHERE player_uuid = #{uuid} AND ranked_season_id = #{rankedSeasonId}")
    long countHistoryByPlayerAndSeason(@Param("uuid") UUID uuid, @Param("rankedSeasonId") Long rankedSeasonId);

    @Select("SELECT p.uuid, pse.elo, pse.matches_played, "
            + "COUNT(CASE WHEN m.id IS NOT NULL AND t.score = (SELECT MAX(t2.score) FROM team t2 WHERE t2.match_id = t.match_id) THEN 1 END) AS wins, "
            + "(SELECT name FROM rank_title WHERE min_elo <= pse.elo ORDER BY min_elo DESC LIMIT 1) AS title "
            + "FROM player_season_elo pse "
            + "JOIN player p ON p.uuid = pse.player_uuid "
            + "LEFT JOIN team_player tp ON tp.player_uuid = p.uuid "
            + "LEFT JOIN team t ON t.id = tp.team_id "
            + "LEFT JOIN match m ON m.id = t.match_id AND m.ranked_season_id = #{rankedSeasonId} "
            + "WHERE pse.ranked_season_id = #{rankedSeasonId} "
            + "GROUP BY p.uuid, pse.elo, pse.matches_played "
            + "ORDER BY pse.elo DESC, p.uuid ASC "
            + "LIMIT #{size} OFFSET #{offset}")
    List<LeaderboardRow> findLeaderboard(
            @Param("rankedSeasonId") Long rankedSeasonId,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM player_season_elo WHERE ranked_season_id = #{rankedSeasonId}")
    long countLeaderboard(@Param("rankedSeasonId") Long rankedSeasonId);

    @Delete("DELETE FROM elo_history WHERE ranked_season_id = #{seasonId}")
    void deleteHistoryBySeasonId(@Param("seasonId") Long seasonId);

    @Update("UPDATE player_season_elo SET elo = #{startingElo}, matches_played = 0 WHERE ranked_season_id = #{seasonId}")
    void resetPlayerSeasonElos(@Param("seasonId") Long seasonId, @Param("startingElo") int startingElo);

    @Delete("DELETE FROM player_season_elo WHERE ranked_season_id = #{seasonId} AND matches_played = 0")
    void deletePlayersWithNoMatches(@Param("seasonId") Long seasonId);
}
