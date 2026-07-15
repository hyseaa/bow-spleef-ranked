package com.playerscores.mapper;

import com.playerscores.model.PartyMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PartyMemberMapper {

    @Insert("INSERT INTO party_member (party_id, player_uuid) VALUES (#{partyId}, #{playerUuid})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(PartyMember member);

    @Select("SELECT id, party_id, player_uuid, joined_at FROM party_member " +
            "WHERE party_id = #{partyId} ORDER BY joined_at ASC, id ASC")
    List<PartyMember> findByPartyId(@Param("partyId") Long partyId);

    @Select("SELECT id, party_id, player_uuid, joined_at FROM party_member WHERE player_uuid = #{playerUuid}")
    Optional<PartyMember> findByPlayerUuid(UUID playerUuid);

    @Select("SELECT COUNT(*) FROM party_member WHERE party_id = #{partyId}")
    int countByPartyId(@Param("partyId") Long partyId);

    @Delete("DELETE FROM party_member WHERE party_id = #{partyId} AND player_uuid = #{playerUuid}")
    int delete(@Param("partyId") Long partyId, @Param("playerUuid") UUID playerUuid);
}
