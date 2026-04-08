package com.playerscores.mapper;

import com.playerscores.model.Team;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeamMapper {

    @Insert("INSERT INTO team (match_id, color, score) VALUES (#{matchId}, #{color}, #{score})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Team team);

    @Select("SELECT id, match_id, color, score FROM team WHERE match_id = #{matchId}")
    List<Team> findByMatchId(Long matchId);
}
