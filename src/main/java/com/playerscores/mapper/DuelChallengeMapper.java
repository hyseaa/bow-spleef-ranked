package com.playerscores.mapper;

import com.playerscores.model.DuelChallenge;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface DuelChallengeMapper {

    @Insert("INSERT INTO duel_challenge (challenger_uuid, challenged_uuid, game_type, expires_at) " +
            "VALUES (#{challengerUuid}, #{challengedUuid}, #{gameType}, #{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DuelChallenge challenge);

    @Select("SELECT id, challenger_uuid, challenged_uuid, game_type, status, " +
            "reporter_uuid, score_challenger, score_challenged, match_id, created_at, expires_at " +
            "FROM duel_challenge WHERE id = #{id}")
    Optional<DuelChallenge> findById(@Param("id") Long id);

    @Update("UPDATE duel_challenge SET status = #{newStatus} " +
            "WHERE id = #{id} AND status = #{expectedStatus}")
    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("newStatus") String newStatus);

    @Update("UPDATE duel_challenge SET status = 'CANCELLED' " +
            "WHERE id = #{id} AND status IN ('PENDING', 'ACTIVE', 'REPORTED')")
    int cancel(@Param("id") Long id);

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
            "reporter_uuid = NULL, score_challenger = NULL, score_challenged = NULL " +
            "WHERE id = #{id} AND status = 'REPORTED'")
    int clearReportedScores(@Param("id") Long id);

    @Update("UPDATE duel_challenge SET match_id = #{matchId}, status = 'COMPLETED' " +
            "WHERE id = #{id}")
    void setMatchId(@Param("id") Long id, @Param("matchId") Long matchId);

    @Delete("DELETE FROM duel_challenge WHERE status = 'PENDING' AND expires_at < NOW()")
    int deleteExpired();
}
