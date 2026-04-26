package com.playerscores.mapper;

import com.playerscores.model.MatchPlayerStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface MatchPlayerStatMapper {

    @Insert("INSERT INTO match_player_stat (team_player_id, stats) VALUES (#{teamPlayerId}, #{stats}::jsonb)")
    void insert(MatchPlayerStat stat);

    @Select("SELECT stats::text FROM match_player_stat WHERE team_player_id = #{teamPlayerId}")
    Optional<String> findStatsByTeamPlayerId(Long teamPlayerId);
}
