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
import com.playerscores.exception.DuplicatePlayerInMatchException;
import com.playerscores.exception.GameTypeNotFoundException;
import com.playerscores.exception.IdempotencyKeyReusedException;
import com.playerscores.exception.InvalidTeamSizeException;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Records a match with no idempotency key: a repeated call inserts a new match every time. The only
     * caller, {@code DuelChallengeService#confirmScores}, guarantees at most one call per duel challenge
     * via a compare-and-swap claim on the challenge's status. A caller that can be retried must use
     * {@link #recordMatch} with an idempotency key.
     */
    // NoActiveRankedSeasonException is thrown before anything is written (only reads and locks have happened), so
    // it must not mark the surrounding transaction rollback-only: a duel confirmation commits its own cancellation
    // when it hits it (spec 003). Keep that check before any write.
    @Transactional(noRollbackFor = NoActiveRankedSeasonException.class)
    public MatchResponse createMatch(CreateMatchRequest request) {
        return recordMatch(request, null).match();
    }

    /**
     * Records a match, at most once per {@code (source, idempotencyKey)} (spec 002). With a key, a
     * transaction-scoped lock serializes concurrent requests carrying it; if a match already holds the key,
     * it is returned untouched (no rating, history or webhook) when the content is the same, and refused
     * with {@link IdempotencyKeyReusedException} when it is not. A failure rolls the key back with the match.
     * Lock order: idempotency lock first, then the season lock (KI-28).
     *
     * @param idempotencyKey the caller's key, or null for no protection
     */
    @Transactional(noRollbackFor = NoActiveRankedSeasonException.class)
    public MatchRecording recordMatch(CreateMatchRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            matchMapper.lockIdempotencyKey(request.source(), idempotencyKey);
            Optional<Match> existing = matchMapper.findByIdempotencyKey(request.source(), idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), request, idempotencyKey);
            }
        }
        log.info("Creating match: gameType={}, teams={}", request.gameType(), request.teams().size());

        GameType gameType = gameTypeMapper.findByName(request.gameType())
                .orElseThrow(() -> {
                    log.warn("Match creation failed: game type not found: {}", request.gameType());
                    return new GameTypeNotFoundException(request.gameType());
                });

        validateTeams(request, gameType);

        // KI-28: lock the season before anything else, so that the matches of a season are rated one at
        // a time, in the order of their played_at.
        RankedSeason season = null;
        if (gameType.isRanked()) {
            season = rankedSeasonMapper.lockActiveByGameType(request.gameType())
                    .orElseThrow(() -> {
                        log.warn("Match creation failed: no active ranked season for gameType={}", request.gameType());
                        return new NoActiveRankedSeasonException();
                    });
        }

        for (TeamRequest teamReq : request.teams()) {
            for (UUID uuid : teamReq.playerUuids()) {
                playerMapper.insertIfAbsent(uuid);
            }
        }

        // KI-06: titles are percentiles, so this match can change the title of players who were not in it. Remember
        // the season's titles now to tell the bot about every one that changed.
        Map<UUID, String> titlesBefore = season != null ? titlesOfSeason(season.getId()) : Map.of();

        Match match = new Match();
        match.setGameType(request.gameType());
        match.setSource(request.source());
        match.setIdempotencyKey(idempotencyKey);
        matchMapper.insert(match);
        log.debug("Match row inserted: id={}", match.getId());

        // teamId → list of player UUIDs (needed for ELO calculation)
        List<UUID> titleChangedUuids = List.of();
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

        if (season != null) {
            matchMapper.updateRankedSeasonId(match.getId(), season.getId());
            match.setRankedSeasonId(season.getId());
            log.debug("Applying ELO updates for matchId={}, seasonId={}", match.getId(), season.getId());
            eloRecomputeService.applyEloUpdates(match.getId(), season, insertedTeams, teamIdToPlayers);
            eloMapper.updateRankTitlesBySeason(season.getId());
            titleChangedUuids = playersWhoseTitleChanged(titlesBefore, titlesOfSeason(season.getId()));
        }

        log.info("Match created: id={}, gameType={}, ranked={}", match.getId(), match.getGameType(), gameType.isRanked());
        MatchResponse response = getMatch(match.getId());
        Set<UUID> reported = new LinkedHashSet<>(teamIdToPlayers.values().stream().flatMap(List::stream).toList());
        reported.addAll(titleChangedUuids);
        List<PlayerRankEntry> playerRanks = buildPlayerRanks(new ArrayList<>(reported), match.getRankedSeasonId());
        matchWebhookClient.notifyMatchResult(new MatchWebhookPayload(response, playerRanks));
        return new MatchRecording(response, true);
    }

    private MatchRecording replay(Match existing, CreateMatchRequest request, String idempotencyKey) {
        MatchResponse stored = getMatch(existing.getId());
        if (!stored.gameType().equals(request.gameType()) || !teamsOf(stored).equals(teamsOf(request))) {
            log.warn("Idempotency key reused with a different match: source={}, key={}, existingMatchId={}",
                    request.source(), idempotencyKey, existing.getId());
            throw new IdempotencyKeyReusedException(request.source(), idempotencyKey);
        }
        log.info("Idempotent replay: source={}, key={}, matchId={}", request.source(), idempotencyKey, existing.getId());
        return new MatchRecording(stored, false);
    }

    /** The teams of a stored match as comparable strings, independent of order. Stats are ignored. */
    private static List<String> teamsOf(MatchResponse match) {
        return match.teams().stream()
                .map(team -> teamKey(team.score(), team.players().stream().map(PlayerSummaryResponse::uuid)))
                .sorted()
                .toList();
    }

    private static List<String> teamsOf(CreateMatchRequest request) {
        return request.teams().stream()
                .map(team -> teamKey(team.score(), team.playerUuids().stream()))
                .sorted()
                .toList();
    }

    private static String teamKey(Integer score, java.util.stream.Stream<UUID> players) {
        return score + ":" + players.map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    /**
     * Ranked game types require exactly teamSize players per team; duplicate players
     * (within or across teams) are rejected for every match.
     */
    private void validateTeams(CreateMatchRequest request, GameType gameType) {
        Set<UUID> seen = new HashSet<>();
        for (TeamRequest teamReq : request.teams()) {
            if (gameType.isRanked() && teamReq.playerUuids().size() != gameType.getTeamSize()) {
                log.warn("Match creation failed: invalid team size for gameType={}: expected={}, actual={}",
                        gameType.getName(), gameType.getTeamSize(), teamReq.playerUuids().size());
                throw new InvalidTeamSizeException(gameType.getName(), gameType.getTeamSize(), teamReq.playerUuids().size());
            }
            for (UUID uuid : teamReq.playerUuids()) {
                if (!seen.add(uuid)) {
                    log.warn("Match creation failed: duplicate player in match: uuid={}", uuid);
                    throw new DuplicatePlayerInMatchException(uuid);
                }
            }
        }
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

    private Map<UUID, String> titlesOfSeason(Long seasonId) {
        Map<UUID, String> titles = new HashMap<>();
        for (PlayerEloSnapshot snapshot : eloMapper.findAllEloBySeasonId(seasonId)) {
            titles.put(snapshot.getPlayerUuid(), snapshot.getRankTitle());
        }
        return titles;
    }

    /** The players whose title is not what it was before (including a player new to the season). */
    private static List<UUID> playersWhoseTitleChanged(Map<UUID, String> before, Map<UUID, String> after) {
        return after.entrySet().stream()
                .filter(e -> !Objects.equals(before.get(e.getKey()), e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
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
