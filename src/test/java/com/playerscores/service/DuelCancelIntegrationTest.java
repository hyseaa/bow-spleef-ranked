package com.playerscores.service;

import com.playerscores.AbstractIntegrationTest;
import com.playerscores.client.MatchWebhookClient;
import com.playerscores.dto.CreateDuelChallengeRequest;
import com.playerscores.dto.DuelCancelledPayload;
import com.playerscores.dto.ReportDuelScoresRequest;
import com.playerscores.exception.DuelCancelAlreadyRequestedException;
import com.playerscores.exception.DuelCancelNeedsAgreementException;
import com.playerscores.exception.NoActiveRankedSeasonException;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.GameType;
import com.playerscores.model.RankedSeason;
import com.playerscores.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Spec 003 against a real PostgreSQL: cancellation by agreement, and the cancellation on a missing season. */
class DuelCancelIntegrationTest extends AbstractIntegrationTest {

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
    private RankedSeasonMapper rankedSeasonMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String gameTypeName;
    private String challengerId;
    private String challengedId;

    /** A snowflake-like Discord ID (player.discord_id is VARCHAR(20)), unique across the shared database. */
    private static String discordId() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }

    private void gameType(boolean ranked) {
        gameTypeName = "CANCEL_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(gameTypeName);
        gameType.setDisplayName(gameTypeName);
        gameType.setRanked(ranked);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
    }

    private RankedSeason startSeason() {
        RankedSeason season = new RankedSeason();
        season.setName(gameTypeName);
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(gameTypeName);
        season.setActive(true);
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL);
        rankedSeasonMapper.insert(season);
        return season;
    }

    /** Two new verified players and an ACTIVE duel between them, or REPORTED (challenger reported 5-3). */
    private long duel(boolean reported) {
        challengerId = discordId();
        challengedId = discordId();
        UUID challenger = UUID.randomUUID();
        UUID challenged = UUID.randomUUID();
        playerMapper.insertIfAbsent(challenger);
        playerMapper.insertIfAbsent(challenged);
        playerMapper.updateDiscordId(challenger, challengerId);
        playerMapper.updateDiscordId(challenged, challengedId);
        long id = duelChallengeService.create(new CreateDuelChallengeRequest(challengerId, challengedId, gameTypeName)).id();
        duelChallengeService.accept(id, challengedId);
        if (reported) {
            duelChallengeService.reportScores(id, new ReportDuelScoresRequest(challengerId, 5, 3));
        }
        return id;
    }

    private String status(long id) {
        return jdbc.queryForObject("SELECT status FROM duel_challenge WHERE id = ?", String.class, id);
    }

    private UUID requester(long id) {
        return jdbc.queryForObject("SELECT cancel_requested_by FROM duel_challenge WHERE id = ?", UUID.class, id);
    }

    @Test
    void requestThenAcceptCancelsTheDuelAndTellsTheBotOnce_REQ_003_01_REQ_003_02_REQ_003_10() {
        gameType(false);
        long id = duel(false);

        var requested = duelChallengeService.requestCancel(id, challengerId);
        assertThat(requested.cancelRequestedBy()).isNotNull();
        assertThat(status(id)).isEqualTo("ACTIVE");
        verify(matchWebhookClient, never()).notifyDuelCancelled(any());

        duelChallengeService.acceptCancel(id, challengedId);

        assertThat(status(id)).isEqualTo("CANCELLED");
        ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
        verify(matchWebhookClient, times(1)).notifyDuelCancelled(payload.capture());
        assertThat(payload.getValue().duelId()).isEqualTo(id);
        assertThat(payload.getValue().reason()).isEqualTo("AGREEMENT");
    }

    @Test
    void requestThenDeclineKeepsTheDuelGoing_REQ_003_03() {
        gameType(false);
        long id = duel(false);

        duelChallengeService.requestCancel(id, challengerId);
        duelChallengeService.declineCancel(id, challengedId);

        assertThat(status(id)).isEqualTo("ACTIVE");
        assertThat(requester(id)).isNull();
        verify(matchWebhookClient, never()).notifyDuelCancelled(any());
        // and the request can be made again
        duelChallengeService.requestCancel(id, challengedId);
        assertThat(requester(id)).isNotNull();
    }

    @Test
    void aReportedDuelCanBeCancelledToo_REQ_003_08() {
        gameType(false);
        long id = duel(true);
        assertThat(status(id)).isEqualTo("REPORTED");

        duelChallengeService.requestCancel(id, challengedId);
        duelChallengeService.acceptCancel(id, challengerId);

        assertThat(status(id)).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAloneFromAnActiveDuelIsRefused_REQ_003_09() {
        gameType(false);
        long id = duel(false);

        assertThatThrownBy(() -> duelChallengeService.cancel(id, challengerId))
                .isInstanceOf(DuelCancelNeedsAgreementException.class);

        assertThat(status(id)).isEqualTo("ACTIVE");
    }

    @Test
    void aWaitingRequestDoesNotStopTheScoreFromBeingConfirmed_REQ_003_01() {
        gameType(false);
        long id = duel(true);
        duelChallengeService.requestCancel(id, challengerId);

        duelChallengeService.confirmScores(id, challengedId);

        assertThat(status(id)).isEqualTo("COMPLETED");
    }

    @Test
    void twoRequestsAtTheSameTimeRecordOneRequester_REQ_003_07() throws Exception {
        gameType(false);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long id = duel(false);
                String first = challengerId;
                String second = challengedId;
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (String who : List.of(first, second)) {
                    Callable<Boolean> task = () -> {
                        go.await();
                        try {
                            duelChallengeService.requestCancel(id, who);
                            return true;
                        } catch (DuelCancelAlreadyRequestedException e) {
                            return false;
                        }
                    };
                    futures.add(pool.submit(task));
                }
                go.countDown();
                boolean a = futures.get(0).get(30, TimeUnit.SECONDS);
                boolean b = futures.get(1).get(30, TimeUnit.SECONDS);

                assertThat(a ^ b).as("exactly one request wins, round " + round).isTrue();
                assertThat(requester(id)).isNotNull();
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void confirmingWithoutAnActiveSeasonCancelsTheDuelForGood_REQ_003_11() {
        gameType(true);
        RankedSeason season = startSeason();
        long id = duel(true);
        rankedSeasonMapper.deactivate(season.getId());

        assertThatThrownBy(() -> duelChallengeService.confirmScores(id, challengedId))
                .isInstanceOf(NoActiveRankedSeasonException.class);

        assertThat(status(id)).as("committed despite the exception").isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT match_id FROM duel_challenge WHERE id = ?", Long.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM match WHERE game_type = ?", Long.class, gameTypeName)).isZero();
        ArgumentCaptor<DuelCancelledPayload> payload = ArgumentCaptor.forClass(DuelCancelledPayload.class);
        verify(matchWebhookClient, times(1)).notifyDuelCancelled(payload.capture());
        assertThat(payload.getValue().duelId()).isEqualTo(id);
        assertThat(payload.getValue().reason()).isEqualTo("NO_ACTIVE_RANKED_SEASON");
    }
}
