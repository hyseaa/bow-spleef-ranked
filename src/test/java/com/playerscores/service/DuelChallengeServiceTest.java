package com.playerscores.service;

import com.playerscores.dto.CreateDuelChallengeRequest;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.DuelChallengeResponse;
import com.playerscores.dto.MatchResponse;
import com.playerscores.exception.DuelChallengeForbiddenException;
import com.playerscores.exception.DuplicatePlayerInMatchException;
import com.playerscores.exception.InvalidTeamSizeException;
import com.playerscores.exception.PlayerNotInPartyException;
import com.playerscores.mapper.DuelChallengeMapper;
import com.playerscores.mapper.DuelChallengeParticipantMapper;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PartyMapper;
import com.playerscores.mapper.PartyMemberMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.DuelChallenge;
import com.playerscores.model.DuelChallengeParticipant;
import com.playerscores.model.GameType;
import com.playerscores.model.Party;
import com.playerscores.model.PartyMember;
import com.playerscores.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuelChallengeServiceTest {

    @Mock
    private DuelChallengeMapper duelChallengeMapper;
    @Mock
    private DuelChallengeParticipantMapper participantMapper;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private GameTypeMapper gameTypeMapper;
    @Mock
    private PartyMapper partyMapper;
    @Mock
    private PartyMemberMapper partyMemberMapper;
    @Mock
    private MatchService matchService;
    @Mock
    private UsernameCache usernameCache;

    @InjectMocks
    private DuelChallengeService duelChallengeService;

    private static final UUID CHALLENGER = UUID.randomUUID();
    private static final UUID CHALLENGED = UUID.randomUUID();
    private static final UUID CHALLENGER_MATE = UUID.randomUUID();
    private static final UUID CHALLENGED_MATE = UUID.randomUUID();

    private static Player player(UUID uuid, String discordId) {
        Player p = new Player();
        p.setUuid(uuid);
        p.setDiscordId(discordId);
        return p;
    }

    private static GameType gameType(String name, int teamSize) {
        GameType gt = new GameType();
        gt.setName(name);
        gt.setDisplayName(name);
        gt.setRanked(true);
        gt.setTeamSize(teamSize);
        return gt;
    }

    private void stubPlayer(String discordId, UUID uuid) {
        when(playerMapper.findByDiscordId(discordId)).thenReturn(Optional.of(player(uuid, discordId)));
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

    /** Puts a player in a party and makes that party resolvable by id. */
    private void stubPartyOf(UUID playerUuid, long partyId, UUID leaderUuid) {
        when(partyMemberMapper.findByPlayerUuid(playerUuid)).thenReturn(Optional.of(member(partyId, playerUuid)));
        when(partyMapper.findById(partyId)).thenReturn(Optional.of(party(partyId, leaderUuid)));
    }

    private void stubRoster(long partyId, UUID... memberUuids) {
        when(partyMemberMapper.findByPartyId(partyId)).thenReturn(
                java.util.Arrays.stream(memberUuids).map(u -> member(partyId, u)).toList());
    }

    @Test
    void create_2v2_snapshotsBothPartiesAndAddressesTheLeader() {
        stubPlayer("d-challenger", CHALLENGER);
        // The request targets a regular member of the opposing party, not its leader
        stubPlayer("d-mate2", CHALLENGED_MATE);
        when(gameTypeMapper.findByName("DUEL_2V2")).thenReturn(Optional.of(gameType("DUEL_2V2", 2)));
        stubPartyOf(CHALLENGER, 1L, CHALLENGER);
        stubPartyOf(CHALLENGED_MATE, 2L, CHALLENGED);
        stubRoster(1L, CHALLENGER, CHALLENGER_MATE);
        stubRoster(2L, CHALLENGED, CHALLENGED_MATE);
        doAnswer(inv -> {
            DuelChallenge c = inv.getArgument(0);
            c.setId(7L);
            c.setStatus("PENDING");
            return null;
        }).when(duelChallengeMapper).insert(any(DuelChallenge.class));

        DuelChallengeResponse response = duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-mate2", "DUEL_2V2"));

        assertThat(response.id()).isEqualTo(7L);

        ArgumentCaptor<DuelChallenge> challengeCaptor = ArgumentCaptor.forClass(DuelChallenge.class);
        verify(duelChallengeMapper).insert(challengeCaptor.capture());
        assertThat(challengeCaptor.getValue().getChallengedUuid()).isEqualTo(CHALLENGED);

        ArgumentCaptor<DuelChallengeParticipant> captor = ArgumentCaptor.forClass(DuelChallengeParticipant.class);
        verify(participantMapper, times(4)).insert(captor.capture());
        List<DuelChallengeParticipant> participants = captor.getAllValues();

        assertThat(participants).allMatch(p -> p.getChallengeId().equals(7L));
        assertThat(participants.stream()
                .filter(p -> DuelChallengeParticipant.SIDE_CHALLENGER.equals(p.getSide()))
                .map(DuelChallengeParticipant::getPlayerUuid))
                .containsExactlyInAnyOrder(CHALLENGER, CHALLENGER_MATE);
        assertThat(participants.stream()
                .filter(p -> DuelChallengeParticipant.SIDE_CHALLENGED.equals(p.getSide()))
                .map(DuelChallengeParticipant::getPlayerUuid))
                .containsExactlyInAnyOrder(CHALLENGED, CHALLENGED_MATE);
    }

    @Test
    void create_2v2_challengerNotInParty_forbidden() {
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(gameTypeMapper.findByName("DUEL_2V2")).thenReturn(Optional.of(gameType("DUEL_2V2", 2)));
        when(partyMemberMapper.findByPlayerUuid(CHALLENGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-challenged", "DUEL_2V2")))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        verify(duelChallengeMapper, never()).insert(any());
    }

    @Test
    void create_2v2_challengerNotLeader_forbidden() {
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(gameTypeMapper.findByName("DUEL_2V2")).thenReturn(Optional.of(gameType("DUEL_2V2", 2)));
        // The challenger is a regular member: their party is led by someone else
        stubPartyOf(CHALLENGER, 1L, CHALLENGER_MATE);

        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-challenged", "DUEL_2V2")))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        verify(duelChallengeMapper, never()).insert(any());
    }

    @Test
    void create_2v2_targetNotInParty_throwsNotInParty() {
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(gameTypeMapper.findByName("DUEL_2V2")).thenReturn(Optional.of(gameType("DUEL_2V2", 2)));
        stubPartyOf(CHALLENGER, 1L, CHALLENGER);
        when(partyMemberMapper.findByPlayerUuid(CHALLENGED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-challenged", "DUEL_2V2")))
                .isInstanceOf(PlayerNotInPartyException.class);
        verify(duelChallengeMapper, never()).insert(any());
    }

    @Test
    void create_2v2_targetInSameParty_throwsDuplicatePlayer() {
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-mate1", CHALLENGER_MATE);
        when(gameTypeMapper.findByName("DUEL_2V2")).thenReturn(Optional.of(gameType("DUEL_2V2", 2)));
        stubPartyOf(CHALLENGER, 1L, CHALLENGER);
        when(partyMemberMapper.findByPlayerUuid(CHALLENGER_MATE))
                .thenReturn(Optional.of(member(1L, CHALLENGER_MATE)));

        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-mate1", "DUEL_2V2")))
                .isInstanceOf(DuplicatePlayerInMatchException.class);
        verify(duelChallengeMapper, never()).insert(any());
    }

    @Test
    void create_2v2_partySizeMismatch_throwsInvalidTeamSize() {
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(gameTypeMapper.findByName("DUEL_2V2")).thenReturn(Optional.of(gameType("DUEL_2V2", 2)));
        stubPartyOf(CHALLENGER, 1L, CHALLENGER);
        stubPartyOf(CHALLENGED, 2L, CHALLENGED);
        // A 3-member party cannot enter a 2v2 challenge
        stubRoster(1L, CHALLENGER, CHALLENGER_MATE, UUID.randomUUID());
        stubRoster(2L, CHALLENGED, CHALLENGED_MATE);

        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-challenged", "DUEL_2V2")))
                .isInstanceOf(InvalidTeamSizeException.class);
        verify(duelChallengeMapper, never()).insert(any());
    }

    @Test
    void create_1v1_neverConsultsParties() {
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(gameTypeMapper.findByName("DUEL_1V1")).thenReturn(Optional.of(gameType("DUEL_1V1", 1)));
        doAnswer(inv -> {
            DuelChallenge c = inv.getArgument(0);
            c.setId(8L);
            c.setStatus("PENDING");
            return null;
        }).when(duelChallengeMapper).insert(any(DuelChallenge.class));

        DuelChallengeResponse response = duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-challenged", "DUEL_1V1"));

        assertThat(response.id()).isEqualTo(8L);
        verifyNoInteractions(partyMapper, partyMemberMapper);
    }

    @Test
    void confirmScores_buildsMatchWithFullRosters() {
        DuelChallenge challenge = new DuelChallenge();
        challenge.setId(7L);
        challenge.setChallengerUuid(CHALLENGER);
        challenge.setChallengedUuid(CHALLENGED);
        challenge.setGameType("DUEL_2V2");
        challenge.setStatus("REPORTED");
        challenge.setReporterUuid(CHALLENGER);
        challenge.setScoreChallenger(5);
        challenge.setScoreChallenged(3);
        when(duelChallengeMapper.findById(7L)).thenReturn(Optional.of(challenge));
        stubPlayer("d-challenged", CHALLENGED);
        when(participantMapper.findByChallengeId(7L)).thenReturn(List.of(
                participant(CHALLENGER, DuelChallengeParticipant.SIDE_CHALLENGER),
                participant(CHALLENGER_MATE, DuelChallengeParticipant.SIDE_CHALLENGER),
                participant(CHALLENGED, DuelChallengeParticipant.SIDE_CHALLENGED),
                participant(CHALLENGED_MATE, DuelChallengeParticipant.SIDE_CHALLENGED)));
        when(matchService.createMatch(any())).thenReturn(new MatchResponse(
                42L, "DUEL_2V2", "Duel 2v2", "discord_bot", OffsetDateTime.now(), List.of(), 1L, null));

        DuelChallengeResponse response = duelChallengeService.confirmScores(7L, "d-challenged");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.matchId()).isEqualTo(42L);

        ArgumentCaptor<CreateMatchRequest> captor = ArgumentCaptor.forClass(CreateMatchRequest.class);
        verify(matchService).createMatch(captor.capture());
        CreateMatchRequest matchRequest = captor.getValue();

        assertThat(matchRequest.teams()).hasSize(2);
        assertThat(matchRequest.teams().get(0).score()).isEqualTo(5);
        assertThat(matchRequest.teams().get(0).playerUuids())
                .containsExactlyInAnyOrder(CHALLENGER, CHALLENGER_MATE);
        assertThat(matchRequest.teams().get(1).score()).isEqualTo(3);
        assertThat(matchRequest.teams().get(1).playerUuids())
                .containsExactlyInAnyOrder(CHALLENGED, CHALLENGED_MATE);
    }

    @Test
    void accept_byTeammate_isForbidden() {
        DuelChallenge challenge = new DuelChallenge();
        challenge.setId(7L);
        challenge.setChallengerUuid(CHALLENGER);
        challenge.setChallengedUuid(CHALLENGED);
        challenge.setStatus("PENDING");
        when(duelChallengeMapper.findById(7L)).thenReturn(Optional.of(challenge));
        // A teammate of the challenged side, not the captain
        stubPlayer("d-mate2", CHALLENGED_MATE);

        assertThatThrownBy(() -> duelChallengeService.accept(7L, "d-mate2"))
                .isInstanceOf(DuelChallengeForbiddenException.class);
    }

    private static DuelChallengeParticipant participant(UUID uuid, String side) {
        DuelChallengeParticipant p = new DuelChallengeParticipant();
        p.setChallengeId(7L);
        p.setPlayerUuid(uuid);
        p.setSide(side);
        return p;
    }
}
