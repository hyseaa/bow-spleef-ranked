package com.playerscores.service;

import com.playerscores.dto.LeaderboardEntryResponse;
import com.playerscores.dto.PageResponse;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.LeaderboardMapper;
import com.playerscores.mapper.PlayerMapper;
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
    private final LeaderboardMapper leaderboardMapper;
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
    public PageResponse<LeaderboardEntryResponse> getLeaderboard(int page, int size) {
        long total = leaderboardMapper.countLeaderboard();
        List<LeaderboardEntryResponse> content = leaderboardMapper.findLeaderboard(size, page * size)
                .stream()
                .map(row -> new LeaderboardEntryResponse(row.uuid(), usernameCache.get(row.uuid()), row.wins()))
                .toList();
        return PageResponse.of(content, page, size, total);
    }
}
