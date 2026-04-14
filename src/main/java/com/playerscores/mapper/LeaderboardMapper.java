package com.playerscores.mapper;

import com.playerscores.dto.WinsLeaderboardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LeaderboardMapper {

    @Select("SELECT p.uuid, COUNT(*) AS wins "
            + "FROM player p "
            + "JOIN team_player tp ON tp.player_uuid = p.uuid "
            + "JOIN team t ON t.id = tp.team_id "
            + "JOIN match m ON m.id = t.match_id "
            + "WHERE t.score = (SELECT MAX(t2.score) FROM team t2 WHERE t2.match_id = t.match_id) "
            + "AND m.game_type = #{gameType} "
            + "GROUP BY p.uuid "
            + "ORDER BY wins DESC, p.uuid ASC "
            + "LIMIT #{size} OFFSET #{offset}")
    List<WinsLeaderboardRow> findLeaderboard(
            @Param("gameType") String gameType,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM ("
            + "SELECT p.uuid "
            + "FROM player p "
            + "JOIN team_player tp ON tp.player_uuid = p.uuid "
            + "JOIN team t ON t.id = tp.team_id "
            + "JOIN match m ON m.id = t.match_id "
            + "WHERE t.score = (SELECT MAX(t2.score) FROM team t2 WHERE t2.match_id = t.match_id) "
            + "AND m.game_type = #{gameType} "
            + "GROUP BY p.uuid"
            + ") AS sub")
    long countLeaderboard(@Param("gameType") String gameType);
}
