package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.model.Match;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MatchMapper {

    // clock_timestamp(), not NOW(): NOW() is the start of the transaction, and a ranked match must be dated
    // after it has obtained the season lock so that played_at order is the order in which it was rated (KI-28).
    @Insert("INSERT INTO match (game_type, source, played_at, idempotency_key, modifiers) "
            + "VALUES (#{gameType}, #{source}, clock_timestamp(), #{idempotencyKey}, CAST(#{modifiers, jdbcType=VARCHAR} AS jsonb))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Match match);

    @Update("UPDATE match SET ranked_season_id = #{rankedSeasonId} WHERE id = #{matchId}")
    void updateRankedSeasonId(@Param("matchId") Long matchId, @Param("rankedSeasonId") Long rankedSeasonId);

    @Select("SELECT id, game_type, source, played_at, ranked_season_id, modifiers::text AS modifiers FROM match WHERE id = #{id}")
    Optional<Match> findById(Long id);

    @Select("SELECT id, game_type, source, played_at, ranked_season_id FROM match WHERE game_type = #{gameType} ORDER BY played_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Match> findByGameType(@Param("gameType") String gameType, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM match WHERE game_type = #{gameType}")
    long countByGameType(@Param("gameType") String gameType);

    @Delete("DELETE FROM match WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT id, game_type, source, played_at, ranked_season_id FROM match WHERE ranked_season_id = #{seasonId} ORDER BY played_at ASC, id ASC")
    List<Match> findByRankedSeasonId(@Param("seasonId") Long seasonId);

    @Select("SELECT id, game_type, source, played_at, ranked_season_id, idempotency_key FROM match "
            + "WHERE source = #{source} AND idempotency_key = #{key}")
    Optional<Match> findByIdempotencyKey(@Param("source") String source, @Param("key") String key);

    /**
     * Takes a transaction-scoped advisory lock on (source, key), so that two requests carrying the same
     * idempotency key are processed one after the other (spec 002). Released at commit or rollback.
     */
    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{source} || ':' || #{key}, 0))) AS locked")
    int lockIdempotencyKey(@Param("source") String source, @Param("key") String key);
}
