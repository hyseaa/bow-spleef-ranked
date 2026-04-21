package com.playerscores.service;

import com.playerscores.dto.LeaderboardEntryResponse;
import com.playerscores.dto.MatchHistoryResponse;
import com.playerscores.dto.MatchHistoryRow;
import com.playerscores.dto.OpponentRow;
import com.playerscores.dto.PlayerSummaryResponse;
import com.playerscores.dto.PageResponse;
import com.playerscores.dto.PlayerCasualStatsResponse;
import com.playerscores.dto.PlayerCasualStatsRow;
import com.playerscores.dto.PlayerSeasonEloResponse;
import com.playerscores.dto.PlayerSeasonEloRow;
import com.playerscores.dto.PlayerStatsResponse;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.dto.WinsLeaderboardEntryResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.exception.PlayerSeasonEloNotFoundException;
import com.playerscores.exception.RankedSeasonNotFoundException;
import com.playerscores.mapper.EloMapper;
import com.playerscores.mapper.LeaderboardMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.PlayerStatsMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.Player;
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
                .map(row -> new LeaderboardEntryResponse(row.getUuid(), usernameCache.get(row.getUuid()), row.getElo(), row.getMatchesPlayed(), row.getWins(), row.getTitle()))
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
                .map(row -> new WinsLeaderboardEntryResponse(row.getUuid(), usernameCache.get(row.getUuid()), row.getWins()))
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

        PlayerCasualStatsRow casual = playerStatsMapper.findCasualStats(uuid);
        PlayerStatsResponse response = new PlayerStatsResponse(
                uuid,
                new PlayerCasualStatsResponse(casual.getMatchesPlayed(), casual.getWins(), casual.getMatchesPlayed() - casual.getWins()),
                playerStatsMapper.findActiveRankedStats(uuid)
        );
        log.debug("Stats fetched for uuid={}: casualMatches={}, rankedSeasons={}", uuid, casual.getMatchesPlayed(), response.ranked().size());
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
        return new PlayerSeasonEloResponse(uuid, row.getSeasonId(), row.getSeasonName(), row.getGameType(), row.getElo(), row.getMatchesPlayed(), row.getTitle());
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
