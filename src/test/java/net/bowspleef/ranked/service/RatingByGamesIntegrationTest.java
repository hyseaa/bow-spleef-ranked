package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.config.EloProperties;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.PlayerEloSnapshot;
import net.bowspleef.ranked.dto.PlayerRating;
import net.bowspleef.ranked.dto.TeamRatingContext;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.mapper.EloMapper;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 034 against a real database: a season on OPENSKILL_GAMES rates a 3-2 by its games, writes one history row per
 * player per match and counts the match once (REQ-034-01, REQ-034-09, REQ-034-10); a recompute gives back the same
 * ratings and history (REQ-034-08).
 */
class RatingByGamesIntegrationTest extends AbstractIntegrationTest {

    /** Not under test, and it would call Mojang for every player. */
    @MockBean
    private UsernameCache usernameCache;

    @Autowired
    private MatchService matchService;
    @Autowired
    private EloRecomputeService eloRecomputeService;
    @Autowired
    private OpenSkillByGamesRatingService byGames;
    @Autowired
    private EloProperties props;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private RankedSeasonMapper rankedSeasonMapper;
    @Autowired
    private EloMapper eloMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aThreeTwoIsRatedByItsGames_onceInTheStats_andARecomputeChangesNothing_REQ_034_01_08_09_10() {
        Long seasonId = newSeason();
        String gameType = rankedSeasonMapper.findById(seasonId).orElseThrow().getGameType();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        matchService.createMatch(new CreateMatchRequest(gameType, "discord_bot", List.of(
                new TeamRequest(3, List.of(alice)), new TeamRequest(2, List.of(bob))), null));

        Map<UUID, PlayerRating> expected = byGames.rate(List.of(
                new TeamRatingContext(1L, 3, List.of(new PlayerRating(alice, props.mu(), props.sigma()))),
                new TeamRatingContext(2L, 2, List.of(new PlayerRating(bob, props.mu(), props.sigma())))));
        for (PlayerEloSnapshot s : eloMapper.findEloByUuidsAndSeason(List.of(alice, bob), seasonId)) {
            assertThat(s.getMu()).isEqualTo(expected.get(s.getPlayerUuid()).mu());
            assertThat(s.getSigma()).isEqualTo(expected.get(s.getPlayerUuid()).sigma());
        }

        matchService.createMatch(new CreateMatchRequest(gameType, "hysea-server", List.of(
                new TeamRequest(0, List.of(alice)), new TeamRequest(1, List.of(bob))), null));

        assertThat(jdbc.queryForList("SELECT matches_played FROM player_season_elo WHERE ranked_season_id = ?",
                Integer.class, seasonId)).containsExactly(2, 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM elo_history WHERE ranked_season_id = ?",
                Integer.class, seasonId)).isEqualTo(4);

        List<Map<String, Object>> ratingsBefore = ratings(seasonId);
        List<Map<String, Object>> historyBefore = history(seasonId);
        eloRecomputeService.recomputeSeasonElo(seasonId);
        assertThat(ratings(seasonId)).isEqualTo(ratingsBefore);
        assertThat(history(seasonId)).isEqualTo(historyBefore);
    }

    private Long newSeason() {
        String name = "games_" + UUID.randomUUID().toString().substring(0, 8);
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
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL_GAMES);
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
