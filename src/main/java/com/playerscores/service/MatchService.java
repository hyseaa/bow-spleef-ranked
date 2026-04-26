package com.playerscores.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.client.MatchWebhookClient;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.PlayerEloSnapshot;
import com.playerscores.dto.MatchListResponse;
import com.playerscores.dto.MatchResponse;
import com.playerscores.dto.MatchWebhookPayload;
import com.playerscores.dto.PlayerRankEntry;
import com.playerscores.dto.PlayerSummaryResponse;
import com.playerscores.dto.TeamRequest;
import com.playerscores.dto.TeamResponse;
import com.playerscores.exception.GameTypeNotFoundException;
import com.playerscores.exception.MatchNotFoundException;
import com.playerscores.exception.NoActiveRankedSeasonException;
import com.playerscores.mapper.EloMapper;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.MatchPlayerStatMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.GameType;
import com.playerscores.model.Match;
import com.playerscores.model.MatchPlayerStat;
import com.playerscores.model.Player;
import com.playerscores.model.RankedSeason;
import com.playerscores.model.Team;
import com.playerscores.model.TeamPlayer;
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
public class MatchService {

    private final MatchMapper matchMapper;
    private final TeamMapper teamMapper;
    private final TeamPlayerMapper teamPlayerMapper;
    private final MatchPlayerStatMapper matchPlayerStatMapper;
    private final PlayerMapper playerMapper;
    private final EloRecomputeService eloRecomputeService;
    private final EloMapper eloMapper;
    private final UsernameCache usernameCache;
    private final ObjectMapper objectMapper;
    private final GameTypeMapper gameTypeMapper;
    private final RankedSeasonMapper rankedSeasonMapper;
    private final MatchWebhookClient matchWebhookClient;

    @Transactional
    public MatchResponse createMatch(CreateMatchRequest request) {
        log.info("Creating match: gameType={}, teams={}", request.gameType(), request.teams().size());

        for (TeamRequest teamReq : request.teams()) {
            for (UUID uuid : teamReq.playerUuids()) {
                playerMapper.insertIfAbsent(uuid);
            }
        }

        GameType gameType = gameTypeMapper.findByName(request.gameType())
                .orElseThrow(() -> {
                    log.warn("Match creation failed: game type not found: {}", request.gameType());
                    return new GameTypeNotFoundException(request.gameType());
                });

        Match match = new Match();
        match.setGameType(request.gameType());
        match.setSource(request.source());
        matchMapper.insert(match);
        log.debug("Match row inserted: id={}", match.getId());

        // teamId → list of player UUIDs (needed for ELO calculation)
        Map<Long, List<UUID>> teamIdToPlayers = new HashMap<>();
        List<Team> insertedTeams = new ArrayList<>();

        for (TeamRequest teamReq : request.teams()) {
            Team team = new Team();
            team.setMatchId(match.getId());
            team.setScore(teamReq.score());
            teamMapper.insert(team);
            insertedTeams.add(team);

            List<UUID> teamPlayers = new ArrayList<>();
            for (UUID uuid : teamReq.playerUuids()) {
                TeamPlayer tp = new TeamPlayer();
                tp.setTeamId(team.getId());
                tp.setPlayerUuid(uuid);
                teamPlayerMapper.insert(tp);
                teamPlayers.add(uuid);

                Map<String, Object> stats = request.playerStats() != null ? request.playerStats().get(uuid) : null;
                if (stats != null) {
                    MatchPlayerStat stat = new MatchPlayerStat();
                    stat.setTeamPlayerId(tp.getId());
                    try {
                        stat.setStats(objectMapper.writeValueAsString(stats));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize stats for player uuid={}: {}", uuid, e.getMessage(), e);
                        throw new IllegalArgumentException("Invalid stats for player " + uuid, e);
                    }
                    matchPlayerStatMapper.insert(stat);
                }
            }
            teamIdToPlayers.put(team.getId(), teamPlayers);
        }

        if (gameType.isRanked()) {
            RankedSeason season = rankedSeasonMapper.findActiveByGameType(request.gameType())
                    .orElseThrow(() -> {
                        log.warn("Match creation failed: no active ranked season for gameType={}", request.gameType());
                        return new NoActiveRankedSeasonException();
                    });
            matchMapper.updateRankedSeasonId(match.getId(), season.getId());
            match.setRankedSeasonId(season.getId());
            log.debug("Applying ELO updates for matchId={}, seasonId={}", match.getId(), season.getId());
            eloRecomputeService.applyEloUpdates(match.getId(), season.getId(), insertedTeams, teamIdToPlayers);
            eloMapper.updateRankTitlesBySeason(season.getId());
        }

        log.info("Match created: id={}, gameType={}, ranked={}", match.getId(), match.getGameType(), gameType.isRanked());
        MatchResponse response = getMatch(match.getId());
        List<UUID> allUuids = teamIdToPlayers.values().stream().flatMap(List::stream).toList();
        List<PlayerRankEntry> playerRanks = buildPlayerRanks(allUuids, match.getRankedSeasonId());
        matchWebhookClient.notifyMatchResult(new MatchWebhookPayload(response, playerRanks));
        return response;
    }

