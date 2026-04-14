package com.playerscores.mapper;

import com.playerscores.model.GameType;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface GameTypeMapper {

    @Insert("INSERT INTO game_types (name, display_name, ranked) VALUES (#{name}, #{displayName}, #{ranked})")
    void insert(GameType gameType);

    @Select("SELECT name, display_name, ranked FROM game_types WHERE name = #{name}")
    Optional<GameType> findByName(String name);

    @Select("SELECT name, display_name, ranked FROM game_types ORDER BY name ASC")
    List<GameType> findAll();
}
