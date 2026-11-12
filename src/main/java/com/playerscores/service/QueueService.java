package com.playerscores.service;

import com.playerscores.dto.PlayerSeasonEloRow;
import com.playerscores.dto.QueueByGameTypeResponse;
import com.playerscores.dto.QueueResponse;
import com.playerscores.dto.QueuedPlayerInfo;
import com.playerscores.exception.NoActiveRankedSeasonException;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.exception.QueueTeamGameTypeException;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final int DEFAULT_ELO = 1000;

    @Value("${queue.elo-tolerance:100}")
    private int eloTolerance;

    @Value("${queue.entry-ttl-minutes:60}")
    private int entryTtlMinutes;

    private final PlayerMapper playerMapper;
    private final RankedSeasonMapper rankedSeasonMapper;
    private final PlayerStatsMapper playerStatsMapper;
    private final QueueMapper queueMapper;
    private final GameTypeMapper gameTypeMapper;
    private final UsernameCache usernameCache;

    @Transactional
    public QueueResponse join(String discordId, String gameType) {
        log.info("Player joining queue: discordId={}, gameType={}", discordId, gameType);

        // KI-03: a queue match becomes a 1v1 duel, which a team game type cannot be; matchmaking for teams is
        // a later project.
        gameTypeMapper.findByName(gameType).ifPresent(gt -> {
            if (gt.getTeamSize() > 1) {
                log.warn("Queue join refused: gameType={} has teamSize={}", gameType, gt.getTeamSize());
                throw new QueueTeamGameTypeException(gameType, gt.getTeamSize());
            }
        });

        Player player = playerMapper.findByDiscordId(discordId)
                .orElseThrow(() -> {
                    log.warn("Queue join failed: player not found for discordId={}", discordId);
                    return new PlayerNotFoundException(discordId);
                });

        RankedSeason season = rankedSeasonMapper.findActiveByGameType(gameType)
                .orElseThrow(() -> {
                    log.warn("Queue join failed: no active ranked season for gameType={}", gameType);
                    return new NoActiveRankedSeasonException();
                });

        int elo = playerStatsMapper.findSeasonElo(player.getUuid(), season.getId())
                .map(PlayerSeasonEloRow::getElo)
                .orElse(DEFAULT_ELO);
        log.debug("Player uuid={} queuing with elo={} (seasonId={})", player.getUuid(), elo, season.getId());

        QueueEntry entry = new QueueEntry();
        entry.setPlayerUuid(player.getUuid());
        entry.setGameType(gameType);
        entry.setRankedSeasonId(season.getId());
        entry.setElo(elo);
        queueMapper.upsert(entry);

        Optional<QueueEntry> opponent = queueMapper.findMatch(
                player.getUuid(), gameType, season.getId(),
                elo, elo - eloTolerance, elo + eloTolerance);

        if (opponent.isPresent()) {
            QueueEntry opp = opponent.get();
            log.info("Match found: uuid={} (elo={}) vs uuid={} (elo={}), gameType={}",
                    player.getUuid(), elo, opp.getPlayerUuid(), opp.getElo(), gameType);
            queueMapper.delete(player.getUuid(), gameType);
            queueMapper.delete(opp.getPlayerUuid(), gameType);

            return new QueueResponse(true,
                    new QueuedPlayerInfo(opp.getPlayerUuid(), usernameCache.get(opp.getPlayerUuid()), opp.getElo()),
                    entryTtlMinutes);
        }

        log.info("No match found yet for uuid={} (elo={}), staying in queue", player.getUuid(), elo);
        return new QueueResponse(false, null, entryTtlMinutes);
    }

    @Transactional(readOnly = true)
    public List<QueueByGameTypeResponse> listQueue() {
        log.debug("Listing all queued players");
        Map<String, List<QueueEntry>> byGameType = queueMapper.findAll()
                .stream()
                .collect(Collectors.groupingBy(QueueEntry::getGameType));

        List<QueueByGameTypeResponse> result = byGameType.entrySet().stream()
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
        log.debug("Queue listed: {} game type(s), {} total player(s)",
                result.size(), result.stream().mapToInt(r -> r.players().size()).sum());
        return result;
    }

    @Transactional
    public void leave(String discordId, String gameType) {
        log.info("Player leaving queue: discordId={}, gameType={}", discordId, gameType);
        Player player = playerMapper.findByDiscordId(discordId)
                .orElseThrow(() -> {
                    log.warn("Queue leave failed: player not found for discordId={}", discordId);
                    return new PlayerNotFoundException(discordId);
                });
        queueMapper.delete(player.getUuid(), gameType);
        log.info("Player removed from queue: uuid={}, gameType={}", player.getUuid(), gameType);
    }
}