    @Transactional(readOnly = true)
    public MatchListResponse getMatchesByGameType(String gameType, int page, int size) {
        log.debug("Fetching matches by gameType: gameType={}, page={}, size={}", gameType, page, size);
        GameType gt = gameTypeMapper.findByName(gameType).orElseThrow(() -> {
            log.warn("Game type not found: {}", gameType);
            return new GameTypeNotFoundException(gameType);
        });
        long total = matchMapper.countByGameType(gameType);
        List<MatchResponse> content = matchMapper.findByGameType(gameType, size, page * size)
                .stream()
                .map(match -> getMatch(match.getId()))
                .toList();
        log.debug("Matches fetched: gameType={}, total={}, returned={}", gameType, total, content.size());
        return MatchListResponse.of(gameType, gt.getDisplayName(), content, page, size, total);
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long id) {
        log.debug("Fetching match: id={}", id);
        Match match = matchMapper.findById(id).orElseThrow(() -> {
            log.warn("Match not found: id={}", id);
            return new MatchNotFoundException(id);
        });

        Map<UUID, Map<String, Object>> playerStats = new HashMap<>();

        List<TeamResponse> teams = teamMapper.findByMatchId(id).stream()
                .map(team -> {
                    List<PlayerSummaryResponse> players = teamPlayerMapper.findByTeamId(team.getId())
                            .stream()
                            .map(tp -> {
                                matchPlayerStatMapper.findStatsByTeamPlayerId(tp.getId()).ifPresent(json -> {
                                    try {
                                        playerStats.put(tp.getPlayerUuid(),
                                                objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {}));
                                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                                        log.warn("Failed to deserialize stats for teamPlayerId={}: {}", tp.getId(), e.getMessage());
                                    }
                                });
                                return new PlayerSummaryResponse(tp.getPlayerUuid(), usernameCache.get(tp.getPlayerUuid()));
                            })
                            .toList();
                    return new TeamResponse(team.getId(), team.getScore(), players);
                })
                .toList();

        String displayName = gameTypeMapper.findByName(match.getGameType())
                .map(GameType::getDisplayName)
                .orElse(match.getGameType());

        log.debug("Match fetched: id={}, gameType={}, teams={}", id, match.getGameType(), teams.size());
        Map<UUID, Map<String, Object>> stats = playerStats.isEmpty() ? null : playerStats;
        return new MatchResponse(match.getId(), match.getGameType(), displayName,
                match.getSource(), match.getPlayedAt(), teams, match.getRankedSeasonId(), stats);
    }

    private List<PlayerRankEntry> buildPlayerRanks(List<UUID> uuids, Long rankedSeasonId) {
        if (rankedSeasonId == null || uuids.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> discordIds = playerMapper.findByUuids(uuids).stream()
                .filter(p -> p.getDiscordId() != null)
                .collect(Collectors.toMap(Player::getUuid, Player::getDiscordId));
        if (discordIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> rankTitleByUuid = eloMapper.findEloByUuidsAndSeason(uuids, rankedSeasonId).stream()
                .collect(Collectors.toMap(PlayerEloSnapshot::getPlayerUuid, PlayerEloSnapshot::getRankTitle));
        List<PlayerRankEntry> ranks = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : discordIds.entrySet()) {
            String title = rankTitleByUuid.get(entry.getKey());
            if (title != null) {
                ranks.add(new PlayerRankEntry(entry.getValue(), title));
            }
        }
        return ranks;
    }

    @Transactional
    public void deleteMatch(Long id) {
        log.info("Deleting match: id={}", id);
        Match match = matchMapper.findById(id).orElseThrow(() -> {
            log.warn("Match not found for deletion: id={}", id);
            return new MatchNotFoundException(id);
        });
        matchMapper.deleteById(id);
        if (match.getRankedSeasonId() != null) {
            rankedSeasonMapper.markEloDirty(match.getRankedSeasonId());
            log.debug("Season marked dirty for ELO recompute: seasonId={}", match.getRankedSeasonId());
        }
        log.info("Match deleted: id={}, ranked={}", id, match.getRankedSeasonId() != null);
    }
}
