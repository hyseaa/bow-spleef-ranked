package net.bowspleef.ranked.service;

import net.bowspleef.ranked.config.EloProperties;
import net.bowspleef.ranked.dto.PlayerRating;
import net.bowspleef.ranked.dto.TeamRatingContext;
import net.bowspleef.ranked.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Spec 034, ADR-0034: a ranked match rated by its games, the mean over every order. */
class OpenSkillByGamesRatingServiceTest {

    private static final double MU = 25.0;
    private static final double SIGMA = 25.0 / 3.0;
    private static final double BETA = 25.0 / 6.0;

    /** The production constants (ordinal scale 10), as in the simulations of 2026-11-19. */
    private final EloProperties props = new EloProperties(100, 1000, 10, MU, SIGMA, BETA, 0.0001, 0.0);
    private final OpenSkillRatingService openSkill = new OpenSkillRatingService(props);
    private final OpenSkillByGamesRatingService service = new OpenSkillByGamesRatingService(openSkill);

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    private static TeamRatingContext team(long id, int score, PlayerRating... players) {
        return new TeamRatingContext(id, score, List.of(players));
    }

    private static double ordinalElo(PlayerRating r) {
        return 1000 + 10 * (r.mu() - 3 * r.sigma());
    }

    /** The game-by-game updates of one order written by hand: 'W' = the first team wins the game. */
    private Map<UUID, PlayerRating> byHand(List<TeamRatingContext> teams, String order) {
        List<TeamRatingContext> current = teams;
        Map<UUID, PlayerRating> after = new HashMap<>();
        for (char g : order.toCharArray()) {
            after = openSkill.rate(List.of(
                    new TeamRatingContext(1L, g == 'W' ? 1 : 0, current.get(0).players()),
                    new TeamRatingContext(2L, g == 'W' ? 0 : 1, current.get(1).players())));
            Map<UUID, PlayerRating> ratings = after;
            List<TeamRatingContext> next = new ArrayList<>();
            for (TeamRatingContext t : current) {
                next.add(new TeamRatingContext(t.teamId(), t.score(),
                        t.players().stream().map(p -> ratings.get(p.playerUuid())).toList()));
            }
            current = next;
        }
        return after;
    }

    private void assertMeanOfOrders(List<TeamRatingContext> teams, List<String> orders, UUID... players) {
        Map<UUID, PlayerRating> result = service.rate(teams);
        for (UUID p : players) {
            double mu = 0;
            double sigma = 0;
            for (String order : orders) {
                PlayerRating r = byHand(teams, order).get(p);
                mu += r.mu();
                sigma += r.sigma();
            }
            assertThat(result.get(p).mu()).isCloseTo(mu / orders.size(), within(1e-9));
            assertThat(result.get(p).sigma()).isCloseTo(sigma / orders.size(), within(1e-9));
        }
    }

    @Test
    void algorithmIsOpenSkillGames_REQ_034_11() {
        assertThat(service.algorithm()).isEqualTo(RatingAlgorithm.OPENSKILL_GAMES);
        assertThat(service.initialRating()).isEqualTo(openSkill.initialRating());
    }

    @Test
    void threeTwo_isTheMeanOfItsTenOrders_REQ_034_01_REQ_034_02() {
        List<TeamRatingContext> teams = List.of(
                team(1, 3, new PlayerRating(a, 27.0, 5.0)), team(2, 2, new PlayerRating(b, 24.0, 6.5)));
        assertMeanOfOrders(teams, List.of("WWWLL", "WWLWL", "WWLLW", "WLWWL", "WLWLW",
                "WLLWW", "LWWWL", "LWWLW", "LWLWW", "LLWWW"), a, b);
    }

    @Test
    void valuesOfTheSimulation_REQ_034_02() {
        // Simulation of 2026-11-19 (journal): Elo change of A, from mu - 3 sigma before rounding.
        PlayerRating newA = new PlayerRating(a, MU, SIGMA);
        PlayerRating newB = new PlayerRating(b, MU, SIGMA);
        assertThat(ordinalElo(service.rate(List.of(team(1, 3, newA), team(2, 2, newB))).get(a)) - 1000)
                .isCloseTo(51.7, within(0.1));
        assertThat(ordinalElo(service.rate(List.of(team(1, 4, newA), team(2, 0, newB))).get(a)) - 1000)
                .isCloseTo(101.9, within(0.1));
        PlayerRating oldA = new PlayerRating(a, 30.0, 2.5);
        PlayerRating oldB = new PlayerRating(b, 30.0, 2.5);
        assertThat(ordinalElo(service.rate(List.of(team(1, 3, oldA), team(2, 2, oldB))).get(a)) - ordinalElo(oldA))
                .isCloseTo(6.1, within(0.1));
    }

