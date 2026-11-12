package com.playerscores.mapper;

import com.playerscores.model.DuelChallenge;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface DuelChallengeMapper {

    @Insert("INSERT INTO duel_challenge (challenger_uuid, challenged_uuid, game_type, expires_at) " +
            "VALUES (#{challengerUuid}, #{challengedUuid}, #{gameType}, #{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id,status,createdAt", keyColumn = "id,status,created_at")
    void insert(DuelChallenge challenge);

    @Select("SELECT id, challenger_uuid, challenged_uuid, game_type, status, " +
            "reporter_uuid, score_challenger, score_challenged, match_id, created_at, expires_at, " +
            "cancel_requested_by, active_since " +
            "FROM duel_challenge WHERE id = #{id}")
    Optional<DuelChallenge> findById(@Param("id") Long id);

    // active_since: the moment the duel last became ACTIVE, which the expiry of stale duels counts from (spec 006)
    // KI-09: at most one open duel between the same two players for a game type. A PENDING challenge past its
    // expiry is deleted by the scheduler within a minute, so it is not counted.
    @Select("SELECT EXISTS (SELECT 1 FROM duel_challenge WHERE game_type = #{gameType} " +
            "AND status IN ('PENDING', 'ACTIVE', 'REPORTED', 'CONFIRMING') " +
            "AND NOT (status = 'PENDING' AND expires_at < NOW()) " +
            "AND ((challenger_uuid = #{a} AND challenged_uuid = #{b}) OR (challenger_uuid = #{b} AND challenged_uuid = #{a})))")
    boolean existsOpenBetween(@Param("a") UUID a, @Param("b") UUID b, @Param("gameType") String gameType);

    /** Transaction-scoped lock so that two simultaneous creations for the same pair are checked one after the other. */
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{key}, 0))) AS locked")
    int lockPair(@Param("key") String key);

    @Update("UPDATE duel_challenge SET status = #{newStatus}, " +
            "active_since = CASE WHEN CAST(#{newStatus} AS VARCHAR) = 'ACTIVE' THEN NOW() ELSE active_since END " +
            "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("newStatus") String newStatus);

    @Update("UPDATE duel_challenge SET status = 'CANCELLED' " +
            "WHERE id = #{id} AND status = 'PENDING'")
    int cancel(@Param("id") Long id);

    // Cancellation by mutual agreement (spec 003): each update is a compare-and-swap on the duel row.
    @Update("UPDATE duel_challenge SET cancel_requested_by = #{requesterUuid} " +
            "WHERE id = #{id} AND status IN ('ACTIVE', 'REPORTED') AND cancel_requested_by IS NULL")
    int requestCancel(@Param("id") Long id, @Param("requesterUuid") UUID requesterUuid);

    @Update("UPDATE duel_challenge SET status = 'CANCELLED' " +
            "WHERE id = #{id} AND status IN ('ACTIVE', 'REPORTED') " +
            "AND cancel_requested_by IS NOT NULL AND cancel_requested_by <> #{accepterUuid}")
    int acceptCancel(@Param("id") Long id, @Param("accepterUuid") UUID accepterUuid);

    @Update("UPDATE duel_challenge SET cancel_requested_by = NULL " +
            "WHERE id = #{id} AND status IN ('ACTIVE', 'REPORTED') " +
            "AND cancel_requested_by IS NOT NULL AND cancel_requested_by <> #{declinerUuid}")
    int declineCancel(@Param("id") Long id, @Param("declinerUuid") UUID declinerUuid);

    @Update("UPDATE duel_challenge SET status = 'REPORTED', " +
            "reporter_uuid = #{reporterUuid}, " +
            "score_challenger = #{scoreChallenger}, " +
            "score_challenged = #{scoreChallenged} " +
            "WHERE id = #{id} AND status = 'ACTIVE'")
    int setReportedScores(@Param("id") Long id,
                          @Param("reporterUuid") UUID reporterUuid,
                          @Param("scoreChallenger") int scoreChallenger,
                          @Param("scoreChallenged") int scoreChallenged);

    @Update("UPDATE duel_challenge SET status = 'ACTIVE', " +
            "reporter_uuid = NULL, score_challenger = NULL, score_challenged = NULL, active_since = NOW() " +
            "WHERE id = #{id} AND status = 'REPORTED'")
    int clearReportedScores(@Param("id") Long id);

    @Update("UPDATE duel_challenge SET match_id = #{matchId}, status = 'COMPLETED' " +
            "WHERE id = #{id} AND status = 'CONFIRMING'")
    int setMatchId(@Param("id") Long id, @Param("matchId") Long matchId);

    /** The duel is cancelled whatever the other player thinks, because a participant left (spec 006). */
    @Update("UPDATE duel_challenge SET status = 'CANCELLED' WHERE id = #{id} AND status IN ('ACTIVE', 'REPORTED')")
    int cancelAbandoned(@Param("id") Long id);

    /**
     * Cancels the ACTIVE duels that became ACTIVE more than {@code hours} hours ago and returns their ids (spec 006).
     * Guarded by status = 'ACTIVE', like a score report, so that exactly one of the two wins a race.
     */
    @Select("UPDATE duel_challenge SET status = 'CANCELLED' " +
            "WHERE status = 'ACTIVE' AND active_since < NOW() - make_interval(hours => #{hours}) " +
            "RETURNING id")
    List<Long> cancelStaleActive(@Param("hours") int hours);

    @Delete("DELETE FROM duel_challenge WHERE status = 'PENDING' AND expires_at < NOW()")
    int deleteExpired();
}
