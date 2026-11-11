package com.playerscores.service;

import com.playerscores.client.MatchWebhookClient;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.dto.PlayerRankEntry;
import com.playerscores.dto.InitialRating;
import com.playerscores.dto.RankSnapshotPayload;
import com.playerscores.dto.RatingResult;
import com.playerscores.dto.TeamRatingInput;
import com.playerscores.exception.RankedSeasonNotFoundException;
import com.playerscores.mapper.EloMapper;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.EloHistory;
import com.playerscores.model.Match;
import com.playerscores.model.Player;
import com.playerscores.model.RankedSeason;
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
    private final RatingEngines ratingEngines;
    private final MatchMapper matchMapper;
    private final TeamMapper teamMapper;
    private final TeamPlayerMapper teamPlayerMapper;
    private final RankedSeasonMapper rankedSeasonMapper;
    private final PlayerMapper playerMapper;
    private final MatchWebhookClient matchWebhookClient;

    /**
     * Rates one match of a season with the season's own rating algorithm (ADR-0013, REQ-001-05)
     * and records the new ratings and the rating history.
     */
    public void applyEloUpdates(Long matchId, RankedSeason season, List<Team> teams, Map<Long, List<UUID>> teamIdToPlayers) {
        Long seasonId = season.getId();
        RatingEngine engine = ratingEngines.get(season.getRatingAlgorithm());
        InitialRating initial = engine.initialRating();
        for (List<UUID> uuids : teamIdToPlayers.values()) {
            for (UUID uuid : uuids) {
                eloMapper.upsertPlayerSeasonElo(uuid, seasonId, initial.elo(), initial.mu(), initial.sigma());
            }
        }

        List<UUID> allUuids = teamIdToPlayers.values().stream().flatMap(List::stream).toList();
        Map<UUID, PlayerEloSnapshot> snapshots = new HashMap<>();
        for (PlayerEloSnapshot snapshot : eloMapper.findEloByUuidsAndSeason(allUuids, seasonId)) {
            snapshots.put(snapshot.getPlayerUuid(), snapshot);
        }
        log.debug("Loaded rating snapshots for {} player(s) in seasonId={}", snapshots.size(), seasonId);

        List<TeamRatingInput> inputs = new ArrayList<>();
        for (Team team : teams) {
            List<UUID> members = teamIdToPlayers.getOrDefault(team.getId(), List.of());
            if (members.isEmpty()) {
                continue;
            }
            inputs.add(new TeamRatingInput(team.getId(), team.getScore(), members.stream().map(snapshots::get).toList()));
        }

        Map<UUID, RatingResult> results = engine.rateMatch(inputs);

        for (Team team : teams) {
            for (UUID uuid : teamIdToPlayers.getOrDefault(team.getId(), List.of())) {
                RatingResult rating = results.get(uuid);
                PlayerEloSnapshot snapshot = snapshots.get(uuid);
                int newElo = rating.elo();
                log.debug("Rating update ({}) for uuid={}: elo {} -> {} (mu={}, sigma={})",
                        engine.algorithm(), uuid, snapshot.getElo(), newElo, rating.mu(), rating.sigma());

                eloMapper.updateElo(uuid, seasonId, newElo, rating.mu(), rating.sigma());

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
        log.debug("Rating updates persisted for matchId={}", matchId);
    }

    @Transactional
    public void recomputeSeasonElo(Long seasonId) {
        log.info("Recomputing ELO for seasonId={}", seasonId);
        // Clear the dirty flag FIRST — acquires the row lock on ranked_season.
        // Any concurrent markEloDirty blocks until this commits, then re-sets
        // the flag, so the next scheduler pass recomputes again.
        rankedSeasonMapper.clearEloDirty(seasonId);
        RankedSeason season = rankedSeasonMapper.findById(seasonId)
                .orElseThrow(() -> new RankedSeasonNotFoundException(seasonId));
        // REQ-001-06: replay with the season's own rating algorithm
        InitialRating initial = ratingEngines.get(season.getRatingAlgorithm()).initialRating();
        log.info("Replaying seasonId={} with {}", seasonId, season.getRatingAlgorithm());
        eloMapper.deleteHistoryBySeasonId(seasonId);
        eloMapper.resetPlayerSeasonElos(seasonId, initial.elo(), initial.mu(), initial.sigma());

        List<Match> matches = matchMapper.findByRankedSeasonId(seasonId);
        log.debug("Replaying {} match(es) for seasonId={}", matches.size(), seasonId);
        for (Match match : matches) {
            List<Team> teams = teamMapper.findByMatchId(match.getId());
            Map<Long, List<UUID>> teamIdToPlayers = new HashMap<>();
            for (Team team : teams) {
                teamIdToPlayers.put(team.getId(), teamPlayerMapper.findPlayerUuidsByTeamId(team.getId()));
            }
            applyEloUpdates(match.getId(), season, teams, teamIdToPlayers);
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
