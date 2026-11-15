package net.bowspleef.ranked.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bowspleef.ranked.dto.ReplayFile;
import net.bowspleef.ranked.dto.ReplaySummaryResponse;
import net.bowspleef.ranked.exception.InvalidReplayException;
import net.bowspleef.ranked.exception.ReplayTooLargeException;
import net.bowspleef.ranked.service.ReplayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The replays of the matches of the custom server (spec 024, ADR-0026): the plugin sends the file of a match once
 * the match is recorded, reads it back to play it, and lists the newest ones.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Replays", description = "Replays of the matches of the custom server")
public class ReplayController {

    static final String MAP_HEADER = "Replay-Map";
    static final String DURATION_HEADER = "Replay-Duration-Ticks";
    static final String FORMAT_HEADER = "Replay-Format-Version";

    private final ReplayService replayService;

    @PutMapping(value = "/api/v1/matches/{id}/replay", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Keep the replay of a match",
            description = "The body is the plugin's file, kept as it comes. `201` stored; `200` the same bytes were "
                    + "already stored; `409` (`REPLAY_ALREADY_EXISTS`) the match has another replay; `404` "
                    + "(`MATCH_NOT_FOUND`); `413` (`REPLAY_TOO_LARGE`) above `replay.max-size-bytes`.")
    public ResponseEntity<Void> putReplay(@PathVariable Long id,
                                          @RequestParam(required = false) String map,
                                          @RequestParam(required = false) Integer durationTicks,
                                          @RequestParam(required = false) Integer formatVersion,
                                          HttpServletRequest request) throws IOException {
        log.info("Call to API: PUT /api/v1/matches/{}/replay with parameters: map={}, durationTicks={}, "
                + "formatVersion={}, contentLength={}", id, map, durationTicks, formatVersion, request.getContentLengthLong());
        if (durationTicks == null || formatVersion == null) {
            throw new InvalidReplayException("map, durationTicks and formatVersion are required");
        }
        byte[] data = readBody(request, replayService.maxSizeBytes());
        boolean created = replayService.store(id, map, durationTicks, formatVersion, data);
        log.info("Call to API: PUT /api/v1/matches/{}/replay completed: created={}", id, created);
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    /** Reads at most one byte more than the limit, so that a body too large is refused without being read whole. */
    private static byte[] readBody(HttpServletRequest request, long maxSizeBytes) throws IOException {
        long declared = request.getContentLengthLong();
        if (declared > maxSizeBytes) {
            throw new ReplayTooLargeException(maxSizeBytes);
        }
        try (InputStream in = request.getInputStream()) {
            byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxSizeBytes + 1));
            if (data.length > maxSizeBytes) {
                throw new ReplayTooLargeException(maxSizeBytes);
            }
            if (data.length == 0) {
                throw new InvalidReplayException("The replay file is empty");
            }
            return data;
        }
    }

    @GetMapping(value = "/api/v1/matches/{id}/replay", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Get the replay of a match",
            description = "The file as it was sent, with its map, duration and format version in the headers "
                    + "`Replay-Map`, `Replay-Duration-Ticks` and `Replay-Format-Version`. `404` (`REPLAY_NOT_FOUND`).")
    public ResponseEntity<byte[]> getReplay(@PathVariable Long id) {
        log.info("Call to API: GET /api/v1/matches/{}/replay with parameters: id={}", id, id);
        ReplayFile file = replayService.read(id);
        log.info("Call to API: GET /api/v1/matches/{}/replay completed: sizeBytes={}", id, file.data().length);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(MAP_HEADER, file.map())
                .header(DURATION_HEADER, String.valueOf(file.durationTicks()))
                .header(FORMAT_HEADER, String.valueOf(file.formatVersion()))
                .body(file.data());
    }

    @GetMapping("/api/v1/replays")
    @Operation(summary = "List the newest replays",
            description = "Newest first by the date of the match, without the files. With `username`, only the "
                    + "matches where a player of that name (case-insensitive) played.")
    public List<ReplaySummaryResponse> listReplays(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "10") @Min(1) @Max(ReplayService.MAX_LIST_SIZE) int size) {
        log.info("Call to API: GET /api/v1/replays with parameters: username={}, size={}", username, size);
        List<ReplaySummaryResponse> replays = replayService.latest(username, size);
        log.info("Call to API: GET /api/v1/replays completed: count={}", replays.size());
        return replays;
    }
}
