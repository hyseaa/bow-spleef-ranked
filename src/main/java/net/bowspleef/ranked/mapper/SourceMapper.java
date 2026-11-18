package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.model.Source;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SourceMapper {

    @Insert("INSERT INTO source (name, display_name, kind) VALUES (#{name}, #{displayName}, #{kind})")
    void insert(Source source);

    @Select("SELECT name, display_name, kind FROM source WHERE name = #{name}")
    Optional<Source> findByName(String name);

    @Select("SELECT name, display_name, kind FROM source ORDER BY name ASC")
    List<Source> findAll();
}
