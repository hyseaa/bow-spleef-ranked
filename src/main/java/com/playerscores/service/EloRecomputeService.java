package com.playerscores.service;

import com.playerscores.client.MatchWebhookClient;
import com.playerscores.config.EloProperties;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.dto.PlayerRankEntry;
import com.playerscores.dto.RankSnapshotPayload;
import com.playerscores.dto.TeamEloContext;
import com.playerscores.mapper.EloMapper;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.EloHistory;
import com.playerscores.model.Match;
import com.playerscores.model.Player;
import com.playerscores.model.Team;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EloRecomputeService {

    private final EloMapper eloMapper;
    private final EloCalculatorService eloCalculator;
    private final MatchMapper matchMapper;
    private final TeamMapper teamMapper;
    private final TeamPlayerMapper teamPlayerMapper;
    private final RankedSeasonMapper rankedSeasonMapper;
    private final EloProperties eloProperties;
    private final PlayerMapper playerMapper;
    private final MatchWebhookClient matchWebhookClient;

    public void applyEloUpdates(Long matchId, Long seasonId, List<Team> teams, Map<Long, List<UUID>> teamIdToPlayers) {
        for (List<UUID> uuids : teamIdToPlayers.values()) {
            for (UUID uuid : uuids) {
                eloMapper.upsertPlayerSeasonElo(uuid, seasonId);
            }
        }

        List<UUID> allUuids = teamIdToPlayers.values().stream().flatMap(List::stream).toList();
        Map<UUID, PlayerEloSnapshot> snapshots = new HashMap<>();
        for (PlayerEloSnapshot snapshot : eloMapper.findEloByUuidsAndSeason(allUuids, seasonId)) {
            snapshots.put(snapshot.getPlayerUuid(), snapshot);
        }
        log.debug("Loaded ELO snapshots for {} player(s) in seasonId={}", snapshots.size(), seasonId);

        List<TeamEloContext> teamContexts = new ArrayList<>();
        for (Team team : teams) {
            List<UUID> members = teamIdToPlayers.getOrDefault(team.getId(), List.of());
            if (members.isEmpty()) {
                continue;
            }
            double avgElo = members.stream()
                    .mapToInt(uuid -> snapshots.get(uuid).getElo())
                    .average()
                    .orElse(1000.0);
            teamContexts.add(new TeamEloContext(team.getId(), team.getScore(), avgElo));
        }

        Map<UUID, Integer> newElos = new HashMap<>();
        for (Team team : teams) {
            for (UUID uuid : teamIdToPlayers.getOrDefault(team.getId(), List.of())) {
                int newElo = eloCalculator.computeNewElo(team.getId(), teamContexts, snapshots.get(uuid));
                log.debug("ELO update for uuid={}: {} -> {}", uuid, snapshots.get(uuid).getElo(), newElo);
                newElos.put(uuid, newElo);
            }
        }

        for (Team team : teams) {
            for (UUID uuid : teamIdToPlayers.getOrDefault(team.getId(), List.of())) {
                int newElo = newElos.get(uuid);
                PlayerEloSnapshot snapshot = snapshots.get(uuid);

                eloMapper.updateElo(uuid, seasonId, newElo);

                EloHistory history = new EloHistory();
                history.setPlayerUuid(uuid);
                history.setMatchId(matchId);
                history.setRankedSeasonId(seasonId);
                history.setEloBefore(snapshot.getElo());
                history.setEloAfter(newElo);
                history.setEloChange(newElo - snapshot.getElo());
                eloMapper.insertHistory(history);
            }
        }
        log.debug("ELO updates persisted for matchId={}", matchId);
    }

    @Transactional
    public void recomputeSeasonElo(Long seasonId) {
        log.info("Recomputing ELO for seasonId={}", seasonId);
        // Clear dirty flag EN PREMIER — acquiert le row lock sur ranked_season.
        // Toute markEloDirty concurrente sera bloquée jusqu'au commit, puis
        // re-posera le flag → prochain passage du scheduler recompute à nouveau.
        rankedSeasonMapper.clearEloDirty(seasonId);
        eloMapper.deleteHistoryBySeasonId(seasonId);
        eloMapper.resetPlayerSeasonElos(seasonId, eloProperties.startingElo());

        List<Match> matches = matchMapper.findByRankedSeasonId(seasonId);
        log.debug("Replaying {} match(es) for seasonId={}", matches.size(), seasonId);
        for (Match match : matches) {
            List<Team> teams = teamMapper.findByMatchId(match.getId());
            Map<Long, List<UUID>> teamIdToPlayers = new HashMap<>();
            for (Team team : teams) {
                teamIdToPlayers.put(team.getId(), teamPlayerMapper.findPlayerUuidsByTeamId(team.getId()));
            }
            applyEloUpdates(match.getId(), seasonId, teams, teamIdToPlayers);
        }
        List<UUID> zeroMatchUuids = eloMapper.findUuidsWithNoMatches(seasonId);
        eloMapper.deletePlayersWithNoMatches(seasonId);
        eloMapper.updateRankTitlesBySeason(seasonId);
        log.info("ELO recompute complete for seasonId={}, {} match(es) replayed", seasonId, matches.size());

        RankSnapshotPayload snapshot = buildRankSnapshot(seasonId, zeroMatchUuids);
        matchWebhookClient.notifyRanksUpdated(snapshot);
    }

    private RankSnapshotPayload buildRankSnapshot(Long seasonId, List<UUID> zeroMatchUuids) {
        List<PlayerEloSnapshot> activeSnapshots = eloMapper.findAllEloBySeasonId(seasonId);
        List<UUID> allUuids = new ArrayList<>();
        activeSnapshots.forEach(s -> allUuids.add(s.getPlayerUuid()));
        allUuids.addAll(zeroMatchUuids);

        Map<UUID, String> discordIds = playerMapper.findByUuids(allUuids).stream()
                .filter(p -> p.getDiscordId() != null)
                .collect(Collectors.toMap(Player::getUuid, Player::getDiscordId));

        Map<UUID, String> rankTitleByUuid = activeSnapshots.stream()
                .collect(Collectors.toMap(PlayerEloSnapshot::getPlayerUuid, PlayerEloSnapshot::getRankTitle));

        List<PlayerRankEntry> playerRanks = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : discordIds.entrySet()) {
            String title = rankTitleByUuid.get(entry.getKey());
            playerRanks.add(new PlayerRankEntry(entry.getValue(), title));
        }
        return new RankSnapshotPayload("RANKS_UPDATED", seasonId, playerRanks);
    }

}
