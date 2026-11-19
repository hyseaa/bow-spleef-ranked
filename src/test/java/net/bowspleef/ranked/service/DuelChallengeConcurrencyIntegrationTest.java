package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.dto.CreateDuelChallengeRequest;
import net.bowspleef.ranked.dto.DuelChallengeResponse;
import net.bowspleef.ranked.dto.ReportDuelScoresRequest;
import net.bowspleef.ranked.exception.DuelChallengeNotPendingException;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.MatchMapper;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.model.GameType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the fix for the confirmScores race under real concurrency: two threads confirming the
 * same duel challenge at the same time against a real (Testcontainers) Postgres instance must
 * result in exactly one Match row, not two. A pure-mock unit test cannot exercise the actual
 * database-level compare-and-swap this relies on, hence a dedicated integration test.
 */
class DuelChallengeConcurrencyIntegrationTest extends AbstractIntegrationTest {

    // Discord snowflake-like IDs: player.discord_id is VARCHAR(20)
    private static final String CHALLENGER_DISCORD_ID = "100000000000000001";
    private static final String CHALLENGED_DISCORD_ID = "100000000000000002";

    @Autowired
    private DuelChallengeService duelChallengeService;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private MatchMapper matchMapper;

    @Test
    void confirmScores_calledConcurrentlyTwice_onlyOneMatchIsCreated() throws Exception {
        UUID challengerUuid = UUID.randomUUID();
        UUID challengedUuid = UUID.randomUUID();
        playerMapper.insertIfAbsent(challengerUuid);
        playerMapper.insertIfAbsent(challengedUuid);
        playerMapper.updateDiscordId(challengerUuid, CHALLENGER_DISCORD_ID);
        playerMapper.updateDiscordId(challengedUuid, CHALLENGED_DISCORD_ID);

        // Non-ranked so createMatch doesn't require an active ranked season; unique name so the
        // final match count assertion is isolated from anything else the test run may create.
        String gameTypeName = "CONCURRENCY_TEST_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(gameTypeName);
        gameType.setDisplayName(gameTypeName);
        gameType.setRanked(false);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);

        DuelChallengeResponse challenge = duelChallengeService.create(
                new CreateDuelChallengeRequest(CHALLENGER_DISCORD_ID, CHALLENGED_DISCORD_ID, gameTypeName));
        duelChallengeService.accept(challenge.id(), CHALLENGED_DISCORD_ID);
        duelChallengeService.reportScores(challenge.id(),
                new ReportDuelScoresRequest(CHALLENGER_DISCORD_ID, 4, 3));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        Callable<Void> confirmTask = () -> {
            ready.countDown();
            go.await();
            try {
                duelChallengeService.confirmScores(challenge.id(), CHALLENGED_DISCORD_ID);
                successCount.incrementAndGet();
            } catch (DuelChallengeNotPendingException e) {
                conflictCount.incrementAndGet();
            }
            return null;
        };

        try {
            Future<Void> first = pool.submit(confirmTask);
            Future<Void> second = pool.submit(confirmTask);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
        assertThat(matchMapper.countByGameType(gameTypeName)).isEqualTo(1);
    }
}
