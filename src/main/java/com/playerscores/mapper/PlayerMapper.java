package com.playerscores.mapper;

import com.playerscores.model.Player;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface PlayerMapper {

    @Insert("INSERT INTO players (username, created_at) VALUES (#{username}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Player player);

    @Select("SELECT id, username, created_at FROM players WHERE id = #{id}")
    Optional<Player> findById(Long id);

    @Select("SELECT id, username, created_at FROM players WHERE username = #{username}")
    Optional<Player> findByUsername(String username);
}
