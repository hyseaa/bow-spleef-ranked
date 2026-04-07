package com.playerscores.mapper;

import com.playerscores.dto.LeaderboardEntryDto;
import com.playerscores.model.Score;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface ScoreMapper {

    @Insert("INSERT INTO scores (player_id, value, game, created_at) VALUES (#{playerId}, #{value}, #{game}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Score score);

    @Select("SELECT id, player_id, value, game, created_at FROM scores WHERE player_id = #{playerId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Score> findByPlayerId(@Param("playerId") Long playerId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM scores WHERE player_id = #{playerId}")
    long countByPlayerId(Long playerId);

    @Select("SELECT id, player_id, value, game, created_at FROM scores WHERE player_id = #{playerId} AND game = #{game} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Score> findByPlayerIdAndGame(@Param("playerId") Long playerId, @Param("game") String game, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM scores WHERE player_id = #{playerId} AND game = #{game}")
    long countByPlayerIdAndGame(@Param("playerId") Long playerId, @Param("game") String game);

    @SelectProvider(type = ScoreSqlProvider.class, method = "findLeaderboard")
    List<LeaderboardEntryDto> findLeaderboard(@Param("game") String game, @Param("limit") int limit);
}
