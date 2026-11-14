package net.bowspleef.ranked.service;

import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateDuelChallengeRequest;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.DuelCancelledPayload;
import net.bowspleef.ranked.dto.DuelChallengeResponse;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.exception.DuelAlreadyOpenException;
import net.bowspleef.ranked.exception.DuelCancelAlreadyRequestedException;
import net.bowspleef.ranked.exception.DuelCancelNeedsAgreementException;
import net.bowspleef.ranked.exception.DuelChallengeForbiddenException;
import net.bowspleef.ranked.exception.DuelChallengeNotPendingException;
import net.bowspleef.ranked.exception.DuplicatePlayerInMatchException;
import net.bowspleef.ranked.exception.InvalidTeamSizeException;
import net.bowspleef.ranked.exception.NoActiveRankedSeasonException;
import net.bowspleef.ranked.exception.PlayerNotInPartyException;
import net.bowspleef.ranked.mapper.DuelChallengeMapper;
import net.bowspleef.ranked.mapper.DuelChallengeParticipantMapper;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.PartyMapper;
import net.bowspleef.ranked.mapper.PartyMemberMapper;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.model.DuelChallenge;
import net.bowspleef.ranked.model.DuelChallengeParticipant;
import net.bowspleef.ranked.model.GameType;
import net.bowspleef.ranked.model.Party;
import net.bowspleef.ranked.model.PartyMember;
import net.bowspleef.ranked.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
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
    @Mock
    private MatchWebhookClient matchWebhookClient;

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
        when(duelChallengeMapper.updateStatus(7L, "REPORTED", "CONFIRMING")).thenReturn(1);
        when(matchService.createMatch(any())).thenReturn(new MatchResponse(
                42L, "DUEL_2V2", "Duel 2v2", "discord_bot", OffsetDateTime.now(), List.of(), 1L, null));
        when(duelChallengeMapper.setMatchId(7L, 42L)).thenReturn(1);

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
    void confirmScores_claimLost_throwsAndSkipsCreateMatch() {
        DuelChallenge challenge = new DuelChallenge();
        challenge.setId(7L);
        challenge.setChallengerUuid(CHALLENGER);
        challenge.setChallengedUuid(CHALLENGED);
        challenge.setGameType("DUEL_1V1");
        challenge.setStatus("REPORTED");
        challenge.setReporterUuid(CHALLENGER);
        challenge.setScoreChallenger(5);
        challenge.setScoreChallenged(3);
        when(duelChallengeMapper.findById(7L)).thenReturn(Optional.of(challenge));
        stubPlayer("d-challenged", CHALLENGED);
        // Another confirm call already won the CAS (or the status moved on) between the read and the claim
        when(duelChallengeMapper.updateStatus(7L, "REPORTED", "CONFIRMING")).thenReturn(0);

        assertThatThrownBy(() -> duelChallengeService.confirmScores(7L, "d-challenged"))
                .isInstanceOf(DuelChallengeNotPendingException.class);
        verify(matchService, never()).createMatch(any());
        verify(duelChallengeMapper, never()).setMatchId(any(), any());
    }

    private DuelChallenge reportedDuelBeingConfirmed() {
        DuelChallenge challenge = new DuelChallenge();
        challenge.setId(7L);
        challenge.setChallengerUuid(CHALLENGER);
        challenge.setChallengedUuid(CHALLENGED);
        challenge.setGameType("DUEL_1V1");
        challenge.setStatus("REPORTED");
        challenge.setReporterUuid(CHALLENGER);
        challenge.setScoreChallenger(5);
        challenge.setScoreChallenged(3);
        when(duelChallengeMapper.findById(7L)).thenReturn(Optional.of(challenge));
        stubPlayer("d-challenged", CHALLENGED);
        when(duelChallengeMapper.updateStatus(7L, "REPORTED", "CONFIRMING")).thenReturn(1);
        return challenge;
    }

    @Test
    void confirmScores_createMatchThrows_revertsClaimToReported() {
        reportedDuelBeingConfirmed();
        when(matchService.createMatch(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> duelChallengeService.confirmScores(7L, "d-challenged"))
                .isInstanceOf(IllegalStateException.class);

        verify(duelChallengeMapper).updateStatus(7L, "CONFIRMING", "REPORTED");
        verify(duelChallengeMapper, never()).setMatchId(any(), any());
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void confirmScores_noActiveSeason_cancelsTheDuelAndTellsTheBot_REQ_003_11() {
        reportedDuelBeingConfirmed();
        when(matchService.createMatch(any())).thenThrow(new NoActiveRankedSeasonException());

        assertThatThrownBy(() -> duelChallengeService.confirmScores(7L, "d-challenged"))
                .isInstanceOf(NoActiveRankedSeasonException.class);

        verify(duelChallengeMapper).updateStatus(7L, "CONFIRMING", "CANCELLED");
        verify(duelChallengeMapper, never()).updateStatus(7L, "CONFIRMING", "REPORTED");
        verify(duelChallengeMapper, never()).setMatchId(any(), any());
        ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
        verify(matchWebhookClient, times(1)).notifyDuelCancelled(payload.capture());
        assertThat(payload.getValue().duelId()).isEqualTo(7L);
        assertThat(payload.getValue().reason()).isEqualTo("NO_ACTIVE_RANKED_SEASON");
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

    // ---- Cancellation by mutual agreement (spec 003) -----------------------------------------------

    private DuelChallenge duel(String status, UUID cancelRequestedBy) {
        DuelChallenge challenge = new DuelChallenge();
        challenge.setId(7L);
        challenge.setChallengerUuid(CHALLENGER);
        challenge.setChallengedUuid(CHALLENGED);
        challenge.setGameType("DUEL_1V1");
        challenge.setStatus(status);
        challenge.setCancelRequestedBy(cancelRequestedBy);
        when(duelChallengeMapper.findById(7L)).thenReturn(Optional.of(challenge));
        return challenge;
    }

    @Test
    void requestCancel_fromActiveAndReported_recordsTheRequester_REQ_003_01_REQ_003_08() {
        for (String status : List.of("ACTIVE", "REPORTED")) {
            DuelChallenge challenge = duel(status, null);
            stubPlayer("d-challenger", CHALLENGER);
            when(duelChallengeMapper.requestCancel(7L, CHALLENGER)).thenReturn(1);

            DuelChallengeResponse response = duelChallengeService.requestCancel(7L, "d-challenger");

            assertThat(response.cancelRequestedBy()).as(status).isEqualTo(CHALLENGER);
            assertThat(response.status()).isEqualTo(status);
            assertThat(challenge.getCancelRequestedBy()).isEqualTo(CHALLENGER);
        }
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void requestCancel_whileARequestWaits_isRefused_REQ_003_07() {
        duel("ACTIVE", CHALLENGED);
        stubPlayer("d-challenger", CHALLENGER);
        when(duelChallengeMapper.requestCancel(7L, CHALLENGER)).thenReturn(0);

        assertThatThrownBy(() -> duelChallengeService.requestCancel(7L, "d-challenger"))
                .isInstanceOf(DuelCancelAlreadyRequestedException.class);
    }

    @Test
    void requestCancel_whenNotCancellable_isNotPending_REQ_003_06() {
        for (String status : List.of("PENDING", "CONFIRMING", "COMPLETED", "CANCELLED", "REFUSED")) {
            duel(status, null);
            stubPlayer("d-challenger", CHALLENGER);
            when(duelChallengeMapper.requestCancel(7L, CHALLENGER)).thenReturn(0);

            assertThatThrownBy(() -> duelChallengeService.requestCancel(7L, "d-challenger"))
                    .as(status).isInstanceOf(DuelChallengeNotPendingException.class);
        }
    }

    @Test
    void requestCancel_byAnOutsider_isForbidden_REQ_003_04() {
        duel("ACTIVE", null);
        stubPlayer("d-outsider", UUID.randomUUID());

        assertThatThrownBy(() -> duelChallengeService.requestCancel(7L, "d-outsider"))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        verify(duelChallengeMapper, never()).requestCancel(any(), any());
    }

    @Test
    void acceptCancel_byTheOtherPlayer_cancelsAndNotifiesTheBotOnce_REQ_003_02_REQ_003_10() {
        duel("REPORTED", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(duelChallengeMapper.acceptCancel(7L, CHALLENGED)).thenReturn(1);

        DuelChallengeResponse response = duelChallengeService.acceptCancel(7L, "d-challenged");

        assertThat(response.status()).isEqualTo("CANCELLED");
        ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
        verify(matchWebhookClient, times(1)).notifyDuelCancelled(payload.capture());
        assertThat(payload.getValue().duelId()).isEqualTo(7L);
        assertThat(payload.getValue().reason()).isEqualTo("AGREEMENT");
    }

    @Test
    void acceptCancel_byTheRequester_isForbidden_REQ_003_04() {
        duel("ACTIVE", CHALLENGER);
        stubPlayer("d-challenger", CHALLENGER);

        assertThatThrownBy(() -> duelChallengeService.acceptCancel(7L, "d-challenger"))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        verify(duelChallengeMapper, never()).acceptCancel(any(), any());
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void acceptCancel_byAnOutsider_isForbidden_REQ_003_04() {
        duel("ACTIVE", CHALLENGER);
        stubPlayer("d-outsider", UUID.randomUUID());

        assertThatThrownBy(() -> duelChallengeService.acceptCancel(7L, "d-outsider"))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void acceptCancel_withNoRequest_isNotPendingAndSendsNothing_REQ_003_06() {
        duel("ACTIVE", null);
        stubPlayer("d-challenged", CHALLENGED);
        when(duelChallengeMapper.acceptCancel(7L, CHALLENGED)).thenReturn(0);

        assertThatThrownBy(() -> duelChallengeService.acceptCancel(7L, "d-challenged"))
                .isInstanceOf(DuelChallengeNotPendingException.class);
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void declineCancel_clearsTheRequestAndSendsNothing_REQ_003_03() {
        DuelChallenge challenge = duel("ACTIVE", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(duelChallengeMapper.declineCancel(7L, CHALLENGED)).thenReturn(1);

        DuelChallengeResponse response = duelChallengeService.declineCancel(7L, "d-challenged");

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.cancelRequestedBy()).isNull();
        assertThat(challenge.getCancelRequestedBy()).isNull();
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void declineCancel_byTheRequesterOrAnOutsider_isForbidden_REQ_003_04() {
        duel("ACTIVE", CHALLENGER);
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-outsider", UUID.randomUUID());

        assertThatThrownBy(() -> duelChallengeService.declineCancel(7L, "d-challenger"))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        assertThatThrownBy(() -> duelChallengeService.declineCancel(7L, "d-outsider"))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        verify(duelChallengeMapper, never()).declineCancel(any(), any());
    }

    @Test
    void declineCancel_withNoRequest_isNotPending_REQ_003_06() {
        duel("ACTIVE", null);
        stubPlayer("d-challenged", CHALLENGED);
        when(duelChallengeMapper.declineCancel(7L, CHALLENGED)).thenReturn(0);

        assertThatThrownBy(() -> duelChallengeService.declineCancel(7L, "d-challenged"))
                .isInstanceOf(DuelChallengeNotPendingException.class);
    }

    @Test
    void cancel_aPendingChallenge_stillCancelsAlone_REQ_003_09() {
        duel("PENDING", null);
        stubPlayer("d-challenger", CHALLENGER);
        when(duelChallengeMapper.cancel(7L)).thenReturn(1);

        assertThat(duelChallengeService.cancel(7L, "d-challenger").status()).isEqualTo("CANCELLED");
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void cancel_anActiveOrReportedDuel_needsAgreementAndChangesNothing_REQ_003_09() {
        for (String status : List.of("ACTIVE", "REPORTED")) {
            duel(status, null);
            stubPlayer("d-challenger", CHALLENGER);

            assertThatThrownBy(() -> duelChallengeService.cancel(7L, "d-challenger"))
                    .as(status).isInstanceOf(DuelCancelNeedsAgreementException.class);
        }
        verify(duelChallengeMapper, never()).cancel(any());
    }

    @Test
    void cancel_otherStatuses_areNotPending_REQ_003_09() {
        for (String status : List.of("COMPLETED", "CANCELLED", "REFUSED", "CONFIRMING")) {
            duel(status, null);
            stubPlayer("d-challenger", CHALLENGER);
            when(duelChallengeMapper.cancel(7L)).thenReturn(0);

            assertThatThrownBy(() -> duelChallengeService.cancel(7L, "d-challenger"))
                    .as(status).isInstanceOf(DuelChallengeNotPendingException.class);
        }
    }

    // ---- Abandoned duels (spec 006) ----------------------------------------------------------------

    @Test
    void expireStaleActiveDuels_cancelsAndTellsTheBotForEachOne_REQ_006_01() {
        ReflectionTestUtils.setField(duelChallengeService, "activeExpiryHours", 24);
        when(duelChallengeMapper.cancelStaleActive(24)).thenReturn(List.of(11L, 12L));

        int expired = duelChallengeService.expireStaleActiveDuels();

        assertThat(expired).isEqualTo(2);
        ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
        verify(matchWebhookClient, times(2)).notifyDuelCancelled(payload.capture());
        assertThat(payload.getAllValues()).extracting(DuelCancelledPayload::duelId).containsExactly(11L, 12L);
        assertThat(payload.getAllValues()).extracting(DuelCancelledPayload::reason).containsOnly("EXPIRED");
    }

    @Test
    void expireStaleActiveDuels_nothingStale_sendsNothing_REQ_006_01() {
        ReflectionTestUtils.setField(duelChallengeService, "activeExpiryHours", 24);
        when(duelChallengeMapper.cancelStaleActive(24)).thenReturn(List.of());

        assertThat(duelChallengeService.expireStaleActiveDuels()).isZero();
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void expireStaleActiveDuels_usesTheConfiguredDelay_REQ_006_02() {
        ReflectionTestUtils.setField(duelChallengeService, "activeExpiryHours", 3);
        when(duelChallengeMapper.cancelStaleActive(3)).thenReturn(List.of());

        duelChallengeService.expireStaleActiveDuels();

        verify(duelChallengeMapper).cancelStaleActive(3);
        verify(duelChallengeMapper, never()).cancelStaleActive(24);
    }

    @Test
    void cancelPlayerLeft_fromActiveAndReported_cancelsAndTellsTheBotOnce_REQ_006_08() {
        for (String status : List.of("ACTIVE", "REPORTED")) {
            clearInvocations(matchWebhookClient);
            duel(status, null);
            stubPlayer("d-challenged", CHALLENGED);
            when(duelChallengeMapper.cancelAbandoned(7L)).thenReturn(1);

            DuelChallengeResponse response = duelChallengeService.cancelPlayerLeft(7L, "d-challenged");

            assertThat(response.status()).as(status).isEqualTo("CANCELLED");
            ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
            verify(matchWebhookClient, times(1)).notifyDuelCancelled(payload.capture());
            assertThat(payload.getValue().duelId()).isEqualTo(7L);
            assertThat(payload.getValue().reason()).isEqualTo("PLAYER_LEFT");
        }
    }

    @Test
    void cancelPlayerLeft_byAnOutsider_isForbiddenAndChangesNothing_REQ_006_10() {
        duel("ACTIVE", null);
        stubPlayer("d-outsider", UUID.randomUUID());

        assertThatThrownBy(() -> duelChallengeService.cancelPlayerLeft(7L, "d-outsider"))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        verify(duelChallengeMapper, never()).cancelAbandoned(any());
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void cancelPlayerLeft_whenNotOpen_isNotPendingAndSendsNothing_REQ_006_10() {
        for (String status : List.of("PENDING", "CONFIRMING", "COMPLETED", "CANCELLED", "REFUSED")) {
            duel(status, null);
            stubPlayer("d-challenged", CHALLENGED);
            when(duelChallengeMapper.cancelAbandoned(7L)).thenReturn(0);

            assertThatThrownBy(() -> duelChallengeService.cancelPlayerLeft(7L, "d-challenged"))
                    .as(status).isInstanceOf(DuelChallengeNotPendingException.class);
        }
        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void create_whenTheSamePlayersAlreadyHaveAnOpenDuelOfThatGameType_isRefusedBeforeAnythingIsWritten_KI_09() {
        stubPlayer("d-challenger", CHALLENGER);
        stubPlayer("d-challenged", CHALLENGED);
        when(gameTypeMapper.findByName("DUEL_1V1")).thenReturn(Optional.of(gameType("DUEL_1V1", 1)));
        when(duelChallengeMapper.existsOpenBetween(CHALLENGER, CHALLENGED, "DUEL_1V1")).thenReturn(true);

        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(
                "d-challenger", "d-challenged", "DUEL_1V1")))
                .isInstanceOf(DuelAlreadyOpenException.class);
        verify(duelChallengeMapper, never()).insert(any());
        verifyNoInteractions(participantMapper);
    }
}
