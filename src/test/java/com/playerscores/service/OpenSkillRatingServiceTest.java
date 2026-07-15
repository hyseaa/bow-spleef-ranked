package com.playerscores.service;

import com.playerscores.config.EloProperties;
import com.playerscores.dto.PlayerRating;
import com.playerscores.dto.TeamRatingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OpenSkillRatingServiceTest {

    private static final double MU = 25.0;
    private static final double SIGMA = 25.0 / 3.0;
    private static final double BETA = 25.0 / 6.0;

    private final EloProperties props = new EloProperties(100, 1000, 15, MU, SIGMA, BETA, 0.0001, 0.0);
    private final OpenSkillRatingService service = new OpenSkillRatingService(props);

    private static PlayerRating defaultRating(UUID uuid) {
        return new PlayerRating(uuid, MU, SIGMA);
    }

    @Test
    void rate_1v1_decisive_matchesReferenceValues() {
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();

        Map<UUID, PlayerRating> result = service.rate(List.of(
                new TeamRatingContext(1L, 1, List.of(defaultRating(winner))),
                new TeamRatingContext(2L, 0, List.of(defaultRating(loser)))));

        // Reference values from the Plackett-Luce model (openskill)
        assertThat(result.get(winner).mu()).isCloseTo(27.63523138347365, within(1e-9));
        assertThat(result.get(winner).sigma()).isCloseTo(8.065506316323548, within(1e-9));
        assertThat(result.get(loser).mu()).isCloseTo(22.36476861652635, within(1e-9));
        assertThat(result.get(loser).sigma()).isCloseTo(8.065506316323548, within(1e-9));
    }

    @Test
    void rate_1v1_tie_muUnchangedSigmaShrinks() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        Map<UUID, PlayerRating> result = service.rate(List.of(
                new TeamRatingContext(1L, 2, List.of(defaultRating(p1))),
                new TeamRatingContext(2L, 2, List.of(defaultRating(p2)))));

        assertThat(result.get(p1).mu()).isCloseTo(25.0, within(1e-9));
        assertThat(result.get(p2).mu()).isCloseTo(25.0, within(1e-9));
        assertThat(result.get(p1).sigma()).isCloseTo(8.065506316323548, within(1e-9));
        assertThat(result.get(p2).sigma()).isCloseTo(8.065506316323548, within(1e-9));
    }

    @Test
    void rate_2v2_decisive_matchesReferenceValues() {
        UUID w1 = UUID.randomUUID();
        UUID w2 = UUID.randomUUID();
        UUID l1 = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();

        Map<UUID, PlayerRating> result = service.rate(List.of(
                new TeamRatingContext(1L, 5, List.of(defaultRating(w1), defaultRating(w2))),
                new TeamRatingContext(2L, 3, List.of(defaultRating(l1), defaultRating(l2)))));

        for (UUID uuid : List.of(w1, w2)) {
            assertThat(result.get(uuid).mu()).isCloseTo(26.964186, within(1e-5));
            assertThat(result.get(uuid).sigma()).isCloseTo(8.177556, within(1e-5));
        }
        for (UUID uuid : List.of(l1, l2)) {
            assertThat(result.get(uuid).mu()).isCloseTo(23.035814, within(1e-5));
            assertThat(result.get(uuid).sigma()).isCloseTo(8.177556, within(1e-5));
        }
    }

    @Test
    void rate_asymmetricSigmas_higherSigmaMovesMore() {
        UUID uncertain = UUID.randomUUID(); // new player, high sigma
        UUID established = UUID.randomUUID(); // established player, low sigma

        Map<UUID, PlayerRating> result = service.rate(List.of(
                new TeamRatingContext(1L, 1, List.of(new PlayerRating(uncertain, 25.0, 25.0 / 3.0))),
                new TeamRatingContext(2L, 0, List.of(new PlayerRating(established, 30.0, 4.0)))));

        double deltaUncertain = result.get(uncertain).mu() - 25.0;
        double deltaEstablished = result.get(established).mu() - 30.0;

        assertThat(deltaUncertain).isPositive();
        assertThat(deltaEstablished).isNegative();
        assertThat(Math.abs(deltaUncertain)).isGreaterThan(Math.abs(deltaEstablished));
        assertThat(result.get(uncertain).sigma()).isLessThan(25.0 / 3.0);
        assertThat(result.get(established).sigma()).isLessThan(4.0);
    }

    @Test
    void rate_threeTeamFfa_ordersMuDeltasByRank() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        Map<UUID, PlayerRating> result = service.rate(List.of(
                new TeamRatingContext(1L, 3, List.of(defaultRating(first))),
                new TeamRatingContext(2L, 2, List.of(defaultRating(second))),
                new TeamRatingContext(3L, 1, List.of(defaultRating(third)))));

        assertThat(result.get(first).mu()).isGreaterThan(25.0);
        assertThat(result.get(third).mu()).isLessThan(25.0);
        assertThat(result.get(first).mu()).isGreaterThan(result.get(second).mu());
        assertThat(result.get(second).mu()).isGreaterThan(result.get(third).mu());
    }

    @Test
    void rate_emptyTeamIsIgnored() {
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();

        Map<UUID, PlayerRating> result = service.rate(List.of(
                new TeamRatingContext(1L, 1, List.of(defaultRating(winner))),
                new TeamRatingContext(2L, 5, List.of()),
                new TeamRatingContext(3L, 0, List.of(defaultRating(loser)))));

        // The empty team is ignored: result is identical to the 1v1 winner/loser case
        assertThat(result.get(winner).mu()).isCloseTo(27.63523138347365, within(1e-9));
        assertThat(result.get(loser).mu()).isCloseTo(22.36476861652635, within(1e-9));
    }

    @Test
    void rate_fewerThanTwoTeams_returnsUnchangedRatings() {
        UUID solo = UUID.randomUUID();

        Map<UUID, PlayerRating> result = service.rate(List.of(
                new TeamRatingContext(1L, 1, List.of(defaultRating(solo))),
                new TeamRatingContext(2L, 0, List.of())));

        assertThat(result.get(solo).mu()).isEqualTo(MU);
        assertThat(result.get(solo).sigma()).isEqualTo(SIGMA);
    }

    @Test
    void displayedElo_defaultRating_isStartingElo() {
        assertThat(service.displayedElo(MU, SIGMA)).isEqualTo(1000);
    }

    @Test
    void displayedElo_isClampedToFloor() {
        // ordinal = -40 - 25 = -65 -> 1000 + 15 * (-65) = 25, clamped to the floor
        assertThat(service.displayedElo(-40.0, SIGMA)).isEqualTo(100);
    }

    @Test
    void displayedElo_isMonotonicInOrdinal() {
        int low = service.displayedElo(22.0, 8.0);
        int mid = service.displayedElo(25.0, 8.0);
        int high = service.displayedElo(28.0, 7.5);
        assertThat(low).isLessThan(mid);
        assertThat(mid).isLessThan(high);
    }
}
