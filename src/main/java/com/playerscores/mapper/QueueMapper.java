package com.playerscores.mapper;

import com.playerscores.model.QueueEntry;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface QueueMapper {

    @Insert("INSERT INTO queue (player_uuid, game_type, ranked_season_id, elo, queued_at) " +
            "VALUES (#{playerUuid}, #{gameType}, #{rankedSeasonId}, #{elo}, NOW()) " +
            "ON CONFLICT (player_uuid, game_type) DO UPDATE SET elo = #{elo}, queued_at = NOW()")
    void upsert(QueueEntry entry);

    @Select("SELECT player_uuid, game_type, ranked_season_id, elo, queued_at " +
            "FROM queue " +
            "WHERE game_type = #{gameType} " +
            "  AND ranked_season_id = #{seasonId} " +
            "  AND player_uuid != #{playerUuid} " +
            "  AND elo BETWEEN #{minElo} AND #{maxElo} " +
            // two players who already have an open duel of this game type are not paired: the pairing removes both
            // from the queue and the duel could not be created (one open duel per pair and game type)
            "  AND NOT EXISTS (SELECT 1 FROM duel_challenge d WHERE d.game_type = #{gameType} " +
            "    AND d.status IN ('PENDING', 'ACTIVE', 'REPORTED', 'CONFIRMING') " +
            "    AND NOT (d.status = 'PENDING' AND d.expires_at < NOW()) " +
            "    AND ((d.challenger_uuid = queue.player_uuid AND d.challenged_uuid = #{playerUuid}) " +
            "      OR (d.challenger_uuid = #{playerUuid} AND d.challenged_uuid = queue.player_uuid))) " +
            "ORDER BY ABS(elo - #{elo}) ASC, queued_at ASC " +
            "LIMIT 1 " +
            "FOR UPDATE SKIP LOCKED")
    Optional<QueueEntry> findMatch(
            @Param("playerUuid") UUID playerUuid,
            @Param("gameType") String gameType,
            @Param("seasonId") Long seasonId,
            @Param("elo") int elo,
            @Param("minElo") int minElo,
            @Param("maxElo") int maxElo);

    @Select("SELECT player_uuid, game_type, ranked_season_id, elo, queued_at " +
            "FROM queue " +
            "ORDER BY game_type ASC, queued_at ASC")
    List<QueueEntry> findAll();

    @Delete("DELETE FROM queue WHERE player_uuid = #{playerUuid} AND game_type = #{gameType}")
    void delete(@Param("playerUuid") UUID playerUuid, @Param("gameType") String gameType);

    @Delete("DELETE FROM queue WHERE queued_at < NOW() - make_interval(mins => #{minutes})")
    int deleteExpired(@Param("minutes") int minutes);
}
