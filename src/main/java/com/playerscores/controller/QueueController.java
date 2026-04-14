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
        return queueService.listQueue();
    }

    @PostMapping
    @Operation(summary = "Join the queue — returns a match immediately if a compatible opponent is found")
    public QueueResponse join(@Valid @RequestBody QueueRequest request) {
        return queueService.join(request.discordId(), request.gameType());
    }

    @DeleteMapping
    @Operation(summary = "Leave the queue")
    public ResponseEntity<Void> leave(
            @RequestParam @NotBlank String discordId,
            @RequestParam @NotBlank String gameType) {
        queueService.leave(discordId, gameType);
        return ResponseEntity.noContent().build();
    }
}
