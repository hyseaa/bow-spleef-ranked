package net.bowspleef.ranked.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bowspleef.ranked.dto.InitialRating;
import net.bowspleef.ranked.dto.PlayerRating;
import net.bowspleef.ranked.dto.RatingResult;
import net.bowspleef.ranked.dto.TeamRatingContext;
import net.bowspleef.ranked.dto.TeamRatingInput;
import net.bowspleef.ranked.model.RatingAlgorithm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenSkill by games (spec 034, ADR-0034). A match of two non-empty teams scored {@code a}-{@code b}, {@code a + b ≥ 1},
 * is {@code a + b} games whose order is unknown: for every distinct order, the Weng-Lin update of
 * {@link OpenSkillRatingService} is applied game after game from the ratings of before the match, and each player's new
 * {@code mu} and {@code sigma} are the means over the orders. A 1-0 match is therefore rated exactly as by
 * {@code OPENSKILL}; a 0-0 match, a match of more than two teams and one of fewer than two non-empty teams are rated by
 * one update, as by {@code OPENSKILL}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenSkillByGamesRatingService implements RatingEngine {

    /**
     * Above this number of orders the match is rated by one update. Never reached by a match recorded under the cap of
     * 4 per team (at most 70 orders); only an older match with absurd scores could reach it.
     */
    static final long MAX_ORDERS = 1_000_000L;

    private final OpenSkillRatingService openSkill;

    @Override
    public RatingAlgorithm algorithm() {
        return RatingAlgorithm.OPENSKILL_GAMES;
    }

    @Override
    public InitialRating initialRating() {
        return openSkill.initialRating();
    }

    @Override
    public Map<UUID, RatingResult> rateMatch(List<TeamRatingInput> teams) {
        List<TeamRatingContext> contexts = teams.stream()
                .map(t -> new TeamRatingContext(t.teamId(), t.score(), t.players().stream()
                        .map(p -> new PlayerRating(p.getPlayerUuid(), p.getMu(), p.getSigma()))
                        .toList()))
                .toList();
        Map<UUID, RatingResult> result = new HashMap<>();
        rate(contexts).forEach((uuid, r) -> result.put(uuid,
                new RatingResult(uuid, openSkill.displayedElo(r.mu(), r.sigma()), r.mu(), r.sigma())));
        return result;
    }

    /** The new rating of every player of the match (players of empty teams keep theirs). */
    public Map<UUID, PlayerRating> rate(List<TeamRatingContext> teams) {
        List<TeamRatingContext> rateable = teams.stream().filter(t -> !t.players().isEmpty()).toList();
        if (rateable.size() != 2) {
            return openSkill.rate(teams);
        }
        int wins = rateable.get(0).score();
        int losses = rateable.get(1).score();
        if (wins < 0 || losses < 0 || wins + losses == 0) {
            return openSkill.rate(teams);
        }
        int games = wins + losses;
        long orders = binomial(games, wins);
        if (games > 62 || orders > MAX_ORDERS) {
            log.warn("Match of {}-{} has {} orders of games, rated by one update", wins, losses, orders);
            return openSkill.rate(teams);
        }

        List<TeamRatingContext> start = openSkill.withTau(rateable);
        Map<UUID, double[]> sums = new LinkedHashMap<>();
        // Every mask of `games` bits with `wins` bits set, ascending (Gosper's hack): bit g set = the first team wins
        // game g. The sums always run in the same order, so the same match always gives the same doubles.
        long mask = (1L << wins) - 1;
        long end = 1L << games;
        while (mask < end) {
            accumulate(sums, playOrder(start, mask, games));
            if (mask == 0) {
                break;
            }
            long lowest = mask & -mask;
            long ripple = mask + lowest;
            mask = (((ripple ^ mask) >>> 2) / lowest) | ripple;
        }

        Map<UUID, PlayerRating> result = new HashMap<>();
        sums.forEach((uuid, s) -> result.put(uuid, new PlayerRating(uuid, s[0] / orders, s[1] / orders)));
        teams.forEach(t -> t.players().forEach(p -> result.putIfAbsent(p.playerUuid(), p)));
        return result;
    }

    /** The ratings after the games of one order, from the ratings of before the match. */
    private Map<UUID, PlayerRating> playOrder(List<TeamRatingContext> start, long mask, int games) {
        List<TeamRatingContext> current = start;
        Map<UUID, PlayerRating> after = null;
        for (int g = 0; g < games; g++) {
            boolean firstWins = (mask >>> g & 1L) == 1L;
            List<TeamRatingContext> game = List.of(
                    new TeamRatingContext(current.get(0).teamId(), firstWins ? 1 : 0, current.get(0).players()),
                    new TeamRatingContext(current.get(1).teamId(), firstWins ? 0 : 1, current.get(1).players()));
            after = openSkill.rateOnce(game, new HashMap<>());
            current = withRatings(current, after);
        }
        return after;
    }

    private static List<TeamRatingContext> withRatings(List<TeamRatingContext> teams, Map<UUID, PlayerRating> ratings) {
        List<TeamRatingContext> next = new ArrayList<>(teams.size());
        for (TeamRatingContext t : teams) {
            next.add(new TeamRatingContext(t.teamId(), t.score(),
                    t.players().stream().map(p -> ratings.get(p.playerUuid())).toList()));
        }
        return next;
    }

    private static void accumulate(Map<UUID, double[]> sums, Map<UUID, PlayerRating> ratings) {
        ratings.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    double[] s = sums.computeIfAbsent(e.getKey(), k -> new double[2]);
                    s[0] += e.getValue().mu();
                    s[1] += e.getValue().sigma();
                });
    }

    /** The number of ways to choose {@code k} games among {@code n}, capped just above {@link #MAX_ORDERS}. */
    static long binomial(int n, int k) {
        long c = 1;
        for (int i = 1; i <= Math.min(k, n - k); i++) {
            c = c * (n - Math.min(k, n - k) + i) / i;
            if (c > MAX_ORDERS) {
                return MAX_ORDERS + 1;
            }
        }
        return c;
    }
}
