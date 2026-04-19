package com.playerscores.mapper;

import com.playerscores.model.RankTitle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RankTitleMapper {

    @Select("SELECT min_percentile, name FROM rank_title ORDER BY min_percentile ASC")
    List<RankTitle> findAll();
}
