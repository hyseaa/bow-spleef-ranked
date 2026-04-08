package com.playerscores.mapper;

import com.playerscores.model.Player;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PlayerMapper {

    @Insert("INSERT INTO player (uuid, username) VALUES (#{uuid}, #{username}) "
            + "ON CONFLICT (uuid) DO UPDATE SET username = EXCLUDED.username")
    void upsert(Player player);

    @Select("SELECT uuid, username FROM player WHERE uuid = #{uuid}")
    Optional<Player> findByUuid(UUID uuid);
}