    @Test
    void oneZero_isExactlyOpenSkill_REQ_034_03() {
        List<TeamRatingContext> oneVsOne = List.of(
                team(1, 1, new PlayerRating(a, 27.0, 5.0)), team(2, 0, new PlayerRating(b, 24.0, 6.5)));
        assertThat(service.rate(oneVsOne)).isEqualTo(openSkill.rate(oneVsOne));
        List<TeamRatingContext> twoVsTwo = List.of(
                team(1, 0, new PlayerRating(a, 27.0, 5.0), new PlayerRating(b, 22.0, 7.0)),
                team(2, 1, new PlayerRating(c, 24.0, 6.5), new PlayerRating(d, 26.0, 3.0)));
        assertThat(service.rate(twoVsTwo)).isEqualTo(openSkill.rate(twoVsTwo));
    }

    @Test
    void zeroZero_isOneDrawAsOpenSkill_REQ_034_04() {
        List<TeamRatingContext> teams = List.of(
                team(1, 0, new PlayerRating(a, 27.0, 5.0)), team(2, 0, new PlayerRating(b, 24.0, 6.5)));
        assertThat(service.rate(teams)).isEqualTo(openSkill.rate(teams));
    }

    @Test
    void threeTeams_isOneUpdateAsOpenSkill_REQ_034_05() {
        List<TeamRatingContext> teams = List.of(team(1, 2, new PlayerRating(a, MU, SIGMA)),
                team(2, 1, new PlayerRating(b, MU, SIGMA)), team(3, 0, new PlayerRating(c, MU, SIGMA)));
        assertThat(service.rate(teams)).isEqualTo(openSkill.rate(teams));
    }

    @Test
    void anEmptyTeam_keepsTheOthersUnchanged() {
        List<TeamRatingContext> teams = List.of(team(1, 3, new PlayerRating(a, MU, SIGMA)), team(2, 2));
        assertThat(service.rate(teams).get(a)).isEqualTo(new PlayerRating(a, MU, SIGMA));
    }

    @Test
    void twoVsTwo_threeOne_isTheMeanOfItsFourOrders_REQ_034_06() {
        List<TeamRatingContext> teams = List.of(
                team(1, 3, new PlayerRating(a, 27.0, 5.0), new PlayerRating(b, 22.0, 7.0)),
                team(2, 1, new PlayerRating(c, 24.0, 6.5), new PlayerRating(d, 26.0, 3.0)));
        assertMeanOfOrders(teams, List.of("WWWL", "WWLW", "WLWW", "LWWW"), a, b, c, d);
    }

    @Test
    void sameMatchTwice_givesEqualDoubles_REQ_034_02() {
        List<TeamRatingContext> teams = List.of(
                team(1, 2, new PlayerRating(a, 27.0, 5.0)), team(2, 4, new PlayerRating(b, 24.0, 6.5)));
        assertThat(service.rate(teams)).isEqualTo(service.rate(teams));
    }

    @Test
    void tiedScores_areRatedAsGames() {
        PlayerRating ra = new PlayerRating(a, MU, SIGMA);
        Map<UUID, PlayerRating> result = service.rate(List.of(team(1, 2, ra), team(2, 2, new PlayerRating(b, MU, SIGMA))));
        assertThat(result.get(a).mu()).isCloseTo(MU, within(1e-9));
        assertThat(result.get(a).sigma()).isLessThan(openSkill.rate(List.of(
                team(1, 0, ra), team(2, 0, new PlayerRating(b, MU, SIGMA)))).get(a).sigma());
    }

    @Test
    void binomial_isCappedAboveTheLimit() {
        assertThat(OpenSkillByGamesRatingService.binomial(5, 3)).isEqualTo(10);
        assertThat(OpenSkillByGamesRatingService.binomial(8, 4)).isEqualTo(70);
        assertThat(OpenSkillByGamesRatingService.binomial(4, 0)).isEqualTo(1);
        assertThat(OpenSkillByGamesRatingService.binomial(60, 30)).isGreaterThan(OpenSkillByGamesRatingService.MAX_ORDERS);
    }

    @Test
    void absurdScores_areRatedByOneUpdate() {
        List<TeamRatingContext> teams = List.of(
                team(1, 30, new PlayerRating(a, MU, SIGMA)), team(2, 30, new PlayerRating(b, MU, SIGMA)));
        assertThat(service.rate(teams)).isEqualTo(openSkill.rate(teams));
    }
}
