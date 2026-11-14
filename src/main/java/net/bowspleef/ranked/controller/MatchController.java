package net.bowspleef.ranked.controller;

import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.MatchListResponse;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.service.MatchRecording;
import net.bowspleef.ranked.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "Match recording and retrieval")
public class MatchController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final MatchService matchService;

    @PostMapping
    @Operation(summary = "Record a match result",
            description = "Send an optional `Idempotency-Key` header (1 to 100 non-blank characters) to make the call "
                    + "safe to retry: the first call answers `201`; a later call with the same key from the same source "
                    + "and the same match answers `200` with the stored match and records nothing; the same key with a "
                    + "different match answers `409` (`IDEMPOTENCY_KEY_REUSED`).")
    public ResponseEntity<MatchResponse> createMatch(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateMatchRequest request) {
        log.info("Call to API: POST /api/v1/matches with parameters: request={}, idempotencyKey={}", request, idempotencyKey);
        validateIdempotencyKey(idempotencyKey);
        MatchRecording recording = matchService.recordMatch(request, idempotencyKey);
        log.info("Call to API: POST /api/v1/matches completed: created={}", recording.created());
        return ResponseEntity.status(recording.created() ? HttpStatus.CREATED : HttpStatus.OK).body(recording.match());
    }

    private static void validateIdempotencyKey(String key) {
        if (key != null && (key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new ConstraintViolationException(IDEMPOTENCY_KEY_HEADER + " must be 1 to "
                    + MAX_IDEMPOTENCY_KEY_LENGTH + " non-blank characters", null);
        }
    }

    @GetMapping
    @Operation(summary = "Get paginated matches for a game type")
    public MatchListResponse getMatchesByGameType(
            @RequestParam @NotNull String gameType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("Call to API: GET /api/v1/matches with parameters: gameType={}, page={}, size={}", gameType, page, size);
        MatchListResponse response = matchService.getMatchesByGameType(gameType, page, size);
        log.info("Call to API: GET /api/v1/matches completed");
        return response;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a match by ID")
    public MatchResponse getMatch(@PathVariable Long id) {
        log.info("Call to API: GET /api/v1/matches/{} with parameters: id={}", id, id);
        MatchResponse response = matchService.getMatch(id);
        log.info("Call to API: GET /api/v1/matches/{} completed", id);
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a match by ID")
    public void deleteMatch(@PathVariable Long id) {
        log.info("Call to API: DELETE /api/v1/matches/{} with parameters: id={}", id, id);
        matchService.deleteMatch(id);
        log.info("Call to API: DELETE /api/v1/matches/{} completed", id);
    }
}
