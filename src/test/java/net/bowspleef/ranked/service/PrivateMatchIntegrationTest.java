package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.dto.PlayerStatsResponse;
import net.bowspleef.ranked.dto.TeamRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 038 (ADR-0036): a private match is stored and returned, but left out of the stats, the wins leaderboard and the list. */
class PrivateMatchIntegrationTest extends AbstractIntegrationTest {

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

    private String classic() {
        String name = "private_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO game_types (name, display_name, ranked) VALUES (?, ?, ?)", name, "Classic", false);
        return name;
    }

    private MatchResponse play(String gameType, UUID winner, UUID loser, boolean isPrivate) {
        return matchService.recordMatch(new CreateMatchRequest(gameType, "test",
                List.of(new TeamRequest(1, List.of(winner)), new TeamRequest(0, List.of(loser))), null, null, isPrivate),
                null).match();
    }

    @Test
    void aPrivateMatchIsStoredAndReturnedPrivate_REQ_038_13() {
        String classic = classic();

        MatchResponse match = play(classic, UUID.randomUUID(), UUID.randomUUID(), true);

        assertThat(match.privateMatch()).isTrue();
        assertThat(matchService.getMatch(match.id()).privateMatch()).isTrue();
        assertThat(play(classic, UUID.randomUUID(), UUID.randomUUID(), false).privateMatch()).isFalse();
    }

    @Test
    void aPrivateMatchCountsInNeitherTheStatsNorTheWinsLeaderboard_REQ_038_15() {
        String classic = classic();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        play(classic, alice, bob, false);
        play(classic, alice, bob, true);
        play(classic, alice, bob, true);

        PlayerStatsResponse stats = playerService.getPlayerStats(alice);

        assertThat(stats.gameTypes()).filteredOn(l -> l.gameType().equals(classic)).singleElement()
                .satisfies(line -> {
                    assertThat(line.matchesPlayed()).isEqualTo(1);
                    assertThat(line.wins()).isEqualTo(1);
                });
        assertThat(playerService.getWinsLeaderboard(classic, 0, 10).content()).singleElement()
                .satisfies(entry -> assertThat(entry.gamesWon()).isEqualTo(1));
    }

    @Test
    void theListLeavesPrivateMatchesOutUnlessAsked_REQ_038_16() {
        String classic = classic();
        play(classic, UUID.randomUUID(), UUID.randomUUID(), false);
        play(classic, UUID.randomUUID(), UUID.randomUUID(), true);

        assertThat(matchService.getMatchesByGameType(classic, 0, 20).content()).hasSize(1);
        assertThat(matchService.getMatchesByGameType(classic, 0, 20).total()).isEqualTo(1);
        assertThat(matchService.getMatchesByGameType(classic, 0, 20, true).content()).hasSize(2);
    }
}
