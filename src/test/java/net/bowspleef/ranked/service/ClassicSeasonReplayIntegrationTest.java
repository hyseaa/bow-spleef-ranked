package net.bowspleef.ranked.service;

import com.fasterxml.jackson.databind.JsonNode;
import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.PlayerEloSnapshot;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.mapper.EloMapper;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.RankedSeasonMapper;
import net.bowspleef.ranked.model.GameType;
import net.bowspleef.ranked.model.RankedSeason;
import net.bowspleef.ranked.model.RatingAlgorithm;
import net.bowspleef.ranked.rating.GoldenRatings;
import net.bowspleef.ranked.rating.GoldenRatings.GoldenTeam;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 001 against a real database: matches recorded live in a season are rated with the season's
 * algorithm (REQ-001-05) and give the values of the original implementations (REQ-001-07,
 * REQ-001-09); recomputing the season gives back exactly the same ratings, history and titles
 * (REQ-001-06, REQ-001-08).
 */
class ClassicSeasonReplayIntegrationTest extends AbstractIntegrationTest {

    /** Not under test, and it would call Mojang for every player. */
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
    private EloMapper eloMapper;
    @Autowired
    private JdbcTemplate jdbc;

    static Stream<Arguments> seasons() {
        return Stream.of(RatingAlgorithm.values()).flatMap(algorithm ->
                GoldenRatings.sequenceNames().stream().map(sequence -> Arguments.of(algorithm, sequence)));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("seasons")
    void liveMatchesMatchTheGoldenValues_andARecomputeChangesNothing_REQ_001_05_06_08(RatingAlgorithm algorithm, String sequence) {
        List<List<GoldenTeam>> matches = GoldenRatings.sequence(sequence);
        String goldenFile = algorithm == RatingAlgorithm.CLASSIC_ELO ? "rating/golden-classic.json" : "rating/golden-openskill.json";
        List<Map<UUID, JsonNode>> expected = GoldenRatings.expected(goldenFile, sequence);
        Long seasonId = newSeason(algorithm, matches.getFirst().getFirst().players().size());
        String gameType = rankedSeasonMapper.findById(seasonId).orElseThrow().getGameType();

        for (int m = 0; m < matches.size(); m++) {
            List<TeamRequest> teams = matches.get(m).stream().map(t -> new TeamRequest(t.score(), t.players())).toList();
            matchService.createMatch(new CreateMatchRequest(gameType, "golden-test", teams, null));

            List<UUID> players = List.copyOf(expected.get(m).keySet());
            for (PlayerEloSnapshot s : eloMapper.findEloByUuidsAndSeason(players, seasonId)) {
                JsonNode golden = expected.get(m).get(s.getPlayerUuid());
                assertThat(s.getElo()).as("%s %s match %d player %s", algorithm, sequence, m, s.getPlayerUuid())
                        .isEqualTo(golden.get("after").asInt());
                if (algorithm == RatingAlgorithm.OPENSKILL) {
                    assertThat(s.getMu()).isEqualTo(golden.get("mu").asDouble());
                    assertThat(s.getSigma()).isEqualTo(golden.get("sigma").asDouble());
                }
            }
        }

        List<Map<String, Object>> ratingsBefore = ratings(seasonId);
        List<Map<String, Object>> historyBefore = history(seasonId);
        assertThat(historyBefore).hasSize(expected.stream().mapToInt(Map::size).sum());

        eloRecomputeService.recomputeSeasonElo(seasonId);

        assertThat(ratings(seasonId)).isEqualTo(ratingsBefore);
        assertThat(history(seasonId)).isEqualTo(historyBefore);
        assertThat(rankedSeasonMapper.findById(seasonId).orElseThrow().getRatingAlgorithm()).isEqualTo(algorithm);
    }

    private Long newSeason(RatingAlgorithm algorithm, int teamSize) {
        String name = "golden_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(true);
        gameType.setTeamSize(teamSize);
        gameTypeMapper.insert(gameType);

        RankedSeason season = new RankedSeason();
        season.setName(name);
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(name);
        season.setActive(true);
        season.setRatingAlgorithm(algorithm);
        rankedSeasonMapper.insert(season);
        return season.getId();
    }

    private List<Map<String, Object>> ratings(Long seasonId) {
        return jdbc.queryForList("SELECT player_uuid, elo, mu, sigma, matches_played, rank_title FROM player_season_elo "
                + "WHERE ranked_season_id = ? ORDER BY player_uuid", seasonId);
    }

    /** Values only: ids and recorded_at are rebuilt by a recompute (ADR-0009). */
    private List<Map<String, Object>> history(Long seasonId) {
        return jdbc.queryForList("SELECT match_id, player_uuid, elo_before, elo_after, elo_change FROM elo_history "
                + "WHERE ranked_season_id = ? ORDER BY match_id, player_uuid", seasonId);
    }
}
