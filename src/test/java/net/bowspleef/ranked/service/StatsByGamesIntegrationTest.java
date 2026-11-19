package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.LeaderboardEntryResponse;
import net.bowspleef.ranked.dto.PlayerGameTypeStatsResponse;
import net.bowspleef.ranked.dto.PlayerSeasonEloResponse;
import net.bowspleef.ranked.dto.PlayerStatsResponse;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.dto.WinsLeaderboardEntryResponse;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 035 against a real database: what a match gives each of its players (the view {@code player_match_result}),
 * and the stats and leaderboards that sum it.
 */
class StatsByGamesIntegrationTest extends AbstractIntegrationTest {

    /** Not under test, and it would call Mojang for every player. */
    @MockBean
    private UsernameCache usernameCache;

    @Autowired
    private MatchService matchService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private RankedSeasonMapper rankedSeasonMapper;
    @Autowired
    private JdbcTemplate jdbc;

    // --- T1: the view -------------------------------------------------------------------------------------------

    @Test
    void aTwoTeamMatchIsItsGames_REQ_035_01_06() {
        String gameType = gameType(true, 1);
        newSeason(gameType);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        Long threeTwo = match(gameType, 3, List.of(alice), 2, List.of(bob));

        assertThat(row(threeTwo, alice)).isEqualTo("WON 3 2 0");
        assertThat(row(threeTwo, bob)).isEqualTo("LOST 2 3 0");
    }

    @Test
    void bothPlayersOfATeamGetItsGames_REQ_035_01() {
        String gameType = gameType(true, 2);
        newSeason(gameType);
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();

        Long threeOne = match(gameType, 3, List.of(a1, a2), 1, List.of(b1, b2));

        assertThat(row(threeOne, a1)).isEqualTo("WON 3 1 0");
        assertThat(row(threeOne, a2)).isEqualTo("WON 3 1 0");
        assertThat(row(threeOne, b1)).isEqualTo("LOST 1 3 0");
        assertThat(row(threeOne, b2)).isEqualTo("LOST 1 3 0");
    }

    @Test
    void aTieOfAnEvenBestOfIsItsGamesWithNoDraw_REQ_035_01() {
        String gameType = gameType(false, 1);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        Long twoTwo = match(gameType, 2, List.of(alice), 2, List.of(bob));

        assertThat(row(twoTwo, alice)).isEqualTo("DRAWN 2 2 0");
    }

    @Test
    void aNilNilAndAMatchOfThreeTeamsAreOneGame_REQ_035_02() {
        String gameType = gameType(false, 1);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        Long nilNil = match(gameType, 0, List.of(a), 0, List.of(b));
        assertThat(row(nilNil, a)).isEqualTo("DRAWN 0 0 1");

        Long three = createMatch(gameType, new TeamRequest(1, List.of(a)), new TeamRequest(0, List.of(b)),
                new TeamRequest(0, List.of(c)));
        assertThat(row(three, a)).isEqualTo("WON 1 0 0");
        assertThat(row(three, b)).isEqualTo("LOST 0 1 0");

        Long shared = createMatch(gameType, new TeamRequest(2, List.of(a)), new TeamRequest(2, List.of(b)),
                new TeamRequest(1, List.of(c)));
        assertThat(row(shared, a)).isEqualTo("DRAWN 0 0 1");
        assertThat(row(shared, c)).isEqualTo("LOST 0 1 0");
    }

    // --- T2: the stats of a player ------------------------------------------------------------------------------

    @Test
    void theStatsOfAPlayerGiveGamesAndMatches_perActiveSeasonAndPerGameType_REQ_035_04_09() {
        String ranked = gameType(true, 1);
        newSeason(ranked);
        String classic = gameType(false, 1);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        match(ranked, 3, List.of(alice), 2, List.of(bob));
        match(ranked, 1, List.of(alice), 3, List.of(bob));
        match(ranked, 0, List.of(alice), 0, List.of(bob));
        match(classic, 2, List.of(alice), 1, List.of(bob));
        match(classic, 1, List.of(alice), 1, List.of(bob));

        PlayerStatsResponse stats = playerService.getPlayerStats(alice);

        assertThat(stats.ranked()).singleElement().satisfies(r -> {
            assertThat(List.of(r.getMatchesPlayed(), r.getWins(), r.getLosses(), r.getDraws())).containsExactly(3, 1, 1, 1);
            assertThat(List.of(r.getGamesPlayed(), r.getGamesWon(), r.getGamesLost(), r.getGamesDrawn()))
                    .containsExactly(10, 4, 5, 1);
        });
        assertThat(stats.gameTypes()).singleElement().isEqualTo(new PlayerGameTypeStatsResponse(
                classic, classic, 2, 1, 0, 1, 5, 3, 2, 0));
    }

