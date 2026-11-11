package com.playerscores.service;

import com.playerscores.config.ClassicEloProperties;
import com.playerscores.config.EloProperties;
import com.playerscores.dto.InitialRating;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.dto.RatingResult;
import com.playerscores.dto.TeamRatingInput;
import com.playerscores.model.RatingAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classic Elo, restored exactly as it rated seasons until api@29a4e2b (ADR-0013, REQ-001-07):
 * each player is compared with the average pre-match Elo of every other team on a 400-point
 * logistic scale, one rounded delta per opposing team, K-factor by matches already played,
 * floor at {@code floor}. mu and sigma are not used and returned unchanged.
 */
@Service
@RequiredArgsConstructor
public class ClassicEloRatingService implements RatingEngine {

    private final ClassicEloProperties props;
    private final EloProperties openSkillProps;

    @Override
    public RatingAlgorithm algorithm() {
        return RatingAlgorithm.CLASSIC_ELO;
    }

    /** Classic rows keep the OpenSkill defaults in mu/sigma, as V22 left them. */
    @Override
    public InitialRating initialRating() {
        return new InitialRating(props.startingElo(), openSkillProps.mu(), openSkillProps.sigma());
    }

    public int kFactor(int matchesPlayed) {
        if (matchesPlayed < props.provisionalThreshold()) {
            return props.kProvisional();
        }
        if (matchesPlayed < props.establishedThreshold()) {
            return props.kDeveloping();
        }
        return props.kEstablished();
    }

    @Override
    public Map<UUID, RatingResult> rateMatch(List<TeamRatingInput> teams) {
        List<TeamRatingInput> rateable = teams.stream()
                .filter(t -> !t.players().isEmpty())
                .toList();
        double[] avgElo = rateable.stream()
                .mapToDouble(t -> t.players().stream().mapToInt(PlayerEloSnapshot::getElo).average().orElse(props.startingElo()))
                .toArray();

        Map<UUID, RatingResult> result = new HashMap<>();
        for (int i = 0; i < rateable.size(); i++) {
            TeamRatingInput team = rateable.get(i);
            for (PlayerEloSnapshot player : team.players()) {
                int k = kFactor(player.getMatchesPlayed());
                int cumulativeDelta = 0;
                for (int j = 0; j < rateable.size(); j++) {
                    if (j == i) {
                        continue;
                    }
                    int opponentScore = rateable.get(j).score();
                    double score = team.score() > opponentScore ? 1.0 : team.score() < opponentScore ? 0.0 : 0.5;
                    double expected = 1.0 / (1.0 + Math.pow(10.0, (avgElo[j] - player.getElo()) / 400.0));
                    cumulativeDelta += (int) Math.round(k * (score - expected));
                }
                int newElo = Math.max(props.floor(), player.getElo() + cumulativeDelta);
                result.put(player.getPlayerUuid(), new RatingResult(player.getPlayerUuid(), newElo, player.getMu(), player.getSigma()));
            }
        }
        teams.forEach(t -> t.players().forEach(p ->
                result.putIfAbsent(p.getPlayerUuid(), new RatingResult(p.getPlayerUuid(), p.getElo(), p.getMu(), p.getSigma()))));
        return result;
    }
}
