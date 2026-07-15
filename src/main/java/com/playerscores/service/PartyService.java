package com.playerscores.service;

import com.playerscores.dto.CreatePartyRequest;
import com.playerscores.dto.PartyInviteRequest;
import com.playerscores.dto.PartyInviteResponse;
import com.playerscores.dto.PartyResponse;
import com.playerscores.dto.PlayerSummaryResponse;
import com.playerscores.exception.AlreadyInPartyException;
import com.playerscores.exception.PartyForbiddenException;
import com.playerscores.exception.PartyFullException;
import com.playerscores.exception.PartyInviteNotFoundException;
import com.playerscores.exception.PartyNotFoundException;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.exception.PlayerNotInPartyException;
import com.playerscores.mapper.PartyInviteMapper;
import com.playerscores.mapper.PartyMapper;
import com.playerscores.mapper.PartyMemberMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Party;
import com.playerscores.model.PartyInvite;
import com.playerscores.model.PartyMember;
import com.playerscores.model.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyService {

    private final PartyMapper partyMapper;
    private final PartyMemberMapper partyMemberMapper;
    private final PartyInviteMapper partyInviteMapper;
    private final PlayerMapper playerMapper;
    private final UsernameCache usernameCache;

    @Value("${party.invite-ttl-minutes:60}")
    private int inviteTtlMinutes;

    @Value("${party.max-size:16}")
    private int maxSize;

    @Transactional
    public PartyResponse create(CreatePartyRequest request) {
        UUID leaderUuid = resolveDiscordId(request.leaderDiscordId());
        if (partyMemberMapper.findByPlayerUuid(leaderUuid).isPresent()) {
            throw new AlreadyInPartyException(request.leaderDiscordId());
        }

        Party party = new Party();
        party.setLeaderUuid(leaderUuid);
        partyMapper.insert(party);
        insertMember(party.getId(), leaderUuid, request.leaderDiscordId());

        log.info("Party created: id={}, leader={}", party.getId(), leaderUuid);
        return toResponse(party);
    }

    @Transactional(readOnly = true)
    public PartyResponse getParty(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public PartyResponse getPartyByPlayer(String discordId) {
        UUID playerUuid = resolveDiscordId(discordId);
        PartyMember member = partyMemberMapper.findByPlayerUuid(playerUuid)
                .orElseThrow(() -> new PartyNotFoundException(discordId));
        return toResponse(findOrThrow(member.getPartyId()));
    }

    @Transactional
    public PartyResponse invite(Long partyId, PartyInviteRequest request) {
        Party party = findOrThrow(partyId);
        UUID inviterUuid = resolveDiscordId(request.inviterDiscordId());
        requireLeader(party, inviterUuid, "Only the party leader can invite players");

        UUID inviteeUuid = resolveDiscordId(request.inviteeDiscordId());
        // Being in ANOTHER party is fine at invite time; it is only blocked at accept
        partyMemberMapper.findByPlayerUuid(inviteeUuid)
                .filter(m -> m.getPartyId().equals(partyId))
                .ifPresent(m -> {
                    throw new AlreadyInPartyException(request.inviteeDiscordId());
                });
        requireNotFull(party);

        PartyInvite invite = new PartyInvite();
        invite.setPartyId(partyId);
        invite.setPlayerUuid(inviteeUuid);
        invite.setExpiresAt(OffsetDateTime.now().plusMinutes(inviteTtlMinutes));
        partyInviteMapper.upsert(invite);

        log.info("Party invite sent: partyId={}, invitee={}", partyId, inviteeUuid);
        return toResponse(party);
    }

    @Transactional
    public PartyResponse accept(Long partyId, String discordId) {
        UUID playerUuid = resolveDiscordId(discordId);
        partyInviteMapper.findActive(partyId, playerUuid)
                .orElseThrow(() -> new PartyInviteNotFoundException(partyId, discordId));
        if (partyMemberMapper.findByPlayerUuid(playerUuid).isPresent()) {
            throw new AlreadyInPartyException(discordId);
        }
        Party party = findOrThrow(partyId);
        requireNotFull(party);

        insertMember(partyId, playerUuid, discordId);
        // Joining a party voids every other pending invite of this player
        partyInviteMapper.deleteByPlayerUuid(playerUuid);

        log.info("Party invite accepted: partyId={}, player={}", partyId, playerUuid);
        return toResponse(party);
    }

    @Transactional
    public void decline(Long partyId, String discordId) {
        UUID playerUuid = resolveDiscordId(discordId);
        int deleted = partyInviteMapper.delete(partyId, playerUuid);
        if (deleted == 0) {
            throw new PartyInviteNotFoundException(partyId, discordId);
        }
        log.info("Party invite declined: partyId={}, player={}", partyId, playerUuid);
    }

    @Transactional
    public void leave(Long partyId, String discordId) {
        Party party = findOrThrow(partyId);
        UUID playerUuid = resolveDiscordId(discordId);

        if (playerUuid.equals(party.getLeaderUuid())) {
            // The leader leaving dissolves the party (members and invites cascade)
            partyMapper.deleteById(partyId);
            log.info("Party disbanded (leader left): id={}, leader={}", partyId, playerUuid);
            return;
        }
        int deleted = partyMemberMapper.delete(partyId, playerUuid);
        if (deleted == 0) {
            throw new PlayerNotInPartyException(discordId, partyId);
        }
        log.info("Party member left: partyId={}, player={}", partyId, playerUuid);
    }

    @Transactional
    public PartyResponse kick(Long partyId, String leaderDiscordId, String targetDiscordId) {
        Party party = findOrThrow(partyId);
        UUID leaderUuid = resolveDiscordId(leaderDiscordId);
        requireLeader(party, leaderUuid, "Only the party leader can kick members");

        UUID targetUuid = resolveDiscordId(targetDiscordId);
        if (targetUuid.equals(party.getLeaderUuid())) {
            throw new PartyForbiddenException("The leader cannot kick themselves; leave or disband instead");
        }
        int deleted = partyMemberMapper.delete(partyId, targetUuid);
        if (deleted == 0) {
            throw new PlayerNotInPartyException(targetDiscordId, partyId);
        }
        log.info("Party member kicked: partyId={}, target={}", partyId, targetUuid);
        return toResponse(party);
    }

    @Transactional
    public void disband(Long partyId, String leaderDiscordId) {
        Party party = findOrThrow(partyId);
        UUID leaderUuid = resolveDiscordId(leaderDiscordId);
        requireLeader(party, leaderUuid, "Only the party leader can disband the party");

        partyMapper.deleteById(partyId);
        log.info("Party disbanded: id={}, leader={}", partyId, leaderUuid);
    }

    private void insertMember(Long partyId, UUID playerUuid, String discordId) {
        PartyMember member = new PartyMember();
        member.setPartyId(partyId);
        member.setPlayerUuid(playerUuid);
        try {
            partyMemberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            // Concurrent create/accept: the UNIQUE(player_uuid) constraint settles the race
            throw new AlreadyInPartyException(discordId);
        } catch (DataIntegrityViolationException e) {
            // Party deleted between the lookup and the insert (concurrent disband)
            throw new PartyNotFoundException(partyId);
        }
    }

    private Party findOrThrow(Long id) {
        return partyMapper.findById(id)
                .orElseThrow(() -> new PartyNotFoundException(id));
    }

    private UUID resolveDiscordId(String discordId) {
        return playerMapper.findByDiscordId(discordId)
                .map(Player::getUuid)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
    }

    private void requireLeader(Party party, UUID callerUuid, String message) {
        if (!callerUuid.equals(party.getLeaderUuid())) {
            throw new PartyForbiddenException(message);
        }
    }

    private void requireNotFull(Party party) {
        if (partyMemberMapper.countByPartyId(party.getId()) >= maxSize) {
            throw new PartyFullException(party.getId(), maxSize);
        }
    }

    private PartyResponse toResponse(Party party) {
        List<PlayerSummaryResponse> members = partyMemberMapper.findByPartyId(party.getId()).stream()
                .map(m -> toSummary(m.getPlayerUuid()))
                .toList();
        List<PartyInviteResponse> pendingInvites = partyInviteMapper.findActiveByPartyId(party.getId()).stream()
                .map(i -> new PartyInviteResponse(
                        i.getPartyId(), toSummary(i.getPlayerUuid()), i.getCreatedAt(), i.getExpiresAt()))
                .toList();
        return new PartyResponse(
                party.getId(),
                toSummary(party.getLeaderUuid()),
                members,
                pendingInvites,
                party.getCreatedAt()
        );
    }

    private PlayerSummaryResponse toSummary(UUID uuid) {
        return new PlayerSummaryResponse(uuid, usernameCache.get(uuid));
    }
}
