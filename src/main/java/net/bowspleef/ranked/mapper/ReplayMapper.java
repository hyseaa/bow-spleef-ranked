package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.dto.ReplayPlayerRow;
import net.bowspleef.ranked.dto.ReplaySummaryRow;
import net.bowspleef.ranked.model.MatchReplay;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/** The replays of the matches (spec 024, ADR-0026). Only {@link #findByMatch} reads the file. */
@Mapper
public interface ReplayMapper {

    /** Returns 1 when stored, 0 when the match already has a replay (two calls at once: the other one won). */
    @Insert("INSERT INTO match_replay (match_id, map, duration_ticks, format_version, size_bytes, sha256, data) "
            + "VALUES (#{matchId}, #{map}, #{durationTicks}, #{formatVersion}, #{sizeBytes}, #{sha256}, #{data}) "
            + "ON CONFLICT (match_id) DO NOTHING")
    int insert(MatchReplay replay);

    @Select("SELECT sha256 FROM match_replay WHERE match_id = #{matchId}")
    Optional<String> findSha256(Long matchId);

    @Select("SELECT match_id, map, duration_ticks, format_version, size_bytes, sha256, data, created_at "
            + "FROM match_replay WHERE match_id = #{matchId}")
    Optional<MatchReplay> findByMatch(Long matchId);

    /**
     * The newest replays first (by the date of their match); with a username, only the matches where a player of
     * that cached name played (case-insensitive).
     */
    @Select("<script>"
            + "SELECT r.match_id, m.played_at, m.game_type, r.map, r.duration_ticks "
            + "FROM match_replay r JOIN match m ON m.id = r.match_id "
            + "<if test='username != null'>"
            + "WHERE EXISTS (SELECT 1 FROM team t JOIN team_player tp ON tp.team_id = t.id "
            + "JOIN player p ON p.uuid = tp.player_uuid "
            + "WHERE t.match_id = r.match_id AND LOWER(p.username) = LOWER(#{username})) "
            + "</if>"
            + "ORDER BY m.played_at DESC, r.match_id DESC LIMIT #{size}"
            + "</script>")
    List<ReplaySummaryRow> findLatest(@Param("username") String username, @Param("size") int size);

    /** The players of these matches, in the order of their teams. */
    @Select("<script>"
            + "SELECT t.match_id, tp.player_uuid FROM team t JOIN team_player tp ON tp.team_id = t.id "
            + "WHERE t.match_id IN "
            + "<foreach item='id' collection='matchIds' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY t.match_id, t.id, tp.id"
            + "</script>")
    List<ReplayPlayerRow> findPlayers(@Param("matchIds") List<Long> matchIds);
}
