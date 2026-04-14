package com.playerscores.service;

import com.playerscores.dto.PlayerSeasonEloRow;
import com.playerscores.dto.QueueByGameTypeResponse;
import com.playerscores.dto.QueueResponse;
import com.playerscores.dto.QueuedPlayerInfo;
import com.playerscores.exception.NoActiveRankedSeasonException;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.PlayerStatsMapper;
import com.playerscores.mapper.QueueMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.GameType;
import com.playerscores.model.Player;
import com.playerscores.model.QueueEntry;
import com.playerscores.model.RankedSeason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final int ELO_TOLERANCE = 100;
    private static final int DEFAULT_ELO = 1000;

    private final PlayerMapper playerMapper;
    private final RankedSeasonMapper rankedSeasonMapper;
    private final PlayerStatsMapper playerStatsMapper;
    private final QueueMapper queueMapper;
    private final GameTypeMapper gameTypeMapper;
    private final UsernameCache usernameCache;

    @Transactional
    public QueueResponse join(String discordId, String gameType) {
        Player player = playerMapper.findByDiscordId(discordId)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));

        RankedSeason season = rankedSeasonMapper.findActiveByGameType(gameType)
                .orElseThrow(NoActiveRankedSeasonException::new);

        int elo = playerStatsMapper.findSeasonElo(player.getUuid(), season.getId())
                .map(PlayerSeasonEloRow::elo)
                .orElse(DEFAULT_ELO);

        QueueEntry entry = new QueueEntry();
        entry.setPlayerUuid(player.getUuid());
        entry.setGameType(gameType);
        entry.setRankedSeasonId(season.getId());
        entry.setElo(elo);
        queueMapper.upsert(entry);

        Optional<QueueEntry> opponent = queueMapper.findMatch(
                player.getUuid(), gameType, season.getId(),
                elo, elo - ELO_TOLERANCE, elo + ELO_TOLERANCE);

        if (opponent.isPresent()) {
            QueueEntry opp = opponent.get();
            queueMapper.delete(player.getUuid(), gameType);
            queueMapper.delete(opp.getPlayerUuid(), gameType);

            return new QueueResponse(true,
                    new QueuedPlayerInfo(opp.getPlayerUuid(), usernameCache.get(opp.getPlayerUuid()), opp.getElo()));
        }

        return new QueueResponse(false, null);
    }

    @Transactional(readOnly = true)
    public List<QueueByGameTypeResponse> listQueue() {
        Map<String, List<QueueEntry>> byGameType = queueMapper.findAll()
                .stream()
                .collect(Collectors.groupingBy(QueueEntry::getGameType));

        return byGameType.entrySet().stream()
                .map(e -> {
                    String gameType = e.getKey();
                    String displayName = gameTypeMapper.findByName(gameType)
                            .map(GameType::getDisplayName)
                            .orElse(gameType);
                    List<QueuedPlayerInfo> players = e.getValue().stream()
                            .map(entry -> new QueuedPlayerInfo(entry.getPlayerUuid(), usernameCache.get(entry.getPlayerUuid()), entry.getElo()))
                            .toList();
                    return new QueueByGameTypeResponse(gameType, displayName, players);
                })
                .toList();
    }

    @Transactional
    public void leave(String discordId, String gameType) {
        Player player = playerMapper.findByDiscordId(discordId)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
        queueMapper.delete(player.getUuid(), gameType);
    }
}
