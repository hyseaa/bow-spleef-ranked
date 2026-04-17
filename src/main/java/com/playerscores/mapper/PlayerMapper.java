package com.playerscores.mapper;

import com.playerscores.model.Player;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PlayerMapper {

    @Insert("INSERT INTO player (uuid) VALUES (#{uuid}) ON CONFLICT (uuid) DO NOTHING")
    void insertIfAbsent(UUID uuid);

    @Select("SELECT uuid, discord_id, username, username_cached_at FROM player WHERE uuid = #{uuid}")
    Optional<Player> findByUuid(UUID uuid);

    @Select({
        "<script>",
        "SELECT uuid, discord_id, username, username_cached_at FROM player",
        "WHERE uuid IN",
        "<foreach item='uuid' collection='uuids' open='(' separator=',' close=')'>#{uuid}</foreach>",
        "</script>"
    })
    List<Player> findByUuids(@Param("uuids") List<UUID> uuids);

    @Select("SELECT uuid, discord_id, username, username_cached_at FROM player WHERE discord_id IS NOT NULL ORDER BY uuid")
    List<Player> findVerifiedPlayers();

    @Select("SELECT uuid, discord_id, username, username_cached_at FROM player WHERE discord_id = #{discordId}")
    Optional<Player> findByDiscordId(String discordId);

    @Update("UPDATE player SET username = #{username}, username_cached_at = NOW() WHERE uuid = #{uuid}")
    void updateUsernameCache(@Param("uuid") UUID uuid, @Param("username") String username);

    @Update("UPDATE player SET discord_id = #{discordId} WHERE uuid = #{uuid}")
    void updateDiscordId(@Param("uuid") UUID uuid, @Param("discordId") String discordId);
}
