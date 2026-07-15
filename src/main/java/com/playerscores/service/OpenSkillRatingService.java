package com.playerscores.service;

import com.playerscores.config.EloProperties;
import com.playerscores.dto.PlayerRating;
import com.playerscores.dto.TeamRatingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OpenSkill rating engine — Weng-Lin Plackett-Luce model
 * ("A Bayesian Approximation Method for Online Ranking", Weng &amp; Lin, 2011).
 * Supports any number of teams of any size; team ranks are derived from scores
 * (higher score = better rank, equal scores tie).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenSkillRatingService {

    private final EloProperties props;

    /**
     * Computes post-match ratings for every player of the match in one pass.
     * Teams without players are ignored; if fewer than 2 ratable teams remain,
     * all ratings are returned unchanged.
     */
    public Map<UUID, PlayerRating> rate(List<TeamRatingContext> teams) {
        Map<UUID, PlayerRating> result = new HashMap<>();

        List<TeamRatingContext> rateable = teams.stream()
                .filter(t -> !t.players().isEmpty())
                .toList();
        if (rateable.size() < 2) {
            log.debug("Fewer than 2 rateable teams ({}), ratings unchanged", rateable.size());
            teams.forEach(t -> t.players().forEach(p -> result.put(p.playerUuid(), p)));
            return result;
        }

        if (props.tau() > 0) {
            rateable = rateable.stream()
                    .map(t -> new TeamRatingContext(t.teamId(), t.score(), t.players().stream()
                            .map(p -> new PlayerRating(p.playerUuid(), p.mu(),
                                    Math.sqrt(p.sigma() * p.sigma() + props.tau() * props.tau())))
                            .toList()))
                    .toList();
        }

        int k = rateable.size();
        int[] rank = new int[k];
        double[] teamMu = new double[k];
        double[] teamSigmaSq = new double[k];

        for (int i = 0; i < k; i++) {
            TeamRatingContext team = rateable.get(i);
            for (int q = 0; q < k; q++) {
                if (rateable.get(q).score() > team.score()) {
                    rank[i]++;
                }
            }
            rank[i]++; // rank 1 = best
            for (PlayerRating p : team.players()) {
                teamMu[i] += p.mu();
                teamSigmaSq[i] += p.sigma() * p.sigma();
            }
        }

        double cSq = props.beta() * props.beta() * k;
        for (int i = 0; i < k; i++) {
            cSq += teamSigmaSq[i];
        }
        double c = Math.sqrt(cSq);

        // A_q = number of teams tied at q's rank (q included)
        int[] ties = new int[k];
        // SumQ_q = sum of exp(muT/c) over teams ranked equal or worse than q
        double[] sumQ = new double[k];
        for (int q = 0; q < k; q++) {
            for (int s = 0; s < k; s++) {
                if (rank[s] == rank[q]) {
                    ties[q]++;
                }
                if (rank[s] >= rank[q]) {
                    sumQ[q] += Math.exp(teamMu[s] / c);
                }
            }
        }

        for (int i = 0; i < k; i++) {
            double expMuI = Math.exp(teamMu[i] / c);
            double omegaSum = 0.0;
            double deltaSum = 0.0;
            for (int q = 0; q < k; q++) {
                if (rank[q] > rank[i]) {
                    continue;
                }
                double p = expMuI / sumQ[q];
                omegaSum += ((q == i) ? (1.0 - p) : -p) / ties[q];
                deltaSum += p * (1.0 - p) / ties[q];
            }
            double gamma = Math.sqrt(teamSigmaSq[i]) / c;
            double omega = omegaSum * (teamSigmaSq[i] / c);
            double delta = gamma * deltaSum * (teamSigmaSq[i] / cSq);

            for (PlayerRating p : rateable.get(i).players()) {
                double weight = (p.sigma() * p.sigma()) / teamSigmaSq[i];
                double newMu = p.mu() + weight * omega;
                double newSigma = p.sigma() * Math.sqrt(Math.max(1.0 - weight * delta, props.kappa()));
                result.put(p.playerUuid(), new PlayerRating(p.playerUuid(), newMu, newSigma));
            }
        }

        // Players from empty teams keep their rating unchanged
        teams.forEach(t -> t.players().forEach(p -> result.putIfAbsent(p.playerUuid(), p)));
        return result;
    }

    /** Displayed integer rating derived from the ordinal mu - 3*sigma (leaderboards, queue, rank titles). */
    public int displayedElo(double mu, double sigma) {
        double ordinal = mu - 3.0 * sigma;
        long displayed = Math.round(props.startingElo() + props.ordinalScale() * ordinal);
        return (int) Math.max(props.floor(), displayed);
    }
}
