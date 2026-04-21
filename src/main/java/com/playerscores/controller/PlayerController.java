package com.playerscores.controller;

import com.playerscores.dto.MatchHistoryResponse;
import com.playerscores.dto.LeaderboardEntryResponse;
import com.playerscores.dto.PageResponse;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.dto.PlayerSeasonEloResponse;
import com.playerscores.dto.PlayerStatsResponse;
import com.playerscores.dto.WinsLeaderboardEntryResponse;
import com.playerscores.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
@Tag(name = "Players", description = "Player management")
public class PlayerController {

    private final PlayerService playerService;

    @PutMapping("/{uuid}")
    @Operation(summary = "Register a player by UUID (auto-fetches username from Mojang)")
    public PlayerResponse upsertPlayer(@PathVariable UUID uuid) {
        log.info("Call to API: PUT /api/v1/players/{} with parameters: uuid={}", uuid, uuid);
        PlayerResponse response = playerService.upsertPlayer(uuid);
        log.info("Call to API: PUT /api/v1/players/{} completed", uuid);
        return response;
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get a player by UUID")
    public PlayerResponse getPlayer(@PathVariable UUID uuid) {
        log.info("Call to API: GET /api/v1/players/{} with parameters: uuid={}", uuid, uuid);
        PlayerResponse response = playerService.getPlayer(uuid);
        log.info("Call to API: GET /api/v1/players/{} completed", uuid);
        return response;
    }

    @GetMapping("/by-discord/{discordId}")
    @Operation(summary = "Get a player by Discord ID")
    public PlayerResponse getPlayerByDiscordId(@PathVariable String discordId) {
        log.info("Call to API: GET /api/v1/players/by-discord/{} with parameters: discordId={}", discordId, discordId);
        PlayerResponse response = playerService.getPlayerByDiscordId(discordId);
        log.info("Call to API: GET /api/v1/players/by-discord/{} completed", discordId);
        return response;
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Get paginated ELO leaderboard for a ranked season")
    public PageResponse<LeaderboardEntryResponse> getLeaderboard(
            @RequestParam @NotNull Long seasonId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("Call to API: GET /api/v1/players/leaderboard with parameters: seasonId={}, page={}, size={}", seasonId, page, size);
        PageResponse<LeaderboardEntryResponse> response = playerService.getLeaderboard(seasonId, page, size);
        log.info("Call to API: GET /api/v1/players/leaderboard completed");
        return response;
    }

    @GetMapping("/leaderboard/wins")
    @Operation(summary = "Get paginated wins leaderboard for a game type")
    public PageResponse<WinsLeaderboardEntryResponse> getWinsLeaderboard(
            @RequestParam @NotNull String gameType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("Call to API: GET /api/v1/players/leaderboard/wins with parameters: gameType={}, page={}, size={}", gameType, page, size);
        PageResponse<WinsLeaderboardEntryResponse> response = playerService.getWinsLeaderboard(gameType, page, size);
        log.info("Call to API: GET /api/v1/players/leaderboard/wins completed");
        return response;
    }

    @GetMapping("/{uuid}/stats")
    @Operation(summary = "Get casual and ranked stats for a player (ranked = active seasons only)")
    public PlayerStatsResponse getPlayerStats(@PathVariable UUID uuid) {
        log.info("Call to API: GET /api/v1/players/{}/stats with parameters: uuid={}", uuid, uuid);
        PlayerStatsResponse response = playerService.getPlayerStats(uuid);
        log.info("Call to API: GET /api/v1/players/{}/stats completed", uuid);
        return response;
    }

    @GetMapping("/{uuid}/seasons/{seasonId}/elo")
    @Operation(summary = "Get a player's ELO for a specific ranked season")
    public PlayerSeasonEloResponse getPlayerSeasonElo(@PathVariable UUID uuid, @PathVariable Long seasonId) {
        log.info("Call to API: GET /api/v1/players/{}/seasons/{}/elo with parameters: uuid={}, seasonId={}", uuid, seasonId, uuid, seasonId);
        PlayerSeasonEloResponse response = playerService.getPlayerSeasonElo(uuid, seasonId);
        log.info("Call to API: GET /api/v1/players/{}/seasons/{}/elo completed", uuid, seasonId);
        return response;
    }

    @GetMapping("/{uuid}/elo-history")
    @Operation(summary = "Get ELO history for a player in a ranked season")
    public PageResponse<MatchHistoryResponse> getEloHistory(
            @PathVariable UUID uuid,
            @RequestParam @NotNull Long seasonId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.info("Call to API: GET /api/v1/players/{}/elo-history with parameters: uuid={}, seasonId={}, page={}, size={}", uuid, uuid, seasonId, page, size);
        PageResponse<MatchHistoryResponse> response = playerService.getEloHistory(uuid, seasonId, page, size);
        log.info("Call to API: GET /api/v1/players/{}/elo-history completed", uuid);
        return response;
    }
}
