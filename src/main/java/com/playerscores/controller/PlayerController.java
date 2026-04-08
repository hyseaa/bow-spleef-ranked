package com.playerscores.controller;

import com.playerscores.dto.PlayerRequest;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
@Tag(name = "Players", description = "Player management")
public class PlayerController {

    private final PlayerService playerService;

    @PutMapping("/{uuid}")
    @Operation(summary = "Register or update a player (upsert)")
    public PlayerResponse upsertPlayer(@PathVariable UUID uuid, @Valid @RequestBody PlayerRequest request) {
        return playerService.upsertPlayer(uuid, request);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get a player by UUID")
    public PlayerResponse getPlayer(@PathVariable UUID uuid) {
        return playerService.getPlayer(uuid);
    }
}
