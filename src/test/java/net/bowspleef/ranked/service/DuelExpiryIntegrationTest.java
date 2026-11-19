package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateDuelChallengeRequest;
import net.bowspleef.ranked.dto.DuelCancelledPayload;
import net.bowspleef.ranked.dto.ReportDuelScoresRequest;
import net.bowspleef.ranked.exception.DuelChallengeForbiddenException;
import net.bowspleef.ranked.exception.DuelChallengeNotPendingException;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.model.GameType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Spec 006 against a real PostgreSQL: the clock of an ACTIVE duel, its expiry, and the departure of a player. */
class DuelExpiryIntegrationTest extends AbstractIntegrationTest {

    private static final int ROUNDS = 12;

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private DuelChallengeService duelChallengeService;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String gameTypeName;
    private String challengerId;
    private String challengedId;

    private static String discordId() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }

    private void nonRankedGameType() {
        gameTypeName = "EXPIRY_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(gameTypeName);
        gameType.setDisplayName(gameTypeName);
        gameType.setRanked(false);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
    }

    /** Two new verified players and a duel between them, accepted, and reported 4-3 when asked. */
    private long duel(boolean reported) {
        challengerId = discordId();
        challengedId = discordId();
        for (String id : new String[] {challengerId, challengedId}) {
            UUID uuid = UUID.randomUUID();
            playerMapper.insertIfAbsent(uuid);
            playerMapper.updateDiscordId(uuid, id);
        }
        long id = duelChallengeService.create(new CreateDuelChallengeRequest(challengerId, challengedId, gameTypeName)).id();
        duelChallengeService.accept(id, challengedId);
        if (reported) {
            duelChallengeService.reportScores(id, new ReportDuelScoresRequest(challengerId, 4, 3));
        }
        return id;
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM duel_challenge WHERE id = ?", String.class, id);
    }

    private boolean hasActiveSince(long id) {
        return jdbc.queryForObject("SELECT active_since IS NOT NULL FROM duel_challenge WHERE id = ?", Boolean.class, id);
    }

    private void setBackActiveSince(long id, int hours) {
        jdbc.update("UPDATE duel_challenge SET active_since = NOW() - make_interval(hours => ?) WHERE id = ?", hours, id);
    }

    private boolean activeSinceIsRecent(long id) {
        return jdbc.queryForObject("SELECT active_since > NOW() - INTERVAL '5 minutes' FROM duel_challenge WHERE id = ?",
                Boolean.class, id);
    }

    @Test
    void acceptingADuelStartsItsClock_REQ_006_03() {
        nonRankedGameType();
        long id = duel(false);

        assertThat(hasActiveSince(id)).isTrue();
        assertThat(activeSinceIsRecent(id)).isTrue();
    }

    @Test
    void disputingAScoreRestartsTheClock_REQ_006_03() {
        nonRankedGameType();
        long id = duel(true);
        setBackActiveSince(id, 30);
        assertThat(activeSinceIsRecent(id)).isFalse();

        duelChallengeService.disputeScores(id, challengedId);

        assertThat(status(id)).isEqualTo("ACTIVE");
        assertThat(activeSinceIsRecent(id)).as("a dispute gives the duel a fresh 24 hours").isTrue();
    }

    @Test
    void reportingAndConfirmingDoNotTouchTheClock_REQ_006_03() {
        nonRankedGameType();
        long id = duel(false);
        setBackActiveSince(id, 10);

        duelChallengeService.reportScores(id, new ReportDuelScoresRequest(challengerId, 4, 3));

        assertThat(activeSinceIsRecent(id)).isFalse();
    }

    @Test
    void anActiveDuelNobodyPlaysIsCancelledAndTheBotIsToldOnce_REQ_006_01() {
        nonRankedGameType();
        long stale = duel(false);
        long recent = duel(false);
        setBackActiveSince(stale, 25);

        int expired = duelChallengeService.expireStaleActiveDuels();

        assertThat(expired).isGreaterThanOrEqualTo(1);
        assertThat(status(stale)).isEqualTo("CANCELLED");
        assertThat(status(recent)).as("a recent duel is untouched").isEqualTo("ACTIVE");
        ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
        verify(matchWebhookClient, atLeastOnce()).notifyDuelCancelled(payload.capture());
        assertThat(payload.getAllValues()).filteredOn(p -> p.duelId().equals(stale))
                .singleElement().extracting(DuelCancelledPayload::reason).isEqualTo("EXPIRED");
        assertThat(payload.getAllValues()).filteredOn(p -> p.duelId().equals(recent)).isEmpty();
    }

    @Test
    void aSecondPassDoesNotCancelTwice_REQ_006_01() {
        nonRankedGameType();
        long stale = duel(false);
        setBackActiveSince(stale, 25);
        duelChallengeService.expireStaleActiveDuels();
        clearInvocations(matchWebhookClient);

        duelChallengeService.expireStaleActiveDuels();

        verify(matchWebhookClient, never()).notifyDuelCancelled(any());
    }

    @Test
    void aReportedDuelNeverExpires_REQ_006_04() {
        nonRankedGameType();
        long id = duel(true);
        setBackActiveSince(id, 100);

        duelChallengeService.expireStaleActiveDuels();

        assertThat(status(id)).isEqualTo("REPORTED");
    }

    @Test
    void anExpiryWritesNoMatchAndNoRating_REQ_006_05() {
        nonRankedGameType();
        long id = duel(false);
        setBackActiveSince(id, 25);
        long matchesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM match WHERE game_type = ?", Long.class, gameTypeName);

        duelChallengeService.expireStaleActiveDuels();

        assertThat(status(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM match WHERE game_type = ?", Long.class, gameTypeName))
                .isEqualTo(matchesBefore);
        assertThat(jdbc.queryForObject("SELECT match_id FROM duel_challenge WHERE id = ?", Long.class, id)).isNull();
    }

    @Test
    void aDuelDisputedJustBeforeIsNotExpired_REQ_006_03() {
        nonRankedGameType();
        long id = duel(true);
        setBackActiveSince(id, 30);
        duelChallengeService.disputeScores(id, challengedId);

        duelChallengeService.expireStaleActiveDuels();

        assertThat(status(id)).isEqualTo("ACTIVE");
    }

    @Test
    void aReportAndTheExpiryAtTheSameTimeHaveExactlyOneWinner_REQ_006_06() throws Exception {
        nonRankedGameType();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long id = duel(false);
                setBackActiveSince(id, 25);
                CountDownLatch go = new CountDownLatch(1);
                Callable<Boolean> report = () -> {
                    go.await();
                    try {
                        duelChallengeService.reportScores(id, new ReportDuelScoresRequest(challengerId, 4, 3));
                        return true;
                    } catch (DuelChallengeNotPendingException e) {
                        return false;
                    }
                };
                Callable<Boolean> expiry = () -> {
                    go.await();
                    return duelChallengeService.expireStaleActiveDuels() > 0;
                };
                Future<Boolean> reportResult = pool.submit(report);
                Future<Boolean> expiryResult = pool.submit(expiry);
                go.countDown();
                boolean reported = reportResult.get(30, TimeUnit.SECONDS);
                expiryResult.get(30, TimeUnit.SECONDS);

                String finalStatus = status(id);
                assertThat(finalStatus).as("round " + round).isIn("REPORTED", "CANCELLED");
                assertThat(reported).as("the report succeeded exactly when the duel is REPORTED, round " + round)
                        .isEqualTo("REPORTED".equals(finalStatus));
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void aPlayerWhoLeftCancelsAnActiveOrReportedDuelWithNoResult_REQ_006_08() {
        nonRankedGameType();
        for (boolean reported : new boolean[] {false, true}) {
            clearInvocations(matchWebhookClient);
            long id = duel(reported);

            duelChallengeService.cancelPlayerLeft(id, challengedId);

            assertThat(status(id)).as("reported=" + reported).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT match_id FROM duel_challenge WHERE id = ?", Long.class, id)).isNull();
            ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
            verify(matchWebhookClient, times(1)).notifyDuelCancelled(payload.capture());
            assertThat(payload.getValue().duelId()).isEqualTo(id);
            assertThat(payload.getValue().reason()).isEqualTo("PLAYER_LEFT");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM match WHERE game_type = ?", Long.class, gameTypeName)).isZero();
    }

    @Test
    void anOutsiderAndAClosedDuelAreRefused_REQ_006_10() {
        nonRankedGameType();
        long id = duel(false);
        UUID outsider = UUID.randomUUID();
        String outsiderId = discordId();
        playerMapper.insertIfAbsent(outsider);
        playerMapper.updateDiscordId(outsider, outsiderId);

        assertThatThrownBy(() -> duelChallengeService.cancelPlayerLeft(id, outsiderId))
                .isInstanceOf(DuelChallengeForbiddenException.class);
        assertThat(status(id)).isEqualTo("ACTIVE");

        duelChallengeService.cancelPlayerLeft(id, challengerId);
        assertThatThrownBy(() -> duelChallengeService.cancelPlayerLeft(id, challengedId))
                .isInstanceOf(DuelChallengeNotPendingException.class);
    }
}
