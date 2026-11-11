package com.playerscores.service;

import com.playerscores.config.ClassicEloProperties;
import com.playerscores.config.EloProperties;
import com.playerscores.dto.InitialRating;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.dto.RatingResult;
import com.playerscores.dto.TeamRatingInput;
import com.playerscores.model.RatingAlgorithm;
import com.playerscores.rating.GoldenRatings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.playerscores.rating.GoldenRatings.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

/** REQ-001-07: classic Elo restored exactly as it rated seasons until api@29a4e2b. */
class ClassicEloRatingServiceTest {

    private static final double MU = 25.0;
    private static final double SIGMA = 25.0 / 3;

    private final ClassicEloRatingService service = new ClassicEloRatingService(
            new ClassicEloProperties(100, 1000, 10, 30, 40, 20, 10),
            new EloProperties(100, 1000, 10, MU, SIGMA, 25.0 / 6, 0.0001, 0.0));

    @Test
    void identity_REQ_001_07() {
        assertThat(service.algorithm()).isEqualTo(RatingAlgorithm.CLASSIC_ELO);
        assertThat(service.initialRating()).isEqualTo(new InitialRating(1000, MU, SIGMA));
    }

    @Test
    void equalRatings_winAndLoss_moveByHalfK_REQ_001_07() {
        Map<UUID, RatingResult> r = duel(1000, 0, 3, 1000, 0, 1);
        assertThat(r.get(p(1)).elo()).isEqualTo(1020);
        assertThat(r.get(p(2)).elo()).isEqualTo(980);
    }

    @Test
    void equalRatings_tie_noChange_REQ_001_07() {
        Map<UUID, RatingResult> r = duel(1000, 0, 2, 1000, 0, 2);
        assertThat(r.get(p(1)).elo()).isEqualTo(1000);
        assertThat(r.get(p(2)).elo()).isEqualTo(1000);
    }

    /** 900 vs 1100: expected 0.2403, delta round(40 × 0.7597) = 30. */
    @Test
    void underdogWin_REQ_001_07() {
        Map<UUID, RatingResult> r = duel(900, 0, 3, 1100, 0, 1);
        assertThat(r.get(p(1)).elo()).isEqualTo(930);
        assertThat(r.get(p(2)).elo()).isEqualTo(1070);
    }

    /** K = 40 below 10 matches played, 20 below 30, then 10: a win against an equal opponent gives K/2. */
    @ParameterizedTest
    @CsvSource({"0, 1020", "9, 1020", "10, 1010", "29, 1010", "30, 1005", "100, 1005"})
    void kFactorByMatchesPlayed_REQ_001_07(int matchesPlayed, int expectedElo) {
        Map<UUID, RatingResult> r = duel(1000, matchesPlayed, 3, 1000, 0, 1);
        assertThat(r.get(p(1)).elo()).isEqualTo(expectedElo);
    }

    @Test
    void floor_REQ_001_07() {
        Map<UUID, RatingResult> r = duel(110, 0, 0, 110, 0, 3);
        assertThat(r.get(p(1)).elo()).isEqualTo(100);
        assertThat(r.get(p(2)).elo()).isEqualTo(130);
    }

    /** Each player is compared with the opposing team's average Elo using their own Elo. */
    @Test
    void teams_useOpponentAverageAndOwnElo_REQ_001_07() {
        Map<UUID, RatingResult> r = service.rateMatch(List.of(
                team(0, 3, snapshot(p(1), 1100, MU, SIGMA, 0), snapshot(p(2), 900, MU, SIGMA, 0)),
                team(1, 1, snapshot(p(3), 1000, MU, SIGMA, 0), snapshot(p(4), 1000, MU, SIGMA, 0))));
        assertThat(r.get(p(1)).elo()).isEqualTo(1114);
        assertThat(r.get(p(2)).elo()).isEqualTo(926);
        assertThat(r.get(p(3)).elo()).isEqualTo(980);
        assertThat(r.get(p(4)).elo()).isEqualTo(980);
    }

    /** Three teams: one rounded delta per opposing team (match 20 of the golden duels sequence). */
    @Test
    void threeTeams_roundPerOpponent_REQ_001_07() {
        Map<UUID, RatingResult> r = service.rateMatch(List.of(
                team(0, 3, snapshot(p(1), 1055, MU, SIGMA, 20)),
                team(1, 1, snapshot(p(2), 1008, MU, SIGMA, 4)),
                team(2, 2, snapshot(p(3), 951, MU, SIGMA, 4))));
        assertThat(r.get(p(1)).elo()).isEqualTo(1071);
        assertThat(r.get(p(2)).elo()).isEqualTo(968);
        assertThat(r.get(p(3)).elo()).isEqualTo(960);
    }

    @Test
    void muAndSigmaAreReturnedUnchanged_REQ_001_07() {
        Map<UUID, RatingResult> r = service.rateMatch(List.of(
                team(0, 3, snapshot(p(1), 1000, 30.5, 6.1, 3)),
                team(1, 1, snapshot(p(2), 1000, MU, SIGMA, 3))));
        assertThat(r.get(p(1)).mu()).isEqualTo(30.5);
        assertThat(r.get(p(1)).sigma()).isEqualTo(6.1);
    }

    @Test
    void emptyTeamsAreIgnored_singleTeamKeepsRatings_REQ_001_07() {
        Map<UUID, RatingResult> r = service.rateMatch(List.of(
                team(0, 3, snapshot(p(1), 1040, MU, SIGMA, 2)),
                team(1, 1)));
        assertThat(r.get(p(1)).elo()).isEqualTo(1040);
    }

    @ParameterizedTest
    @MethodSource("com.playerscores.rating.GoldenRatings#sequenceNames")
    void matchesTheOriginalImplementation_REQ_001_07(String sequence) {
        GoldenRatings.assertEngineMatchesGolden(service, "rating/golden-classic.json", sequence, false);
    }

    private Map<UUID, RatingResult> duel(int elo1, int played1, int score1, int elo2, int played2, int score2) {
        return service.rateMatch(List.of(
                team(0, score1, snapshot(p(1), elo1, MU, SIGMA, played1)),
                team(1, score2, snapshot(p(2), elo2, MU, SIGMA, played2))));
    }

    private static TeamRatingInput team(long id, int score, PlayerEloSnapshot... players) {
        return new TeamRatingInput(id, score, List.of(players));
    }

    private static UUID p(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }
}
