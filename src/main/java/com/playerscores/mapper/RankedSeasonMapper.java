package com.playerscores.mapper;

import com.playerscores.model.RankedSeason;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RankedSeasonMapper {

    @Insert("INSERT INTO ranked_season (name, starts_at, game_type, active, rating_algorithm) " +
            "VALUES (#{name}, #{startsAt}, #{gameType}, #{active}, #{ratingAlgorithm})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RankedSeason season);

    @Select("SELECT id, name, starts_at, ends_at, game_type, active, elo_dirty, rating_algorithm FROM ranked_season WHERE id = #{id}")
    Optional<RankedSeason> findById(Long id);

    @Select("SELECT id, name, starts_at, ends_at, game_type, active, elo_dirty, rating_algorithm FROM ranked_season ORDER BY id DESC")
    List<RankedSeason> findAll();

    @Select("SELECT id, name, starts_at, ends_at, game_type, active, elo_dirty, rating_algorithm FROM ranked_season " +
            "WHERE active = TRUE AND game_type = #{gameType}")
    Optional<RankedSeason> findActiveByGameType(String gameType);

    /**
     * The active season of a game type, with its row locked until the end of the transaction (KI-28).
     * Every rating write of a season (live match or recompute) holds this lock, so they run one after
     * the other. If a concurrent deactivation commits while this waits, the season is no longer active
     * and nothing is returned.
     */
    @Select("SELECT id, name, starts_at, ends_at, game_type, active, elo_dirty, rating_algorithm FROM ranked_season " +
            "WHERE active = TRUE AND game_type = #{gameType} FOR UPDATE")
    Optional<RankedSeason> lockActiveByGameType(String gameType);

    @Update("UPDATE ranked_season SET active = FALSE, ends_at = NOW() WHERE id = #{id}")
    int deactivate(Long id);

    @Update("UPDATE ranked_season SET elo_dirty = TRUE WHERE id = #{id}")
    void markEloDirty(@Param("id") Long id);

    @Update("UPDATE ranked_season SET elo_dirty = FALSE WHERE id = #{id}")
    void clearEloDirty(@Param("id") Long id);

    @Select("SELECT id, name, starts_at, ends_at, game_type, active, elo_dirty, rating_algorithm FROM ranked_season WHERE elo_dirty = TRUE")
    List<RankedSeason> findDirtySeasons();
}
