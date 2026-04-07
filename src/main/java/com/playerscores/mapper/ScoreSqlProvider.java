package com.playerscores.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.jdbc.SQL;

public class ScoreSqlProvider {

    public String findLeaderboard(@Param("game") String game, @Param("limit") int limit) {
        SQL sql = new SQL()
                .SELECT("p.id AS player_id", "p.username", "MAX(s.value) AS best_score", "s.game")
                .FROM("scores s")
                .JOIN("players p ON p.id = s.player_id");

        if (game != null) {
            sql.WHERE("s.game = #{game}");
        }

        sql.GROUP_BY("p.id", "p.username", "s.game")
           .ORDER_BY("best_score DESC");

        return sql.toString() + " LIMIT #{limit}";
    }
}
