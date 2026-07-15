package com.playerscores.service;

import com.playerscores.dto.CreatePartyRequest;
import com.playerscores.dto.PartyInviteRequest;
import com.playerscores.dto.PartyResponse;
import com.playerscores.exception.AlreadyInPartyException;
import com.playerscores.exception.PartyForbiddenException;
import com.playerscores.exception.PartyFullException;
import com.playerscores.exception.PartyInviteNotFoundException;
import com.playerscores.exception.PlayerNotInPartyException;
import com.playerscores.mapper.PartyInviteMapper;
import com.playerscores.mapper.PartyMapper;
import com.playerscores.mapper.PartyMemberMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Party;
import com.playerscores.model.PartyInvite;
import com.playerscores.model.PartyMember;
import com.playerscores.model.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyServiceTest {

    @Mock
    private PartyMapper partyMapper;
    @Mock
    private PartyMemberMapper partyMemberMapper;
    @Mock
    private PartyInviteMapper partyInviteMapper;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private UsernameCache usernameCache;

    @InjectMocks
    private PartyService partyService;

    private static final UUID LEADER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID OUTSIDER = UUID.randomUUID();
    private static final long PARTY_ID = 5L;

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(partyService, "inviteTtlMinutes", 60);
        ReflectionTestUtils.setField(partyService, "maxSize", 3);
    }

    private static Player player(UUID uuid, String discordId) {
        Player p = new Player();
        p.setUuid(uuid);
        p.setDiscordId(discordId);
        return p;
    }

    private static Party party(long id, UUID leaderUuid) {
        Party p = new Party();
        p.setId(id);
        p.setLeaderUuid(leaderUuid);
        return p;
    }

    private static PartyMember member(long partyId, UUID playerUuid) {
        PartyMember m = new PartyMember();
        m.setPartyId(partyId);
        m.setPlayerUuid(playerUuid);
        return m;
    }

    private void stubPlayer(String discordId, UUID uuid) {
        when(playerMapper.findByDiscordId(discordId)).thenReturn(Optional.of(player(uuid, discordId)));
    }

    private void stubParty() {
        when(partyMapper.findById(PARTY_ID)).thenReturn(Optional.of(party(PARTY_ID, LEADER)));
    }

    @Test
    void create_insertsPartyAndLeaderMember() {
        stubPlayer("d-leader", LEADER);
        when(partyMemberMapper.findByPlayerUuid(LEADER)).thenReturn(Optional.empty());
        doAnswer(inv -> {
            inv.getArgument(0, Party.class).setId(PARTY_ID);
            return null;
        }).when(partyMapper).insert(any(Party.class));

        PartyResponse response = partyService.create(new CreatePartyRequest("d-leader"));

        assertThat(response.id()).isEqualTo(PARTY_ID);
        ArgumentCaptor<PartyMember> captor = ArgumentCaptor.forClass(PartyMember.class);
        verify(partyMemberMapper).insert(captor.capture());
        assertThat(captor.getValue().getPartyId()).isEqualTo(PARTY_ID);
        assertThat(captor.getValue().getPlayerUuid()).isEqualTo(LEADER);
    }

    @Test
    void create_alreadyInParty_throws() {
        stubPlayer("d-leader", LEADER);
        when(partyMemberMapper.findByPlayerUuid(LEADER)).thenReturn(Optional.of(member(9L, LEADER)));

        assertThatThrownBy(() -> partyService.create(new CreatePartyRequest("d-leader")))
                .isInstanceOf(AlreadyInPartyException.class);
        verify(partyMapper, never()).insert(any());
    }

    @Test
    void create_duplicateKeyRace_throwsAlreadyInParty() {
        stubPlayer("d-leader", LEADER);
        when(partyMemberMapper.findByPlayerUuid(LEADER)).thenReturn(Optional.empty());
        doAnswer(inv -> {
            inv.getArgument(0, Party.class).setId(PARTY_ID);
            return null;
        }).when(partyMapper).insert(any(Party.class));
        doThrow(new DuplicateKeyException("duplicate")).when(partyMemberMapper).insert(any(PartyMember.class));

        assertThatThrownBy(() -> partyService.create(new CreatePartyRequest("d-leader")))
                .isInstanceOf(AlreadyInPartyException.class);
    }

    @Test
    void invite_byNonLeader_forbidden() {
        stubParty();
        stubPlayer("d-member", MEMBER);

        assertThatThrownBy(() -> partyService.invite(PARTY_ID, new PartyInviteRequest("d-member", "d-out")))
                .isInstanceOf(PartyForbiddenException.class);
        verify(partyInviteMapper, never()).upsert(any());
    }

    @Test
    void invite_inviteeAlreadyMemberOfThisParty_throws() {
        stubParty();
        stubPlayer("d-leader", LEADER);
        stubPlayer("d-member", MEMBER);
        when(partyMemberMapper.findByPlayerUuid(MEMBER)).thenReturn(Optional.of(member(PARTY_ID, MEMBER)));

        assertThatThrownBy(() -> partyService.invite(PARTY_ID, new PartyInviteRequest("d-leader", "d-member")))
                .isInstanceOf(AlreadyInPartyException.class);
        verify(partyInviteMapper, never()).upsert(any());
    }

    @Test
    void invite_inviteeInAnotherParty_isAllowed() {
        stubParty();
        stubPlayer("d-leader", LEADER);
        stubPlayer("d-out", OUTSIDER);
        // Being in a different party does not block the invite, only the accept
        when(partyMemberMapper.findByPlayerUuid(OUTSIDER)).thenReturn(Optional.of(member(9L, OUTSIDER)));
        when(partyMemberMapper.countByPartyId(PARTY_ID)).thenReturn(1);

        partyService.invite(PARTY_ID, new PartyInviteRequest("d-leader", "d-out"));

        verify(partyInviteMapper).upsert(any(PartyInvite.class));
    }

    @Test
    void invite_partyFull_throws() {
        stubParty();
        stubPlayer("d-leader", LEADER);
        stubPlayer("d-out", OUTSIDER);
        when(partyMemberMapper.findByPlayerUuid(OUTSIDER)).thenReturn(Optional.empty());
        when(partyMemberMapper.countByPartyId(PARTY_ID)).thenReturn(3);

        assertThatThrownBy(() -> partyService.invite(PARTY_ID, new PartyInviteRequest("d-leader", "d-out")))
                .isInstanceOf(PartyFullException.class);
        verify(partyInviteMapper, never()).upsert(any());
    }

    @Test
    void invite_upsertsWithTtl() {
        stubParty();
        stubPlayer("d-leader", LEADER);
        stubPlayer("d-out", OUTSIDER);
        when(partyMemberMapper.findByPlayerUuid(OUTSIDER)).thenReturn(Optional.empty());
        when(partyMemberMapper.countByPartyId(PARTY_ID)).thenReturn(1);

        partyService.invite(PARTY_ID, new PartyInviteRequest("d-leader", "d-out"));

        ArgumentCaptor<PartyInvite> captor = ArgumentCaptor.forClass(PartyInvite.class);
        verify(partyInviteMapper).upsert(captor.capture());
        PartyInvite invite = captor.getValue();
        assertThat(invite.getPartyId()).isEqualTo(PARTY_ID);
        assertThat(invite.getPlayerUuid()).isEqualTo(OUTSIDER);
        assertThat(invite.getExpiresAt())
                .isBetween(OffsetDateTime.now().plusMinutes(59), OffsetDateTime.now().plusMinutes(61));
    }

    @Test
    void accept_insertsMemberAndClearsAllInvites() {
        stubPlayer("d-out", OUTSIDER);
        when(partyInviteMapper.findActive(PARTY_ID, OUTSIDER)).thenReturn(Optional.of(new PartyInvite()));
        when(partyMemberMapper.findByPlayerUuid(OUTSIDER)).thenReturn(Optional.empty());
        stubParty();
        when(partyMemberMapper.countByPartyId(PARTY_ID)).thenReturn(1);

        partyService.accept(PARTY_ID, "d-out");

        ArgumentCaptor<PartyMember> captor = ArgumentCaptor.forClass(PartyMember.class);
        verify(partyMemberMapper).insert(captor.capture());
        assertThat(captor.getValue().getPartyId()).isEqualTo(PARTY_ID);
        assertThat(captor.getValue().getPlayerUuid()).isEqualTo(OUTSIDER);
        verify(partyInviteMapper).deleteByPlayerUuid(OUTSIDER);
    }

    @Test
    void accept_noActiveInvite_throwsInviteNotFound() {
        stubPlayer("d-out", OUTSIDER);
        when(partyInviteMapper.findActive(PARTY_ID, OUTSIDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partyService.accept(PARTY_ID, "d-out"))
                .isInstanceOf(PartyInviteNotFoundException.class);
        verify(partyMemberMapper, never()).insert(any());
    }

    @Test
    void accept_callerInAnotherParty_throwsAlreadyInParty() {
        stubPlayer("d-out", OUTSIDER);
        when(partyInviteMapper.findActive(PARTY_ID, OUTSIDER)).thenReturn(Optional.of(new PartyInvite()));
        when(partyMemberMapper.findByPlayerUuid(OUTSIDER)).thenReturn(Optional.of(member(9L, OUTSIDER)));

        assertThatThrownBy(() -> partyService.accept(PARTY_ID, "d-out"))
                .isInstanceOf(AlreadyInPartyException.class);
        verify(partyMemberMapper, never()).insert(any());
    }

    @Test
    void decline_withoutInvite_throwsInviteNotFound() {
        stubPlayer("d-out", OUTSIDER);
        when(partyInviteMapper.delete(PARTY_ID, OUTSIDER)).thenReturn(0);

        assertThatThrownBy(() -> partyService.decline(PARTY_ID, "d-out"))
                .isInstanceOf(PartyInviteNotFoundException.class);
    }

    @Test
    void leave_member_deletesMemberRow() {
        stubParty();
        stubPlayer("d-member", MEMBER);
        when(partyMemberMapper.delete(PARTY_ID, MEMBER)).thenReturn(1);

        partyService.leave(PARTY_ID, "d-member");

        verify(partyMapper, never()).deleteById(any());
    }

    @Test
    void leave_leader_disbandsParty() {
        stubParty();
        stubPlayer("d-leader", LEADER);

        partyService.leave(PARTY_ID, "d-leader");

        verify(partyMapper).deleteById(PARTY_ID);
        verify(partyMemberMapper, never()).delete(any(), any());
    }

    @Test
    void leave_notMember_throwsNotInParty() {
        stubParty();
        stubPlayer("d-out", OUTSIDER);
        when(partyMemberMapper.delete(PARTY_ID, OUTSIDER)).thenReturn(0);

        assertThatThrownBy(() -> partyService.leave(PARTY_ID, "d-out"))
                .isInstanceOf(PlayerNotInPartyException.class);
    }

    @Test
    void kick_byNonLeader_forbidden() {
        stubParty();
        stubPlayer("d-member", MEMBER);

        assertThatThrownBy(() -> partyService.kick(PARTY_ID, "d-member", "d-out"))
                .isInstanceOf(PartyForbiddenException.class);
    }

    @Test
    void kick_targetNotMember_throwsNotInParty() {
        stubParty();
        stubPlayer("d-leader", LEADER);
        stubPlayer("d-out", OUTSIDER);
        when(partyMemberMapper.delete(PARTY_ID, OUTSIDER)).thenReturn(0);

        assertThatThrownBy(() -> partyService.kick(PARTY_ID, "d-leader", "d-out"))
                .isInstanceOf(PlayerNotInPartyException.class);
    }

    @Test
    void kick_leaderKickingThemselves_forbidden() {
        stubParty();
        stubPlayer("d-leader", LEADER);

        assertThatThrownBy(() -> partyService.kick(PARTY_ID, "d-leader", "d-leader"))
                .isInstanceOf(PartyForbiddenException.class);
        verify(partyMemberMapper, never()).delete(any(), any());
    }

    @Test
    void disband_byNonLeader_forbidden() {
        stubParty();
        stubPlayer("d-member", MEMBER);

        assertThatThrownBy(() -> partyService.disband(PARTY_ID, "d-member"))
                .isInstanceOf(PartyForbiddenException.class);
        verify(partyMapper, never()).deleteById(any());
    }
}
