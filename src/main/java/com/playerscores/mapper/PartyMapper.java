package com.playerscores.mapper;

import com.playerscores.model.Party;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface PartyMapper {

    @Insert("INSERT INTO party (leader_uuid) VALUES (#{leaderUuid})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Party party);

    @Select("SELECT id, leader_uuid, created_at FROM party WHERE id = #{id}")
    Optional<Party> findById(@Param("id") Long id);

    @Delete("DELETE FROM party WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
