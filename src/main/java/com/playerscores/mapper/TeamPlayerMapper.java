package com.playerscores.mapper;

import com.playerscores.dto.PlayerSummaryResponse;
import com.playerscores.model.TeamPlayer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeamPlayerMapper {

    @Insert("INSERT INTO team_player (team_id, player_uuid) VALUES (#{teamId}, #{playerUuid})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(TeamPlayer teamPlayer);

    @Select("SELECT p.uuid, p.username "
            + "FROM team_player tp JOIN player p ON p.uuid = tp.player_uuid "
            + "WHERE tp.team_id = #{teamId}")
    List<PlayerSummaryResponse> findPlayersByTeamId(Long teamId);
}
