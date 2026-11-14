package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.PlayerStatsResponse;
import net.bowspleef.ranked.dto.TeamRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 011 (ADR-0022): the stats of the non-ranked game types are listed per game type, with no total. */
class GameTypeStatsIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private MatchService matchService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private JdbcTemplate jdbc;

    private String gameType(String display, boolean ranked) {
        String name = "stats_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO game_types (name, display_name, ranked) VALUES (?, ?, ?)", name, display, ranked);
        return name;
    }

    private void play(String gameType, UUID a, int scoreA, UUID b, int scoreB) {
        matchService.createMatch(new CreateMatchRequest(gameType, "test",
                List.of(new TeamRequest(scoreA, List.of(a)), new TeamRequest(scoreB, List.of(b))), null));
    }

    @Test
    void eachNonRankedGameTypeHasItsOwnLineWithItsOwnCounts_REQ_011_03() {
        String classic = gameType("Classic", false);
        String lab = gameType("Lab", false);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        play(classic, alice, 3, bob, 1); // win
        play(classic, alice, 1, bob, 3); // loss
        play(classic, alice, 2, bob, 2); // draw
        play(lab, alice, 5, bob, 0);     // win

        PlayerStatsResponse stats = playerService.getPlayerStats(alice);

        assertThat(stats.gameTypes()).hasSize(2);
        assertThat(stats.gameTypes()).filteredOn(l -> l.gameType().equals(classic)).singleElement().satisfies(line -> {
            assertThat(line.gameTypeDisplayName()).isEqualTo("Classic");
            assertThat(line.matchesPlayed()).isEqualTo(3);
            assertThat(line.wins()).isEqualTo(1);
            assertThat(line.losses()).isEqualTo(1);
            assertThat(line.draws()).isEqualTo(1);
        });
        assertThat(stats.gameTypes()).filteredOn(l -> l.gameType().equals(lab)).singleElement().satisfies(line -> {
            assertThat(line.gameTypeDisplayName()).isEqualTo("Lab");
            assertThat(line.matchesPlayed()).isEqualTo(1);
            assertThat(line.wins()).isEqualTo(1);
            assertThat(line.losses()).isZero();
        });
    }

    @Test
    void theMostPlayedGameTypeComesFirst_REQ_011_03() {
        String few = gameType("Few", false);
        String many = gameType("Many", false);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        play(few, alice, 1, bob, 0);
        play(many, alice, 1, bob, 0);
        play(many, alice, 1, bob, 0);
        play(many, alice, 0, bob, 1);

        assertThat(playerService.getPlayerStats(alice).gameTypes())
                .extracting(l -> l.gameType()).containsExactly(many, few);
    }

    @Test
    void aGameTypeThePlayerNeverPlayedHasNoLine_REQ_011_03() {
        String played = gameType("Played", false);
        String other = gameType("Other", false);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID carol = UUID.randomUUID();
        play(played, alice, 1, bob, 0);
        play(other, bob, 1, carol, 0);

        assertThat(playerService.getPlayerStats(alice).gameTypes())
                .extracting(l -> l.gameType()).containsExactly(played);
    }

    @Test
    void aPlayerWithNoNonRankedMatchHasAnEmptyList_REQ_011_03() {
        UUID alice = UUID.randomUUID();
        jdbc.update("INSERT INTO player (uuid) VALUES (?)", alice);

        PlayerStatsResponse stats = playerService.getPlayerStats(alice);

        assertThat(stats.gameTypes()).isEmpty();
        assertThat(stats.ranked()).isEmpty();
    }

    @Test
    void aRankedGameTypeIsNotInTheNonRankedList_REQ_011_03() {
        String ranked = gameType("Ranked, no season", true);
        String classic = gameType("Classic", false);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        jdbc.update("INSERT INTO ranked_season (name, game_type, active) VALUES (?, ?, TRUE)", ranked, ranked);
        play(ranked, alice, 1, bob, 0);
        play(classic, alice, 1, bob, 0);

        assertThat(playerService.getPlayerStats(alice).gameTypes())
                .extracting(l -> l.gameType()).containsExactly(classic);
    }
}
