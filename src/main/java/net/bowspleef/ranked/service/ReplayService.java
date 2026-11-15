package net.bowspleef.ranked.service;

import lombok.extern.slf4j.Slf4j;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.PlayerSummaryResponse;
import net.bowspleef.ranked.dto.ReplayAddedPayload;
import net.bowspleef.ranked.dto.ReplayFile;
import net.bowspleef.ranked.dto.ReplayPlayerRow;
import net.bowspleef.ranked.dto.ReplaySummaryResponse;
import net.bowspleef.ranked.dto.ReplaySummaryRow;
import net.bowspleef.ranked.exception.InvalidReplayException;
import net.bowspleef.ranked.exception.MatchNotFoundException;
import net.bowspleef.ranked.exception.ReplayAlreadyExistsException;
import net.bowspleef.ranked.exception.ReplayNotFoundException;
import net.bowspleef.ranked.exception.ReplayTooLargeException;
import net.bowspleef.ranked.mapper.MatchMapper;
import net.bowspleef.ranked.mapper.ReplayMapper;
import net.bowspleef.ranked.model.MatchReplay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The replays of the matches (spec 024, ADR-0026): the plugin's file, kept as it comes, one per match, for ever and
 * deleted with its match. The same file sent again is recognised by its SHA-256 (a retry after a lost answer).
 */
@Slf4j
@Service
public class ReplayService {

    /** An hour of game: far above any game, a bound against a wrong value. */
    public static final int MAX_DURATION_TICKS = 72_000;
    public static final int DEFAULT_LIST_SIZE = 10;
    public static final int MAX_LIST_SIZE = 50;
    private static final Pattern MAP_ID = Pattern.compile("[a-z0-9_-]{1,64}");

    private final MatchMapper matchMapper;
    private final ReplayMapper replayMapper;
    private final MatchWebhookClient webhookClient;
    private final UsernameCache usernameCache;
    private final long maxSizeBytes;

    public ReplayService(MatchMapper matchMapper, ReplayMapper replayMapper, MatchWebhookClient webhookClient,
                         UsernameCache usernameCache, @Value("${replay.max-size-bytes:2097152}") long maxSizeBytes) {
        this.matchMapper = matchMapper;
        this.replayMapper = replayMapper;
        this.webhookClient = webhookClient;
        this.usernameCache = usernameCache;
        this.maxSizeBytes = maxSizeBytes;
    }

    public long maxSizeBytes() {
        return maxSizeBytes;
    }

    /**
     * Keeps the replay of a match. Returns true when it was stored (the bot is then told), false when the same file
     * was already there.
     */
    @Transactional
    public boolean store(Long matchId, String map, int durationTicks, int formatVersion, byte[] data) {
        validate(map, durationTicks, formatVersion, data);
        matchMapper.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
        String sha256 = sha256(data);
        Optional<String> existing = replayMapper.findSha256(matchId);
        if (existing.isPresent()) {
            return sameOrRefused(matchId, existing.get(), sha256);
        }
        MatchReplay replay = new MatchReplay();
        replay.setMatchId(matchId);
        replay.setMap(map);
        replay.setDurationTicks(durationTicks);
        replay.setFormatVersion(formatVersion);
        replay.setSizeBytes(data.length);
        replay.setSha256(sha256);
        replay.setData(data);
        if (replayMapper.insert(replay) == 0) {
            // two calls for one match at once: the other one stored first (its transaction has committed)
            return sameOrRefused(matchId, replayMapper.findSha256(matchId).orElse(""), sha256);
        }
        log.info("Replay stored: matchId={}, map={}, durationTicks={}, sizeBytes={}", matchId, map, durationTicks,
                data.length);
        webhookClient.notifyReplayAdded(new ReplayAddedPayload(matchId));
        return true;
    }

    private static boolean sameOrRefused(Long matchId, String stored, String sha256) {
        if (stored.equals(sha256)) {
            log.info("Replay already stored with the same bytes: matchId={}", matchId);
            return false;
        }
        throw new ReplayAlreadyExistsException(matchId);
    }

    private void validate(String map, int durationTicks, int formatVersion, byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidReplayException("The replay file is empty");
        }
        if (data.length > maxSizeBytes) {
            throw new ReplayTooLargeException(maxSizeBytes);
        }
        if (map == null || !MAP_ID.matcher(map).matches()) {
            throw new InvalidReplayException("map must be 1 to 64 characters of a-z, 0-9, _ and -");
        }
        if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) {
            throw new InvalidReplayException("durationTicks must be from 1 to " + MAX_DURATION_TICKS);
        }
        if (formatVersion < 1) {
            throw new InvalidReplayException("formatVersion must be 1 or more");
        }
    }

    public ReplayFile read(Long matchId) {
        MatchReplay replay = replayMapper.findByMatch(matchId).orElseThrow(() -> new ReplayNotFoundException(matchId));
        return new ReplayFile(replay.getMap(), replay.getDurationTicks(), replay.getFormatVersion(), replay.getData());
    }

    /** The newest replays first, of every match or of the matches of a player (by their cached name). */
    public List<ReplaySummaryResponse> latest(String username, int size) {
        String name = username == null || username.isBlank() ? null : username.trim();
        List<ReplaySummaryRow> rows = replayMapper.findLatest(name, size);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PlayerSummaryResponse>> players = new LinkedHashMap<>();
        List<Long> ids = rows.stream().map(ReplaySummaryRow::getMatchId).toList();
        for (ReplayPlayerRow row : replayMapper.findPlayers(ids)) {
            players.computeIfAbsent(row.getMatchId(), id -> new ArrayList<>())
                    .add(new PlayerSummaryResponse(row.getPlayerUuid(), usernameCache.get(row.getPlayerUuid())));
        }
        return rows.stream()
                .map(row -> new ReplaySummaryResponse(row.getMatchId(), row.getPlayedAt(), row.getGameType(),
                        row.getMap(), row.getDurationTicks(), players.getOrDefault(row.getMatchId(), List.of())))
                .toList();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
