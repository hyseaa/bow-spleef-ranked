package com.playerscores.mapper;

import com.playerscores.model.PartyInvite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface PartyInviteMapper {

    /** Re-inviting an already invited player refreshes the invite's TTL instead of failing. */
    @Insert("INSERT INTO party_invite (party_id, player_uuid, expires_at) " +
            "VALUES (#{partyId}, #{playerUuid}, #{expiresAt}) " +
            "ON CONFLICT (party_id, player_uuid) DO UPDATE SET expires_at = EXCLUDED.expires_at, created_at = NOW()")
    void upsert(PartyInvite invite);

    @Select("SELECT id, party_id, player_uuid, created_at, expires_at FROM party_invite " +
            "WHERE party_id = #{partyId} AND player_uuid = #{playerUuid} AND expires_at > NOW()")
    Optional<PartyInvite> findActive(@Param("partyId") Long partyId, @Param("playerUuid") UUID playerUuid);

    @Select("SELECT id, party_id, player_uuid, created_at, expires_at FROM party_invite " +
            "WHERE party_id = #{partyId} AND expires_at > NOW() ORDER BY id ASC")
    List<PartyInvite> findActiveByPartyId(@Param("partyId") Long partyId);

    @Delete("DELETE FROM party_invite WHERE party_id = #{partyId} AND player_uuid = #{playerUuid}")
    int delete(@Param("partyId") Long partyId, @Param("playerUuid") UUID playerUuid);

    @Delete("DELETE FROM party_invite WHERE player_uuid = #{playerUuid}")
    int deleteByPlayerUuid(UUID playerUuid);

    @Delete("DELETE FROM party_invite WHERE expires_at < NOW()")
    int deleteExpired();
}
