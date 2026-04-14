package com.playerscores.controller;

import com.playerscores.dto.QueueByGameTypeResponse;
import com.playerscores.dto.QueueRequest;
import com.playerscores.dto.QueueResponse;
import com.playerscores.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
@Tag(name = "Queue", description = "Matchmaking queue")
public class QueueController {

    private final QueueService queueService;

    @GetMapping
    @Operation(summary = "List all queued players grouped by game type")
    public List<QueueByGameTypeResponse> listQueue() {
        log.info("Call to API: GET /api/v1/queue with no parameters");
        List<QueueByGameTypeResponse> response = queueService.listQueue();
        log.info("Call to API: GET /api/v1/queue completed");
        return response;
    }

    @PostMapping
    @Operation(summary = "Join the queue — returns a match immediately if a compatible opponent is found")
    public QueueResponse join(@Valid @RequestBody QueueRequest request) {
        log.info("Call to API: POST /api/v1/queue with parameters: discordId={}, gameType={}", request.discordId(), request.gameType());
        QueueResponse response = queueService.join(request.discordId(), request.gameType());
        log.info("Call to API: POST /api/v1/queue completed, matched={}", response.matched());
        return response;
    }

    @DeleteMapping
    @Operation(summary = "Leave the queue")
    public ResponseEntity<Void> leave(
            @RequestParam @NotBlank String discordId,
            @RequestParam @NotBlank String gameType) {
        log.info("Call to API: DELETE /api/v1/queue with parameters: discordId={}, gameType={}", discordId, gameType);
        queueService.leave(discordId, gameType);
        log.info("Call to API: DELETE /api/v1/queue completed");
        return ResponseEntity.noContent().build();
    }
}
