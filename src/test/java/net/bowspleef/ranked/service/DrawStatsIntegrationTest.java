package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.LeaderboardEntryResponse;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A drawn match (equal scores: the rating treats it as a draw, and the custom server sends 0-0 when nobody is left)
 * is neither a win nor a loss. The statistics used to count the tied teams as winners.
 */
class DrawStatsIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private MatchService matchService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private RankedSeasonMapper rankedSeasonMapper;

    private String gameType(boolean ranked) {
        String name = "draws_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(ranked);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
        return name;
    }

    private void play(String gameType, UUID a, int scoreA, UUID b, int scoreB) {
        matchService.createMatch(new CreateMatchRequest(gameType, "test",
                List.of(new TeamRequest(scoreA, List.of(a)), new TeamRequest(scoreB, List.of(b))), null));
    }

    @Test
    void nonRankedStatsCountAWinADrawAndALossApart_REQ_011_03() {
        String gameType = gameType(false);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        play(gameType, alice, 3, bob, 1); // alice wins
        play(gameType, alice, 2, bob, 2); // draw
        play(gameType, alice, 0, bob, 0); // draw, nobody left
        play(gameType, alice, 1, bob, 3); // alice loses

        PlayerStatsResponse stats = playerService.getPlayerStats(alice);

        assertThat(stats.gameTypes()).singleElement().satisfies(line -> {
            assertThat(line.gameType()).isEqualTo(gameType);
            assertThat(line.matchesPlayed()).isEqualTo(4);
            assertThat(line.wins()).isEqualTo(1);
            assertThat(line.draws()).isEqualTo(2);
            assertThat(line.losses()).isEqualTo(1);
        });
    }

    @Test
    void theWinsLeaderboardDoesNotCountDraws() {
        // since spec 035 the board counts games won: a 2-2 is two games won by each side, a 0-0 one drawn game
        String gameType = gameType(false);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        play(gameType, alice, 1, bob, 0);
        play(gameType, alice, 0, bob, 0);
        play(gameType, bob, 0, alice, 0);

        List<WinsLeaderboardEntryResponse> board = playerService.getWinsLeaderboard(gameType, 0, 10).content();

        assertThat(board).extracting(WinsLeaderboardEntryResponse::uuid, WinsLeaderboardEntryResponse::gamesWon)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(alice, 1L));
    }

    @Test
    void rankedWinsInTheSeasonAreNotInflatedByDraws() {
        String gameType = gameType(true);
        RankedSeason season = new RankedSeason();
        season.setName(gameType);
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(gameType);
        season.setActive(true);
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL);
        rankedSeasonMapper.insert(season);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        play(gameType, alice, 3, bob, 1);
        play(gameType, alice, 2, bob, 2);
        play(gameType, bob, 4, alice, 4);

        List<LeaderboardEntryResponse> board = playerService.getLeaderboard(season.getId(), 0, 10).content();
        PlayerStatsResponse stats = playerService.getPlayerStats(alice);

        assertThat(board).filteredOn(e -> e.uuid().equals(alice)).singleElement()
                .extracting(LeaderboardEntryResponse::wins).isEqualTo(1L);
        assertThat(board).filteredOn(e -> e.uuid().equals(bob)).singleElement()
                .extracting(LeaderboardEntryResponse::wins).isEqualTo(0L);
        assertThat(stats.ranked()).singleElement().satisfies(r -> assertThat(r.getWins()).isEqualTo(1));
    }
}
