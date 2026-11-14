package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateDuelChallengeRequest;
import net.bowspleef.ranked.exception.DuelAlreadyOpenException;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.model.GameType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

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

/** KI-09: two players cannot have two open duels of the same game type at once. */
class DuelAlreadyOpenIntegrationTest extends AbstractIntegrationTest {

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

    private static String discordId() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }

    private String player() {
        String id = discordId();
        UUID uuid = UUID.randomUUID();
        playerMapper.insertIfAbsent(uuid);
        playerMapper.updateDiscordId(uuid, id);
        return id;
    }

    private String gameType() {
        String name = "OPEN_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(false);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
        return name;
    }

    @Test
    void aSecondOpenDuelBetweenTheSamePlayersInTheSameGameTypeIsRefused_KI_09() {
        String a = player();
        String b = player();
        String gameType = gameType();
        duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType));

        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType)))
                .isInstanceOf(DuelAlreadyOpenException.class);
        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(b, a, gameType)))
                .as("whoever challenges, it is the same two players")
                .isInstanceOf(DuelAlreadyOpenException.class);
    }

    @Test
    void anotherGameTypeOrAnotherOpponentIsFine_KI_09() {
        String a = player();
        String b = player();
        String c = player();
        String gameType = gameType();
        duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType));

        duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType()));
        duelChallengeService.create(new CreateDuelChallengeRequest(a, c, gameType));
    }

    @Test
    void anActiveOrReportedDuelBlocksToo_andACancelledOneNoLonger_KI_09() {
        String a = player();
        String b = player();
        String gameType = gameType();
        long id = duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType)).id();
        duelChallengeService.accept(id, b);
        assertThatThrownBy(() -> duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType)))
                .isInstanceOf(DuelAlreadyOpenException.class);

        duelChallengeService.requestCancel(id, a);
        duelChallengeService.acceptCancel(id, b);

        duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType));
    }

    @Test
    void aChallengeThatHasExpiredButIsNotDeletedYetDoesNotBlock_KI_09() {
        String a = player();
        String b = player();
        String gameType = gameType();
        long id = duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType)).id();
        jdbc.update("UPDATE duel_challenge SET expires_at = NOW() - INTERVAL '1 minute' WHERE id = ?", id);

        duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType));
    }

    @Test
    void twoRequestsAtTheSameTimeCreateOneDuel_KI_09() throws Exception {
        String gameType = gameType();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String a = player();
                String b = player();
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (String[] pair : new String[][] {{a, b}, {b, a}}) {
                    Callable<Boolean> task = () -> {
                        go.await();
                        try {
                            duelChallengeService.create(new CreateDuelChallengeRequest(pair[0], pair[1], gameType));
                            return true;
                        } catch (DuelAlreadyOpenException e) {
                            return false;
                        }
                    };
                    futures.add(pool.submit(task));
                }
                go.countDown();
                boolean first = futures.get(0).get(30, TimeUnit.SECONDS);
                boolean second = futures.get(1).get(30, TimeUnit.SECONDS);

                assertThat(first ^ second).as("exactly one creation wins, round " + round).isTrue();
            }
        } finally {
            pool.shutdown();
        }
    }
}
