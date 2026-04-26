package com.playerscores.mapper;

import com.playerscores.model.TeamPlayer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TeamPlayerMapper {

    @Insert("INSERT INTO team_player (team_id, player_uuid) VALUES (#{teamId}, #{playerUuid})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TeamPlayer teamPlayer);

    @Select("SELECT player_uuid FROM team_player WHERE team_id = #{teamId}")
    List<UUID> findPlayerUuidsByTeamId(Long teamId);

    @Select("SELECT id, team_id, player_uuid FROM team_player WHERE team_id = #{teamId}")
    List<TeamPlayer> findByTeamId(Long teamId);
}
