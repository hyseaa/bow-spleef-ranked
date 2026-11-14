package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.TeamRequest;
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

/**
 * KI-28: two matches that share a player and are recorded at the same time must not lose an update.
 * The proof is that a sequential replay of the season (recompute) reproduces exactly the ratings and
 * the history stored by the concurrent run.
 */
class ConcurrentRatingIntegrationTest extends AbstractIntegrationTest {

    private static final int ROUNDS = 12;

    @MockBean
    private UsernameCache usernameCache;

    @Autowired
    private MatchService matchService;
    @Autowired
    private EloRecomputeService eloRecomputeService;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private RankedSeasonMapper rankedSeasonMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentMatchesSharingAPlayerLoseNoUpdate_KI_28() throws Exception {
        String name = "concurrent_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(true);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
        RankedSeason season = new RankedSeason();
        season.setName(name);
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(name);
        season.setActive(true);
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL);
        rankedSeasonMapper.insert(season);

        UUID shared = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                UUID other1 = UUID.randomUUID();
                UUID other2 = UUID.randomUUID();
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Void>> futures = new ArrayList<>();
                for (UUID other : List.of(other1, other2)) {
                    Callable<Void> task = () -> {
                        go.await();
                        matchService.createMatch(new CreateMatchRequest(name, "concurrency-test", List.of(
                                new TeamRequest(3, List.of(shared)), new TeamRequest(1, List.of(other))), null));
                        return null;
                    };
                    futures.add(pool.submit(task));
                }
                go.countDown();
                for (Future<Void> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
            }
        } finally {
            pool.shutdown();
        }

        assertThat(jdbc.queryForObject("SELECT matches_played FROM player_season_elo WHERE player_uuid = ? AND ranked_season_id = ?",
                Integer.class, shared, season.getId())).isEqualTo(ROUNDS * 2);

        List<Map<String, Object>> ratings = ratings(season.getId());
        List<Map<String, Object>> history = history(season.getId());
        assertThat(history).hasSize(ROUNDS * 4);

        eloRecomputeService.recomputeSeasonElo(season.getId());

        assertThat(ratings(season.getId())).as("ratings after a sequential replay").isEqualTo(ratings);
        assertThat(history(season.getId())).as("history after a sequential replay").isEqualTo(history);
    }

    private List<Map<String, Object>> ratings(Long seasonId) {
        return jdbc.queryForList("SELECT player_uuid, elo, mu, sigma, matches_played, rank_title FROM player_season_elo "
                + "WHERE ranked_season_id = ? ORDER BY player_uuid", seasonId);
    }

    private List<Map<String, Object>> history(Long seasonId) {
        return jdbc.queryForList("SELECT match_id, player_uuid, elo_before, elo_after, elo_change FROM elo_history "
                + "WHERE ranked_season_id = ? ORDER BY match_id, player_uuid", seasonId);
    }
}
