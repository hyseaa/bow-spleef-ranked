package net.bowspleef.ranked.service;

import net.bowspleef.ranked.dto.LeaderboardEntryResponse;
import net.bowspleef.ranked.dto.MatchHistoryResponse;
import net.bowspleef.ranked.dto.MatchHistoryRow;
import net.bowspleef.ranked.dto.OpponentRow;
import net.bowspleef.ranked.dto.PlayerSummaryResponse;
import net.bowspleef.ranked.dto.PageResponse;
import net.bowspleef.ranked.dto.PlayerGameTypeStatsResponse;
import net.bowspleef.ranked.dto.PlayerSeasonEloResponse;
import net.bowspleef.ranked.dto.PlayerSeasonEloRow;
import net.bowspleef.ranked.dto.PlayerStatsResponse;
import net.bowspleef.ranked.dto.PlayerResponse;
import net.bowspleef.ranked.dto.WinsLeaderboardEntryResponse;
import net.bowspleef.ranked.exception.PlayerNotFoundException;
import net.bowspleef.ranked.exception.PlayerSeasonEloNotFoundException;
import net.bowspleef.ranked.exception.RankedSeasonNotFoundException;
import net.bowspleef.ranked.mapper.EloMapper;
import net.bowspleef.ranked.mapper.LeaderboardMapper;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.mapper.PlayerStatsMapper;
import net.bowspleef.ranked.mapper.RankedSeasonMapper;
import net.bowspleef.ranked.model.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerMapper playerMapper;
    private final EloMapper eloMapper;
    private final LeaderboardMapper leaderboardMapper;
    private final PlayerStatsMapper playerStatsMapper;
    private final RankedSeasonMapper rankedSeasonMapper;
    private final UsernameCache usernameCache;

    @Transactional
    public PlayerResponse upsertPlayer(UUID uuid) {
        log.info("Upserting player: uuid={}", uuid);
        playerMapper.insertIfAbsent(uuid);
        String username = usernameCache.get(uuid);
        log.info("Player upserted: uuid={}, username={}", uuid, username);
        return new PlayerResponse(uuid, username);
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayer(UUID uuid) {
        log.debug("Fetching player: uuid={}", uuid);
        playerMapper.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn("Player not found: uuid={}", uuid);
                    return new PlayerNotFoundException(uuid);
                });
        return new PlayerResponse(uuid, usernameCache.get(uuid));
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayerByDiscordId(String discordId) {
        log.debug("Fetching player by discordId={}", discordId);
        Player player = playerMapper.findByDiscordId(discordId)
                .orElseThrow(() -> {
                    log.warn("Player not found for discordId={}", discordId);
                    return new PlayerNotFoundException(discordId);
                });
        return new PlayerResponse(player.getUuid(), usernameCache.get(player.getUuid()));
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaderboardEntryResponse> getLeaderboard(Long rankedSeasonId, int page, int size) {
        log.debug("Fetching ELO leaderboard: seasonId={}, page={}, size={}", rankedSeasonId, page, size);
        long total = eloMapper.countLeaderboard(rankedSeasonId);
        List<LeaderboardEntryResponse> content = eloMapper.findLeaderboard(rankedSeasonId, size, page * size)
                .stream()
                .map(row -> new LeaderboardEntryResponse(row.getUuid(), usernameCache.get(row.getUuid()), row.getElo(), row.getMatchesPlayed(), row.getWins(), row.getTitle(),
                        row.getGamesPlayed(), row.getGamesWon()))
                .toList();
        log.debug("Leaderboard fetched: seasonId={}, total={}, returned={}", rankedSeasonId, total, content.size());
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<WinsLeaderboardEntryResponse> getWinsLeaderboard(String gameType, int page, int size) {
        log.debug("Fetching wins leaderboard: gameType={}, page={}, size={}", gameType, page, size);
        long total = leaderboardMapper.countLeaderboard(gameType);
        List<WinsLeaderboardEntryResponse> content = leaderboardMapper.findLeaderboard(gameType, size, page * size)
                .stream()
                .map(row -> new WinsLeaderboardEntryResponse(row.getUuid(), usernameCache.get(row.getUuid()), row.getWins(), row.getGamesWon()))
                .toList();
        log.debug("Wins leaderboard fetched: gameType={}, total={}, returned={}", gameType, total, content.size());
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public PlayerStatsResponse getPlayerStats(UUID uuid) {
        log.debug("Fetching stats for player: uuid={}", uuid);
        playerMapper.findByUuid(uuid).orElseThrow(() -> {
            log.warn("Player not found: uuid={}", uuid);
            return new PlayerNotFoundException(uuid);
        });

        List<PlayerGameTypeStatsResponse> gameTypes = playerStatsMapper.findNonRankedStats(uuid).stream()
                .map(row -> new PlayerGameTypeStatsResponse(row.getGameType(), row.getGameTypeDisplayName(),
                        row.getMatchesPlayed(), row.getWins(),
                        row.getMatchesPlayed() - row.getWins() - row.getDraws(), row.getDraws(),
                        row.getGamesWon() + row.getGamesLost() + row.getGamesDrawn(),
                        row.getGamesWon(), row.getGamesLost(), row.getGamesDrawn()))
                .toList();
        PlayerStatsResponse response = new PlayerStatsResponse(uuid, playerStatsMapper.findActiveRankedStats(uuid), gameTypes);
        log.debug("Stats fetched for uuid={}: nonRankedGameTypes={}, rankedSeasons={}", uuid, gameTypes.size(), response.ranked().size());
        return response;
    }

    @Transactional(readOnly = true)
    public PlayerSeasonEloResponse getPlayerSeasonElo(UUID uuid, Long seasonId) {
        log.debug("Fetching season ELO: uuid={}, seasonId={}", uuid, seasonId);
        playerMapper.findByUuid(uuid).orElseThrow(() -> {
            log.warn("Player not found: uuid={}", uuid);
            return new PlayerNotFoundException(uuid);
        });
        rankedSeasonMapper.findById(seasonId).orElseThrow(() -> {
            log.warn("Ranked season not found: seasonId={}", seasonId);
            return new RankedSeasonNotFoundException(seasonId);
        });

        PlayerSeasonEloRow row = playerStatsMapper.findSeasonElo(uuid, seasonId)
                .orElseThrow(() -> {
                    log.warn("No ELO entry for uuid={} in seasonId={}", uuid, seasonId);
                    return new PlayerSeasonEloNotFoundException(uuid, seasonId);
                });
        log.debug("Season ELO fetched: uuid={}, seasonId={}, elo={}", uuid, seasonId, row.getElo());
        return new PlayerSeasonEloResponse(uuid, row.getSeasonId(), row.getSeasonName(), row.getGameType(), row.getElo(), row.getMatchesPlayed(),
                row.getGamesPlayed(), row.getGamesWon(), row.getGamesLost(), row.getTitle());
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchHistoryResponse> getEloHistory(UUID uuid, Long rankedSeasonId, int page, int size) {
        log.debug("Fetching match history: uuid={}, seasonId={}, page={}, size={}", uuid, rankedSeasonId, page, size);
        long total = eloMapper.countHistoryByPlayerAndSeason(uuid, rankedSeasonId);
        List<MatchHistoryRow> rows = eloMapper.findMatchHistoryByPlayerAndSeason(uuid, rankedSeasonId, size, page * size);

        if (rows.isEmpty()) {
            return PageResponse.of(List.of(), page, size, total);
        }

        List<Long> matchIds = rows.stream().map(MatchHistoryRow::getMatchId).toList();
        Map<Long, List<PlayerSummaryResponse>> opponentsByMatch = eloMapper.findOpponentsByMatchIds(matchIds, uuid)
                .stream()
                .collect(Collectors.groupingBy(
                        OpponentRow::getMatchId,
                        Collectors.mapping(
                                r -> new PlayerSummaryResponse(r.getUuid(), usernameCache.get(r.getUuid())),
                                Collectors.toList()
                        )
                ));

        List<MatchHistoryResponse> content = rows.stream()
                .map(r -> new MatchHistoryResponse(
                        r.getMatchId(),
                        r.getPlayedAt(),
                        r.getEloChange(),
                        r.getEloAfter(),
                        opponentsByMatch.getOrDefault(r.getMatchId(), List.of())
                ))
                .toList();
        log.debug("Match history fetched: uuid={}, seasonId={}, total={}, returned={}", uuid, rankedSeasonId, total, content.size());
        return PageResponse.of(content, page, size, total);
    }
}
