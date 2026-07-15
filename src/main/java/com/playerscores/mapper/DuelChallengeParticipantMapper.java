package com.playerscores.mapper;

import com.playerscores.model.DuelChallengeParticipant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DuelChallengeParticipantMapper {

    @Insert("INSERT INTO duel_challenge_participant (challenge_id, player_uuid, side) " +
            "VALUES (#{challengeId}, #{playerUuid}, #{side})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DuelChallengeParticipant participant);

    @Select("SELECT id, challenge_id, player_uuid, side FROM duel_challenge_participant " +
            "WHERE challenge_id = #{challengeId} ORDER BY id ASC")
    List<DuelChallengeParticipant> findByChallengeId(@Param("challengeId") Long challengeId);
}
