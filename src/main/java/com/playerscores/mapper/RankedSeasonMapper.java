package com.playerscores.mapper;

import com.playerscores.model.RankedSeason;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RankedSeasonMapper {

    @Insert("INSERT INTO ranked_season (name, starts_at, game_type, active) " +
            "VALUES (#{name}, #{startsAt}, #{gameType}, #{active})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RankedSeason season);

    @Select("SELECT id, name, starts_at, ends_at, game_type, active FROM ranked_season WHERE id = #{id}")
    Optional<RankedSeason> findById(Long id);

    @Select("SELECT id, name, starts_at, ends_at, game_type, active FROM ranked_season ORDER BY id DESC")
    List<RankedSeason> findAll();

    @Select("SELECT id, name, starts_at, ends_at, game_type, active FROM ranked_season " +
            "WHERE active = TRUE AND game_type = #{gameType}")
    Optional<RankedSeason> findActiveByGameType(String gameType);

    @Update("UPDATE ranked_season SET active = FALSE, ends_at = NOW() WHERE id = #{id}")
    int deactivate(Long id);
}
