package com.playerscores.mapper;

import com.playerscores.model.Match;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MatchMapper {

    @Insert("INSERT INTO match (game_type, source, played_at) VALUES (#{gameType}, #{source}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Match match);

    @Update("UPDATE match SET ranked_season_id = #{rankedSeasonId} WHERE id = #{matchId}")
    void updateRankedSeasonId(@Param("matchId") Long matchId, @Param("rankedSeasonId") Long rankedSeasonId);

    @Select("SELECT id, game_type, source, played_at, ranked_season_id FROM match WHERE id = #{id}")
    Optional<Match> findById(Long id);

    @Select("SELECT id, game_type, source, played_at, ranked_season_id FROM match WHERE game_type = #{gameType} ORDER BY played_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Match> findByGameType(@Param("gameType") String gameType, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM match WHERE game_type = #{gameType}")
    long countByGameType(@Param("gameType") String gameType);
}
