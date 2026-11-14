package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.exception.IdempotencyKeyReusedException;
import net.bowspleef.ranked.exception.NoActiveRankedSeasonException;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.RankedSeasonMapper;
import net.bowspleef.ranked.model.GameType;
import net.bowspleef.ranked.model.RankedSeason;
import net.bowspleef.ranked.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Spec 002: {@code MatchService.recordMatch} with an idempotency key, against a real PostgreSQL.
 * Each test uses its own ranked game type (and season), so tests do not see each other's matches.
 */
class IdempotentMatchIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE = "hysea-server";
    private static final int ROUNDS = 12;

    /** A key per test: keys are unique per source across the whole shared database. */
    private final String key = "game-" + UUID.randomUUID();

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private MatchService matchService;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private RankedSeasonMapper rankedSeasonMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private String rankedGameType() {
        String name = "idem_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(true);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
        return name;
    }

    private RankedSeason startSeason(String gameType) {
        RankedSeason season = new RankedSeason();
        season.setName(gameType);
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(gameType);
        season.setActive(true);
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL);
        rankedSeasonMapper.insert(season);
        return season;
    }

    private CreateMatchRequest request(String gameType, String source, UUID winner, UUID loser, int loserScore) {
        return new CreateMatchRequest(gameType, source,
                List.of(new TeamRequest(3, List.of(winner)), new TeamRequest(loserScore, List.of(loser))), null);
    }

    private long matchesWithKey(String gameType, String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM match WHERE game_type = ? AND idempotency_key = ?",
                Long.class, gameType, key);
    }

    private List<Map<String, Object>> ratings(Long seasonId) {
        return jdbc.queryForList("SELECT player_uuid, elo, mu, sigma, matches_played, rank_title FROM player_season_elo "
                + "WHERE ranked_season_id = ? ORDER BY player_uuid", seasonId);
    }

    private List<Map<String, Object>> history(Long seasonId) {
        return jdbc.queryForList("SELECT match_id, player_uuid, elo_before, elo_after, elo_change FROM elo_history "
                + "WHERE ranked_season_id = ? ORDER BY match_id, player_uuid", seasonId);
    }

    @Test
    void replayReturnsTheSameMatchAndChangesNothing_REQ_002_01_REQ_002_02_REQ_002_03() {
        String gameType = rankedGameType();
        RankedSeason season = startSeason(gameType);
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        CreateMatchRequest request = request(gameType, SOURCE, winner, loser, 1);

        MatchRecording first = matchService.recordMatch(request, key);
        List<Map<String, Object>> ratingsAfterFirst = ratings(season.getId());
        List<Map<String, Object>> historyAfterFirst = history(season.getId());
        MatchRecording replay = matchService.recordMatch(request, key);

        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.match().id()).isEqualTo(first.match().id());
        assertThat(matchesWithKey(gameType, key)).isEqualTo(1);
        assertThat(ratings(season.getId())).as("ratings after the replay").isEqualTo(ratingsAfterFirst);
        assertThat(history(season.getId())).as("history after the replay").isEqualTo(historyAfterFirst);
        verify(matchWebhookClient, times(1)).notifyMatchResult(any());
    }

    @Test
    void twoRequestsWithTheSameKeyAtTheSameTimeRecordOneMatch_REQ_002_04() throws Exception {
        String gameType = rankedGameType();
        startSeason(gameType);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String roundKey = key + "-round-" + round;
                CreateMatchRequest request = request(gameType, SOURCE, UUID.randomUUID(), UUID.randomUUID(), 1);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<MatchRecording>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    Callable<MatchRecording> task = () -> {
                        go.await();
                        return matchService.recordMatch(request, roundKey);
                    };
                    futures.add(pool.submit(task));
                }
                go.countDown();
                MatchRecording a = futures.get(0).get(30, TimeUnit.SECONDS);
                MatchRecording b = futures.get(1).get(30, TimeUnit.SECONDS);

                assertThat(matchesWithKey(gameType, roundKey)).as("matches for " + roundKey).isEqualTo(1);
                assertThat(a.match().id()).isEqualTo(b.match().id());
                assertThat(a.created() ^ b.created()).as("exactly one of the two created it").isTrue();
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void aRefusedFirstAttemptLeavesNoKeyBehind_REQ_002_05() {
        String gameType = rankedGameType();
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        CreateMatchRequest request = request(gameType, SOURCE, winner, loser, 1);

        assertThatThrownBy(() -> matchService.recordMatch(request, key))
                .isInstanceOf(NoActiveRankedSeasonException.class);
        assertThat(matchesWithKey(gameType, key)).isZero();

        startSeason(gameType);
        MatchRecording retry = matchService.recordMatch(request, key);

        assertThat(retry.created()).isTrue();
        assertThat(matchesWithKey(gameType, key)).isEqualTo(1);
    }

    @Test
    void theSameKeyInTwoSourcesGivesTwoMatches_REQ_002_07() {
        String gameType = rankedGameType();
        startSeason(gameType);
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();

        MatchRecording a = matchService.recordMatch(request(gameType, "source-a", winner, loser, 1), key);
        MatchRecording b = matchService.recordMatch(request(gameType, "source-b", winner, loser, 1), key);

        assertThat(a.created()).isTrue();
        assertThat(b.created()).isTrue();
        assertThat(a.match().id()).isNotEqualTo(b.match().id());
        assertThat(matchesWithKey(gameType, key)).isEqualTo(2);
    }

    @Test
    void theSameKeyWithAnotherMatchIsRefusedAndCreatesNothing_REQ_002_10() {
        String gameType = rankedGameType();
        RankedSeason season = startSeason(gameType);
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        matchService.recordMatch(request(gameType, SOURCE, winner, loser, 1), key);
        List<Map<String, Object>> ratingsBefore = ratings(season.getId());

        assertThatThrownBy(() -> matchService.recordMatch(request(gameType, SOURCE, winner, loser, 2), key))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        assertThat(matchesWithKey(gameType, key)).isEqualTo(1);
        assertThat(ratings(season.getId())).isEqualTo(ratingsBefore);
    }

    @Test
    void deletingTheMatchFreesItsKey_REQ_002_01() {
        String gameType = rankedGameType();
        startSeason(gameType);
        CreateMatchRequest request = request(gameType, SOURCE, UUID.randomUUID(), UUID.randomUUID(), 1);
        MatchRecording first = matchService.recordMatch(request, key);

        matchService.deleteMatch(first.match().id());
        MatchRecording again = matchService.recordMatch(request, key);

        assertThat(again.created()).isTrue();
        assertThat(again.match().id()).isNotEqualTo(first.match().id());
        assertThat(matchesWithKey(gameType, key)).isEqualTo(1);
    }
}
