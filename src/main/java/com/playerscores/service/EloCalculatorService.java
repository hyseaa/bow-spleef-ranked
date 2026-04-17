package com.playerscores.service;

import com.playerscores.config.EloProperties;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.dto.TeamEloContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EloCalculatorService {

    private final EloProperties props;

    public int kFactor(int matchesPlayed) {
        if (matchesPlayed < props.provisionalThreshold()) {
            return props.kProvisional();
        }
        if (matchesPlayed < props.establishedThreshold()) {
            return props.kDeveloping();
        }
        return props.kEstablished();
    }

    /**
     * Computes a player's new ELO using pairwise comparison against every opposing team.
     * For a 2-team match this collapses to standard 1v1 ELO.
     *
     * @param playerTeamId the team ID the player belongs to
     * @param teams        all teams in the match with their score and average ELO
     * @param snapshot     the player's current ELO and matches played (pre-match values)
     * @return the new ELO rating, clamped to the configured floor
     */
    public int computeNewElo(long playerTeamId, List<TeamEloContext> teams, PlayerEloSnapshot snapshot) {
        TeamEloContext playerTeam = teams.stream()
                .filter(t -> t.teamId() == playerTeamId)
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Player team not found in ELO context: teamId={}", playerTeamId);
                    return new IllegalArgumentException("Player team not found in context: " + playerTeamId);
                });

        int currentElo = snapshot.getElo();
        int k = kFactor(snapshot.getMatchesPlayed());
        log.debug("Computing ELO for playerUuid={}: currentElo={}, matchesPlayed={}, k={}", snapshot.getPlayerUuid(), currentElo, snapshot.getMatchesPlayed(), k);

        int cumulativeDelta = 0;

        for (TeamEloContext opponent : teams) {
            if (opponent.teamId() == playerTeamId) {
                continue;
            }

            double score;
            if (playerTeam.score() > opponent.score()) {
                score = 1.0;
            } else if (playerTeam.score() < opponent.score()) {
                score = 0.0;
            } else {
                score = 0.5;
            }

            double expected = 1.0 / (1.0 + Math.pow(10.0, (opponent.avgElo() - currentElo) / 400.0));
            int delta = (int) Math.round(k * (score - expected));
            log.debug("vs teamId={} (avgElo={}): score={}, expected={}, delta={}", opponent.teamId(), (int) opponent.avgElo(), score, expected, delta);
            cumulativeDelta += delta;
        }

        int newElo = Math.max(props.floor(), currentElo + cumulativeDelta);
        log.debug("New ELO for playerUuid={}: {} (delta={}, floor={})", snapshot.getPlayerUuid(), newElo, cumulativeDelta, props.floor());
        return newElo;
    }
}
