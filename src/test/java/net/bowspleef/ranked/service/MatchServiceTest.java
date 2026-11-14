package net.bowspleef.ranked.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.MatchWebhookPayload;
import net.bowspleef.ranked.dto.PlayerEloSnapshot;
import net.bowspleef.ranked.dto.PlayerRankEntry;
import net.bowspleef.ranked.dto.MatchListResponse;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.exception.DuplicatePlayerInMatchException;
import net.bowspleef.ranked.exception.GameTypeNotFoundException;
import net.bowspleef.ranked.exception.IdempotencyKeyReusedException;
import net.bowspleef.ranked.exception.InvalidTeamSizeException;
import net.bowspleef.ranked.exception.MatchNotFoundException;
import net.bowspleef.ranked.mapper.EloMapper;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.MatchMapper;
import net.bowspleef.ranked.mapper.MatchPlayerStatMapper;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.mapper.RankedSeasonMapper;
import net.bowspleef.ranked.mapper.TeamMapper;
import net.bowspleef.ranked.mapper.TeamPlayerMapper;
import net.bowspleef.ranked.model.GameType;
import net.bowspleef.ranked.model.Match;
import net.bowspleef.ranked.model.Player;
import net.bowspleef.ranked.model.RankedSeason;
import net.bowspleef.ranked.model.Team;
import net.bowspleef.ranked.model.TeamPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchMapper matchMapper;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private TeamPlayerMapper teamPlayerMapper;
    @Mock
    private MatchPlayerStatMapper matchPlayerStatMapper;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private EloRecomputeService eloRecomputeService;
    @Mock
    private GameTypeMapper gameTypeMapper;
    @Mock
    private RankedSeasonMapper rankedSeasonMapper;
    @Mock
    private UsernameCache usernameCache;
    @Mock
    private MatchWebhookClient matchWebhookClient;
    @Mock
    private EloMapper eloMapper;
    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private MatchService matchService;

    @Test
    void createMatch_unknownPlayer_isInsertedAutomatically() {
        UUID uuid = UUID.randomUUID();

        doAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(1L);
            m.setPlayedAt(OffsetDateTime.now());
            return null;
        }).when(matchMapper).insert(any(Match.class));
        doAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(1L);
            return null;
        }).when(teamMapper).insert(any(Team.class));
        doAnswer(inv -> {
            TeamPlayer tp = inv.getArgument(0);
            tp.setId(1L);
            return null;
        }).when(teamPlayerMapper).insert(any(TeamPlayer.class));

        Match match = new Match();
        match.setId(1L);
        match.setGameType("BEDWARS");
        match.setSource("DISCORD_BOT");
        match.setPlayedAt(OffsetDateTime.now());
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));
        when(teamMapper.findByMatchId(1L)).thenReturn(List.of());

        GameType gameType = new GameType();
        gameType.setName("BEDWARS");
        gameType.setDisplayName("Bed Wars");
        gameType.setRanked(false);
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(gameType));

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(uuid))), null);

        MatchResponse response = matchService.createMatch(request);

        assertThat(response.gameType()).isEqualTo("BEDWARS");
    }

    @Test
    void createMatch_success_returnsResponse() {
        UUID uuid = UUID.randomUUID();

        doAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(1L);
            m.setPlayedAt(OffsetDateTime.now());
            return null;
        }).when(matchMapper).insert(any(Match.class));
        doAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(1L);
            return null;
        }).when(teamMapper).insert(any(Team.class));
        doAnswer(inv -> {
            TeamPlayer tp = inv.getArgument(0);
            tp.setId(1L);
            return null;
        }).when(teamPlayerMapper).insert(any(TeamPlayer.class));

        Match match = new Match();
        match.setId(1L);
        match.setGameType("BEDWARS");
        match.setSource("DISCORD_BOT");
        match.setPlayedAt(OffsetDateTime.now());
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));

        Team team = new Team();
        team.setId(1L);
        team.setScore(3);
        when(teamMapper.findByMatchId(1L)).thenReturn(List.of(team));
        TeamPlayer tp = new TeamPlayer();
        tp.setId(1L);
        tp.setTeamId(1L);
        tp.setPlayerUuid(uuid);
        when(teamPlayerMapper.findByTeamId(1L)).thenReturn(List.of(tp));
        when(matchPlayerStatMapper.findStatsByTeamPlayerId(1L)).thenReturn(Optional.empty());
        when(usernameCache.get(uuid)).thenReturn("Notch");

        GameType gameType = new GameType();
        gameType.setName("BEDWARS");
        gameType.setDisplayName("Bed Wars");
        gameType.setRanked(false);
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(gameType));

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(uuid)),
                        new TeamRequest(1, List.of())), null);

        MatchResponse response = matchService.createMatch(request);

        assertThat(response.gameType()).isEqualTo("BEDWARS");
        assertThat(response.teams()).hasSize(1);
        assertThat(response.teams().getFirst().players()).hasSize(1);
    }

    @Test
    void createMatch_ranked_wrongTeamSize_throwsInvalidTeamSize() {
        GameType gameType = new GameType();
        gameType.setName("DUEL_2V2");
        gameType.setDisplayName("Duel 2v2");
        gameType.setRanked(true);
        gameType.setTeamSize(2);
        when(gameTypeMapper.findByName("DUEL_2V2")).thenReturn(Optional.of(gameType));

        CreateMatchRequest request = new CreateMatchRequest("DUEL_2V2", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(UUID.randomUUID(), UUID.randomUUID())),
                        new TeamRequest(1, List.of(UUID.randomUUID()))), null);

        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(InvalidTeamSizeException.class);
        verify(playerMapper, never()).insertIfAbsent(any());
        verify(matchMapper, never()).insert(any(Match.class));
    }

    @Test
    void createMatch_duplicatePlayerAcrossTeams_throwsDuplicatePlayer() {
        UUID duplicated = UUID.randomUUID();

        GameType gameType = new GameType();
        gameType.setName("BEDWARS");
        gameType.setDisplayName("Bed Wars");
        gameType.setRanked(false);
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(gameType));

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(duplicated)),
                        new TeamRequest(1, List.of(duplicated))), null);

        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(DuplicatePlayerInMatchException.class);
        verify(matchMapper, never()).insert(any(Match.class));
    }

    @Test
    void createMatch_unranked_teamSizeNotEnforced() {
        // Casual game types accept arbitrary team compositions: team_size
        // is only enforced for ranked game types.
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        doAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(1L);
            m.setPlayedAt(OffsetDateTime.now());
            return null;
        }).when(matchMapper).insert(any(Match.class));
        doAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(1L);
            return null;
        }).when(teamMapper).insert(any(Team.class));
        doAnswer(inv -> {
            TeamPlayer tp = inv.getArgument(0);
            tp.setId(1L);
            return null;
        }).when(teamPlayerMapper).insert(any(TeamPlayer.class));

        Match match = new Match();
        match.setId(1L);
        match.setGameType("BEDWARS");
        match.setSource("DISCORD_BOT");
        match.setPlayedAt(OffsetDateTime.now());
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));
        when(teamMapper.findByMatchId(1L)).thenReturn(List.of());

        GameType gameType = new GameType();
        gameType.setName("BEDWARS");
        gameType.setDisplayName("Bed Wars");
        gameType.setRanked(false);
        gameType.setTeamSize(1);
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(gameType));

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(p1, p2)),
                        new TeamRequest(1, List.of(p3))), null);

        MatchResponse response = matchService.createMatch(request);

        assertThat(response.gameType()).isEqualTo("BEDWARS");
    }

    @Test
    void getMatch_notFound_throwsException() {
        when(matchMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getMatch(99L))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void getMatchesByGameType_returnsPage() {
        GameType gameType = new GameType();
        gameType.setName("BEDWARS");
        gameType.setDisplayName("Bed Wars");
        gameType.setRanked(false);
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(gameType));

        Match match = new Match();
        match.setId(1L);
        match.setGameType("BEDWARS");
        match.setSource("DISCORD_BOT");
        match.setPlayedAt(OffsetDateTime.now());
        when(matchMapper.countByGameType("BEDWARS")).thenReturn(1L);
        when(matchMapper.findByGameType("BEDWARS", 20, 0)).thenReturn(List.of(match));
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));
        when(teamMapper.findByMatchId(1L)).thenReturn(List.of());

        MatchListResponse response = matchService.getMatchesByGameType("BEDWARS", 0, 20);

        assertThat(response.gameType()).isEqualTo("BEDWARS");
        assertThat(response.gameTypeDisplayName()).isEqualTo("Bed Wars");
        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void getMatchesByGameType_unknownGameType_throwsException() {
        when(gameTypeMapper.findByName("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getMatchesByGameType("UNKNOWN", 0, 20))
                .isInstanceOf(GameTypeNotFoundException.class);
    }

    @Test
    void deleteMatch_ranked_marksDirty() {
        Match match = new Match();
        match.setId(1L);
        match.setGameType("BEDWARS");
        match.setRankedSeasonId(10L);
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));

        matchService.deleteMatch(1L);

        verify(matchMapper).deleteById(1L);
        verify(rankedSeasonMapper).markEloDirty(10L);
    }

    @Test
    void deleteMatch_casual_doesNotMarkDirty() {
        Match match = new Match();
        match.setId(2L);
        match.setGameType("BEDWARS");
        match.setRankedSeasonId(null);
        when(matchMapper.findById(2L)).thenReturn(Optional.of(match));

        matchService.deleteMatch(2L);

        verify(matchMapper).deleteById(2L);
        verify(rankedSeasonMapper, never()).markEloDirty(any());
    }

    @Test
    void deleteMatch_notFound_throwsException() {
        when(matchMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.deleteMatch(99L))
                .isInstanceOf(MatchNotFoundException.class);
    }

    // ---- Idempotency (spec 002) -------------------------------------------------------------------

    private static final String SOURCE = "hysea-server";
    private static final String KEY = "game-1";

    private GameType casual() {
        GameType gameType = new GameType();
        gameType.setName("BEDWARS");
        gameType.setDisplayName("Bed Wars");
        gameType.setRanked(false);
        return gameType;
    }

    /** Stubs the stored match 7 (key {@link #KEY}) with two teams: (score 3, [a]) and (score 1, [b]). */
    private void storedMatch(String gameType, UUID a, UUID b) {
        Match stored = new Match();
        stored.setId(7L);
        stored.setGameType(gameType);
        stored.setSource(SOURCE);
        stored.setPlayedAt(OffsetDateTime.now());
        stored.setIdempotencyKey(KEY);
        when(matchMapper.findByIdempotencyKey(SOURCE, KEY)).thenReturn(Optional.of(stored));
        when(matchMapper.findById(7L)).thenReturn(Optional.of(stored));
        Team t1 = new Team();
        t1.setId(71L);
        t1.setScore(3);
        Team t2 = new Team();
        t2.setId(72L);
        t2.setScore(1);
        when(teamMapper.findByMatchId(7L)).thenReturn(List.of(t1, t2));
        when(teamPlayerMapper.findByTeamId(71L)).thenReturn(List.of(teamPlayer(711L, 71L, a)));
        when(teamPlayerMapper.findByTeamId(72L)).thenReturn(List.of(teamPlayer(721L, 72L, b)));
    }

    private TeamPlayer teamPlayer(long id, long teamId, UUID uuid) {
        TeamPlayer tp = new TeamPlayer();
        tp.setId(id);
        tp.setTeamId(teamId);
        tp.setPlayerUuid(uuid);
        return tp;
    }

    private void assertNothingWritten() {
        verify(matchMapper, never()).insert(any(Match.class));
        verifyNoInteractions(eloRecomputeService, matchWebhookClient, playerMapper, rankedSeasonMapper);
    }

    /** Stubs the insert of a new match with the given id and the reads that follow it. */
    private void newMatchWithId(long id) {
        doAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(id);
            return null;
        }).when(matchMapper).insert(any(Match.class));
        Match created = new Match();
        created.setId(id);
        created.setGameType("BEDWARS");
        when(matchMapper.findById(id)).thenReturn(Optional.of(created));
    }

    @Test
    void recordMatch_newKey_storesTheKeyAndCreates_REQ_002_01() {
        UUID uuid = UUID.randomUUID();
        when(matchMapper.findByIdempotencyKey(SOURCE, KEY)).thenReturn(Optional.empty());
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(casual()));
        newMatchWithId(1L);

        MatchRecording recording = matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(uuid)), new TeamRequest(1, List.of())), null), KEY);

        assertThat(recording.created()).isTrue();
        assertThat(recording.match().id()).isEqualTo(1L);
        ArgumentCaptor<Match> inserted = ArgumentCaptor.forClass(Match.class);
        InOrder order = inOrder(matchMapper);
        order.verify(matchMapper).lockIdempotencyKey(SOURCE, KEY);
        order.verify(matchMapper).findByIdempotencyKey(SOURCE, KEY);
        order.verify(matchMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getIdempotencyKey()).isEqualTo(KEY);
    }

    @Test
    void recordMatch_modifiers_areStoredAsJson_REQ_010_08() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(casual()));
        newMatchWithId(1L);

        matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(uuid)), new TeamRequest(1, List.of())), null,
                java.util.Map.of("no-collision", true, "perks", 20)), null);

        ArgumentCaptor<Match> inserted = ArgumentCaptor.forClass(Match.class);
        verify(matchMapper).insert(inserted.capture());
        assertThat(new ObjectMapper().readTree(inserted.getValue().getModifiers()))
                .isEqualTo(new ObjectMapper().readTree("{\"no-collision\":true,\"perks\":20}"));
    }

    @Test
    void recordMatch_noModifiers_storeNone_REQ_010_09() {
        UUID uuid = UUID.randomUUID();
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(casual()));
        newMatchWithId(1L);

        matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(uuid)), new TeamRequest(1, List.of())), null), null);

        ArgumentCaptor<Match> inserted = ArgumentCaptor.forClass(Match.class);
        verify(matchMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getModifiers()).isNull();
    }

    @Test
    void recordMatch_sameKeySameContent_returnsTheExistingMatchWithoutSideEffect_REQ_002_02_REQ_002_03() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        storedMatch("BEDWARS", a, b);

        MatchRecording recording = matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(a)), new TeamRequest(1, List.of(b))), null), KEY);

        assertThat(recording.created()).isFalse();
        assertThat(recording.match().id()).isEqualTo(7L);
        assertThat(recording.match().teams()).hasSize(2);
        assertNothingWritten();
        verify(matchMapper).lockIdempotencyKey(SOURCE, KEY);
    }

    @Test
    void recordMatch_teamsInAnotherOrderAndOtherStats_isTheSameMatch_REQ_002_10() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        storedMatch("BEDWARS", a, b);

        MatchRecording recording = matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(1, List.of(b)), new TeamRequest(3, List.of(a))),
                Map.of(a, Map.<String, Object>of("kills", 99))), KEY);

        assertThat(recording.created()).isFalse();
        assertNothingWritten();
    }

    @Test
    void recordMatch_sameKeyDifferentScore_isRefused_REQ_002_10() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        storedMatch("BEDWARS", a, b);

        assertThatThrownBy(() -> matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(a)), new TeamRequest(2, List.of(b))), null), KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertNothingWritten();
    }

    @Test
    void recordMatch_sameKeyDifferentPlayer_isRefused_REQ_002_10() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        storedMatch("BEDWARS", a, b);

        assertThatThrownBy(() -> matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(a)), new TeamRequest(1, List.of(UUID.randomUUID()))), null), KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertNothingWritten();
    }

    @Test
    void recordMatch_sameKeyDifferentGameType_isRefused_REQ_002_10() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        storedMatch("BEDWARS", a, b);

        assertThatThrownBy(() -> matchService.recordMatch(new CreateMatchRequest("SKYWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(a)), new TeamRequest(1, List.of(b))), null), KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertNothingWritten();
    }

    @Test
    void recordMatch_sameKeyInAnotherSource_isANewMatch_REQ_002_07() {
        UUID uuid = UUID.randomUUID();
        when(matchMapper.findByIdempotencyKey("other", KEY)).thenReturn(Optional.empty());
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(casual()));
        newMatchWithId(2L);

        MatchRecording recording = matchService.recordMatch(new CreateMatchRequest("BEDWARS", "other",
                List.of(new TeamRequest(3, List.of(uuid)), new TeamRequest(1, List.of())), null), KEY);

        assertThat(recording.created()).isTrue();
        verify(matchMapper).lockIdempotencyKey("other", KEY);
    }

    @Test
    void recordMatch_noKey_skipsTheLockAndTheLookup_REQ_002_06() {
        UUID uuid = UUID.randomUUID();
        when(gameTypeMapper.findByName("BEDWARS")).thenReturn(Optional.of(casual()));
        newMatchWithId(3L);

        MatchRecording recording = matchService.recordMatch(new CreateMatchRequest("BEDWARS", SOURCE,
                List.of(new TeamRequest(3, List.of(uuid)), new TeamRequest(1, List.of())), null), null);

        assertThat(recording.created()).isTrue();
        verify(matchMapper, never()).lockIdempotencyKey(any(), any());
        verify(matchMapper, never()).findByIdempotencyKey(any(), any());
        ArgumentCaptor<Match> inserted = ArgumentCaptor.forClass(Match.class);
        verify(matchMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getIdempotencyKey()).isNull();
    }

    // ---- KI-06: titles are percentiles, so a ranked match can change other players' titles ----

    private static PlayerEloSnapshot title(UUID uuid, String rankTitle) {
        PlayerEloSnapshot snapshot = new PlayerEloSnapshot();
        snapshot.setPlayerUuid(uuid);
        snapshot.setRankTitle(rankTitle);
        return snapshot;
    }

    private static Player verified(UUID uuid, String discordId) {
        Player player = new Player();
        player.setUuid(uuid);
        player.setDiscordId(discordId);
        return player;
    }

    @Test
    void createMatch_ranked_theWebhookAlsoCarriesTheTitlesThatChangedForOtherPlayers_KI_06() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID bystanderChanged = UUID.randomUUID();
        UUID bystanderSame = UUID.randomUUID();
        GameType gameType = new GameType();
        gameType.setName("DUEL");
        gameType.setDisplayName("Duel");
        gameType.setRanked(true);
        gameType.setTeamSize(1);
        when(gameTypeMapper.findByName("DUEL")).thenReturn(Optional.of(gameType));
        RankedSeason season = new RankedSeason();
        season.setId(4L);
        when(rankedSeasonMapper.lockActiveByGameType("DUEL")).thenReturn(Optional.of(season));
        doAnswer(inv -> {
            inv.getArgument(0, Match.class).setId(9L);
            return null;
        }).when(matchMapper).insert(any(Match.class));
        doAnswer(inv -> {
            inv.getArgument(0, Team.class).setId(100L + inv.getArgument(0, Team.class).getScore());
            return null;
        }).when(teamMapper).insert(any(Team.class));
        doAnswer(inv -> {
            inv.getArgument(0, TeamPlayer.class).setId(1L);
            return null;
        }).when(teamPlayerMapper).insert(any(TeamPlayer.class));
        Match stored = new Match();
        stored.setId(9L);
        stored.setGameType("DUEL");
        stored.setRankedSeasonId(4L);
        when(matchMapper.findById(9L)).thenReturn(Optional.of(stored));
        // the season before the match, then after it: the bystander drops from Silver to Bronze, the other stays Gold
        when(eloMapper.findAllEloBySeasonId(4L)).thenReturn(
                List.of(title(a, "Gold"), title(b, "Silver"), title(bystanderChanged, "Silver"), title(bystanderSame, "Gold")),
                List.of(title(a, "Platinum"), title(b, "Bronze"), title(bystanderChanged, "Bronze"), title(bystanderSame, "Gold")));
        // like the database, answer only for the players asked about
        List<Player> everyone = List.of(
                verified(a, "d-a"), verified(b, "d-b"), verified(bystanderChanged, "d-c"), verified(bystanderSame, "d-d"));
        when(playerMapper.findByUuids(any())).thenAnswer(inv -> {
            java.util.Collection<UUID> asked = inv.getArgument(0);
            return everyone.stream().filter(p -> asked.contains(p.getUuid())).toList();
        });
        List<PlayerEloSnapshot> after = List.of(
                title(a, "Platinum"), title(b, "Bronze"), title(bystanderChanged, "Bronze"), title(bystanderSame, "Gold"));
        when(eloMapper.findEloByUuidsAndSeason(any(), org.mockito.ArgumentMatchers.eq(4L))).thenAnswer(inv -> {
            java.util.Collection<UUID> asked = inv.getArgument(0);
            return after.stream().filter(e -> asked.contains(e.getPlayerUuid())).toList();
        });

        matchService.createMatch(new CreateMatchRequest("DUEL", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(a)), new TeamRequest(1, List.of(b))), null));

        ArgumentCaptor<MatchWebhookPayload> payload = ArgumentCaptor.forClass(MatchWebhookPayload.class);
        verify(matchWebhookClient).notifyMatchResult(payload.capture());
        assertThat(payload.getValue().getPlayerRanks()).extracting(PlayerRankEntry::discordId)
                .as("the match's players and the bystander whose title changed, not the one whose title did not")
                .containsExactlyInAnyOrder("d-a", "d-b", "d-c");
    }
}
