package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.PlayerSummaryResponse;
import net.bowspleef.ranked.dto.ReplayAddedPayload;
import net.bowspleef.ranked.dto.ReplayFile;
import net.bowspleef.ranked.dto.ReplaySummaryResponse;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.exception.MatchNotFoundException;
import net.bowspleef.ranked.exception.ReplayAlreadyExistsException;
import net.bowspleef.ranked.exception.ReplayNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 024, ADR-0026: the replays against a real PostgreSQL (V32). */
class ReplayIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private MatchService matchService;
    @Autowired
    private ReplayService replayService;
    @Autowired
    private JdbcTemplate jdbc;

    private String gameType;

    @BeforeEach
    void setUp() {
        gameType = "replay_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO game_types (name, display_name, ranked) VALUES (?, ?, false)", gameType, gameType);
        when(usernameCache.get(any())).thenAnswer(call -> "name-" + call.getArgument(0).toString().substring(0, 4));
        reset(matchWebhookClient);
    }

    private long match(UUID a, UUID b) {
        return matchService.createMatch(new CreateMatchRequest(gameType, "hysea-server",
                List.of(new TeamRequest(1, List.of(a)), new TeamRequest(0, List.of(b))), null)).id();
    }

    private void cacheName(UUID uuid, String name) {
        jdbc.update("UPDATE player SET username = ?, username_cached_at = NOW() WHERE uuid = ?", name, uuid);
    }

    @Test
    void aReplayIsStoredAndReadBackAsItCame_REQ_024_03() {
        long id = match(UUID.randomUUID(), UUID.randomUUID());
        byte[] bytes = {31, -117, 8, 0, 42};

        assertThat(replayService.store(id, "dread-pit", 690, 1, bytes)).isTrue();

        assertThat(replayService.read(id)).satisfies(file -> {
            assertThat(file.map()).isEqualTo("dread-pit");
            assertThat(file.durationTicks()).isEqualTo(690);
            assertThat(file.formatVersion()).isEqualTo(1);
            assertThat(file.data()).isEqualTo(bytes);
        });
        verify(matchWebhookClient).notifyReplayAdded(new ReplayAddedPayload(id));
    }

    @Test
    void theSameBytesAgainChangeNothing_otherBytesAreRefused_REQ_024_14() {
        long id = match(UUID.randomUUID(), UUID.randomUUID());
        byte[] bytes = {1, 2, 3};
        replayService.store(id, "dread-pit", 690, 1, bytes);

        assertThat(replayService.store(id, "dread-pit", 690, 1, bytes.clone())).isFalse();
        assertThatThrownBy(() -> replayService.store(id, "dread-pit", 690, 1, new byte[]{9}))
                .isInstanceOf(ReplayAlreadyExistsException.class);
        assertThat(replayService.read(id).data()).isEqualTo(bytes);
        verify(matchWebhookClient, times(1)).notifyReplayAdded(any());
    }

    @Test
    void aMatchThatDoesNotExistGetsNoReplay_REQ_024_14() {
        assertThatThrownBy(() -> replayService.store(Long.MAX_VALUE, "dread-pit", 690, 1, new byte[]{1}))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void deletingTheMatchDeletesItsReplay_REQ_024_03() {
        long id = match(UUID.randomUUID(), UUID.randomUUID());
        replayService.store(id, "dread-pit", 690, 1, new byte[]{1});

        matchService.deleteMatch(id);

        assertThatThrownBy(() -> replayService.read(id)).isInstanceOf(ReplayNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM match_replay WHERE match_id = ?", Long.class, id))
                .isZero();
    }

    @Test
    void theListIsNewestFirstWithThePlayersAndAnyMatch_REQ_024_04_REQ_024_15() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        long first = match(alice, bob);
        long second = match(bob, alice);
        long withoutReplay = match(alice, bob);
        replayService.store(first, "dread-pit", 100, 1, new byte[]{1});
        replayService.store(second, "chamber", 200, 1, new byte[]{2});

        List<ReplaySummaryResponse> latest = replayService.latest(null, 50);

        List<Long> ids = latest.stream().map(ReplaySummaryResponse::matchId).toList();
        assertThat(ids).containsSubsequence(second, first).doesNotContain(withoutReplay);
        ReplaySummaryResponse top = latest.stream().filter(r -> r.matchId() == second).findFirst().orElseThrow();
        assertThat(top.map()).isEqualTo("chamber");
        assertThat(top.durationTicks()).isEqualTo(200);
        assertThat(top.gameType()).isEqualTo(gameType);
        assertThat(top.playedAt()).isNotNull();
        assertThat(top.players()).extracting(PlayerSummaryResponse::uuid).containsExactly(bob, alice);
        assertThat(replayService.latest(null, 1)).hasSize(1);
    }

    @Test
    void theListOfAPlayerKeepsTheirMatches_caseInsensitive_REQ_024_20() {
        UUID carol = UUID.randomUUID();
        UUID dave = UUID.randomUUID();
        UUID erin = UUID.randomUUID();
        long carolsMatch = match(carol, dave);
        long othersMatch = match(dave, erin);
        replayService.store(carolsMatch, "dread-pit", 100, 1, new byte[]{1});
        replayService.store(othersMatch, "dread-pit", 100, 1, new byte[]{2});
        String name = "C" + UUID.randomUUID().toString().substring(0, 8);
        cacheName(carol, name);

        assertThat(replayService.latest(name.toLowerCase(), 10)).extracting(ReplaySummaryResponse::matchId)
                .containsExactly(carolsMatch);
        assertThat(replayService.latest("nobody-" + UUID.randomUUID().toString().substring(0, 6), 10)).isEmpty();
    }

    @Test
    void theFileIsNeverPartOfTheList_REQ_024_04() {
        long id = match(UUID.randomUUID(), UUID.randomUUID());
        replayService.store(id, "dread-pit", 100, 1, new byte[]{1});

        ReplayFile file = replayService.read(id);
        assertThat(file.data()).hasSize(1);
        assertThat(ReplaySummaryResponse.class.getRecordComponents())
                .extracting(c -> c.getName()).doesNotContain("data");
    }
}
