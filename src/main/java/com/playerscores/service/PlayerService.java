package com.playerscores.service;

import com.playerscores.dto.EloHistoryResponse;
import com.playerscores.dto.LeaderboardEntryResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        playerMapper.insertIfAbsent(uuid);
        return new PlayerResponse(uuid, usernameCache.get(uuid));
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayer(UUID uuid) {
        playerMapper.findByUuid(uuid)
                .orElseThrow(() -> new PlayerNotFoundException(uuid));
        return new PlayerResponse(uuid, usernameCache.get(uuid));
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayerByDiscordId(String discordId) {
        Player player = playerMapper.findByDiscordId(discordId)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
        return new PlayerResponse(player.getUuid(), usernameCache.get(player.getUuid()));
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaderboardEntryResponse> getLeaderboard(Long rankedSeasonId, int page, int size) {
        long total = eloMapper.countLeaderboard(rankedSeasonId);
        List<LeaderboardEntryResponse> content = eloMapper.findLeaderboard(rankedSeasonId, size, page * size)
                .stream()
                .map(row -> new LeaderboardEntryResponse(row.uuid(), usernameCache.get(row.uuid()), row.elo(), row.matchesPlayed(), row.wins(), row.title()))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<WinsLeaderboardEntryResponse> getWinsLeaderboard(String gameType, int page, int size) {
        long total = leaderboardMapper.countLeaderboard(gameType);
        List<WinsLeaderboardEntryResponse> content = leaderboardMapper.findLeaderboard(gameType, size, page * size)
                .stream()
                .map(row -> new WinsLeaderboardEntryResponse(row.uuid(), usernameCache.get(row.uuid()), row.wins()))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public PlayerStatsResponse getPlayerStats(UUID uuid) {
        playerMapper.findByUuid(uuid).orElseThrow(() -> new PlayerNotFoundException(uuid));

        PlayerCasualStatsRow casual = playerStatsMapper.findCasualStats(uuid);
        return new PlayerStatsResponse(
                uuid,
                new PlayerCasualStatsResponse(casual.matchesPlayed(), casual.wins(), casual.matchesPlayed() - casual.wins()),
                playerStatsMapper.findActiveRankedStats(uuid)
        );
    }

    @Transactional(readOnly = true)
    public PlayerSeasonEloResponse getPlayerSeasonElo(UUID uuid, Long seasonId) {
        playerMapper.findByUuid(uuid).orElseThrow(() -> new PlayerNotFoundException(uuid));
        rankedSeasonMapper.findById(seasonId).orElseThrow(() -> new RankedSeasonNotFoundException(seasonId));

        PlayerSeasonEloRow row = playerStatsMapper.findSeasonElo(uuid, seasonId)
                .orElseThrow(() -> new PlayerSeasonEloNotFoundException(uuid, seasonId));
        return new PlayerSeasonEloResponse(uuid, row.seasonId(), row.seasonName(), row.gameType(), row.elo(), row.matchesPlayed(), row.title());
    }

    @Transactional(readOnly = true)
    public PageResponse<EloHistoryResponse> getEloHistory(UUID uuid, Long rankedSeasonId, int page, int size) {
        long total = eloMapper.countHistoryByPlayerAndSeason(uuid, rankedSeasonId);
        List<EloHistoryResponse> content = eloMapper.findHistoryByPlayerAndSeason(uuid, rankedSeasonId, size, page * size)
                .stream()
                .map(h -> new EloHistoryResponse(h.getMatchId(), h.getEloBefore(), h.getEloAfter(), h.getEloChange(), h.getRecordedAt()))
                .toList();
        return PageResponse.of(content, page, size, total);
    }
}
