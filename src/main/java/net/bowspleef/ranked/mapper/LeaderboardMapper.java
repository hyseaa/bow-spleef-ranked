package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.dto.WinsLeaderboardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LeaderboardMapper {

    /** Ranked by games won (spec 035): a best-of won 3-0 is three; every match of the game type, ranked or not. */
    @Select("SELECT r.player_uuid AS uuid, SUM(r.games_won) AS games_won, "
            + "COUNT(*) FILTER (WHERE r.result = 'WON') AS wins "
            + "FROM player_match_result r "
            + "WHERE r.game_type = #{gameType} "
            + "GROUP BY r.player_uuid "
            + "HAVING SUM(r.games_won) > 0 "
            + "ORDER BY games_won DESC, wins DESC, r.player_uuid ASC "
            + "LIMIT #{size} OFFSET #{offset}")
    List<WinsLeaderboardRow> findLeaderboard(
            @Param("gameType") String gameType,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM ("
            + "SELECT r.player_uuid FROM player_match_result r "
            + "WHERE r.game_type = #{gameType} "
            + "GROUP BY r.player_uuid "
            + "HAVING SUM(r.games_won) > 0"
            + ") AS sub")
    long countLeaderboard(@Param("gameType") String gameType);
}