    // --- T3: the Elo leaderboard and the season view ----------------------------------------------------------

    @Test
    void theEloLeaderboardAndTheSeasonViewGiveTheGamesOfTheSeason_matchesStillCounted_REQ_035_05_07() {
        String ranked = gameType(true, 1);
        Long old = newSeason(ranked);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        match(ranked, 4, List.of(alice), 0, List.of(bob));
        jdbc.update("UPDATE ranked_season SET active = FALSE WHERE id = ?", old);
        Long current = newSeason(ranked);
        match(ranked, 3, List.of(alice), 2, List.of(bob));

        List<LeaderboardEntryResponse> board = playerService.getLeaderboard(current, 0, 10).content();
        PlayerSeasonEloResponse season = playerService.getPlayerSeasonElo(alice, current);

        assertThat(board).filteredOn(e -> e.uuid().equals(alice)).singleElement().satisfies(e -> {
            assertThat(e.matchesPlayed()).isEqualTo(1);
            assertThat(e.wins()).isEqualTo(1L);
            assertThat(e.gamesPlayed()).isEqualTo(5L);
            assertThat(e.gamesWon()).isEqualTo(3L);
        });
        assertThat(board).filteredOn(e -> e.uuid().equals(bob)).singleElement()
                .satisfies(e -> assertThat(List.of(e.wins(), e.gamesPlayed(), e.gamesWon())).containsExactly(0L, 5L, 2L));
        assertThat(List.of(season.matchesPlayed(), season.gamesPlayed(), season.gamesWon(), season.gamesLost()))
                .containsExactly(1, 5, 3, 2);
    }

    // --- T4: the wins leaderboard ------------------------------------------------------------------------------

    @Test
    void theWinsLeaderboardRanksByGamesWon_overEveryMatchOfTheGameType_REQ_035_03() {
        String ranked = gameType(true, 1);
        Long old = newSeason(ranked);
        UUID bestOf = UUID.randomUUID();
        UUID four = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        match(ranked, 3, List.of(bestOf), 0, List.of(other));
        jdbc.update("UPDATE ranked_season SET active = FALSE WHERE id = ?", old);
        newSeason(ranked);
        for (int i = 0; i < 4; i++) {
            match(ranked, 1, List.of(four), 0, List.of(other));
        }
        match(ranked, 1, List.of(two), 0, List.of(other));
        match(ranked, 1, List.of(two), 0, List.of(other));
        match(ranked, 2, List.of(loser), 3, List.of(UUID.randomUUID()));

        List<WinsLeaderboardEntryResponse> board = playerService.getWinsLeaderboard(ranked, 0, 10).content();

        assertThat(board).extracting(WinsLeaderboardEntryResponse::uuid)
                .containsSubsequence(four, bestOf, two, loser);
        assertThat(board).filteredOn(e -> e.uuid().equals(bestOf)).singleElement()
                .satisfies(e -> assertThat(List.of(e.gamesWon(), e.wins())).containsExactly(3L, 1L));
        assertThat(board).filteredOn(e -> e.uuid().equals(loser)).singleElement()
                .satisfies(e -> assertThat(List.of(e.gamesWon(), e.wins())).containsExactly(2L, 0L));
        assertThat(board).extracting(WinsLeaderboardEntryResponse::uuid).doesNotContain(other);
        assertThat(playerService.getWinsLeaderboard(ranked, 0, 10).total()).isEqualTo((long) board.size());
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private String row(Long matchId, UUID player) {
        return jdbc.queryForObject("SELECT result || ' ' || games_won || ' ' || games_lost || ' ' || games_drawn "
                + "FROM player_match_result WHERE match_id = ? AND player_uuid = ?", String.class, matchId, player);
    }

    private Long match(String gameType, int scoreA, List<UUID> teamA, int scoreB, List<UUID> teamB) {
        return createMatch(gameType, new TeamRequest(scoreA, teamA), new TeamRequest(scoreB, teamB));
    }

    private Long createMatch(String gameType, TeamRequest... teams) {
        return matchService.createMatch(new CreateMatchRequest(gameType, "discord_bot", Arrays.asList(teams), null)).id();
    }

    private String gameType(boolean ranked, int teamSize) {
        String name = "stats_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(ranked);
        gameType.setTeamSize(teamSize);
        gameTypeMapper.insert(gameType);
        return name;
    }

    private Long newSeason(String gameType) {
        RankedSeason season = new RankedSeason();
        season.setName(gameType + "_" + UUID.randomUUID().toString().substring(0, 4));
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(gameType);
        season.setActive(true);
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL_GAMES);
        rankedSeasonMapper.insert(season);
        return season.getId();
    }
}
