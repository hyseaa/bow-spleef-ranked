package com.playerscores.mapper;

import com.playerscores.model.Match;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface MatchMapper {

    @Insert("INSERT INTO match (game_type, source, played_at) VALUES (#{gameType}, #{source}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Match match);

    @Select("SELECT id, game_type, source, played_at FROM match WHERE id = #{id}")
    Optional<Match> findById(Long id);
}
