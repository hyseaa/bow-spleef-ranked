package com.playerscores.controller;

import com.playerscores.dto.EloHistoryResponse;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
        return playerService.upsertPlayer(uuid);
    }

    @GetMapping("/{uuid}")
    @Operation(summary = "Get a player by UUID")
    public PlayerResponse getPlayer(@PathVariable UUID uuid) {
        return playerService.getPlayer(uuid);
    }

    @GetMapping("/by-discord/{discordId}")
    @Operation(summary = "Get a player by Discord ID")
    public PlayerResponse getPlayerByDiscordId(@PathVariable String discordId) {
        return playerService.getPlayerByDiscordId(discordId);
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Get paginated ELO leaderboard for a ranked season")
    public PageResponse<LeaderboardEntryResponse> getLeaderboard(
            @RequestParam @NotNull Long seasonId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return playerService.getLeaderboard(seasonId, page, size);
    }

    @GetMapping("/leaderboard/wins")
    @Operation(summary = "Get paginated wins leaderboard for a game type")
    public PageResponse<WinsLeaderboardEntryResponse> getWinsLeaderboard(
            @RequestParam @NotNull String gameType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return playerService.getWinsLeaderboard(gameType, page, size);
    }

    @GetMapping("/{uuid}/stats")
    @Operation(summary = "Get casual and ranked stats for a player (ranked = active seasons only)")
    public PlayerStatsResponse getPlayerStats(@PathVariable UUID uuid) {
        return playerService.getPlayerStats(uuid);
    }

    @GetMapping("/{uuid}/seasons/{seasonId}/elo")
    @Operation(summary = "Get a player's ELO for a specific ranked season")
    public PlayerSeasonEloResponse getPlayerSeasonElo(@PathVariable UUID uuid, @PathVariable Long seasonId) {
        return playerService.getPlayerSeasonElo(uuid, seasonId);
    }

    @GetMapping("/{uuid}/elo-history")
    @Operation(summary = "Get ELO history for a player in a ranked season")
    public PageResponse<EloHistoryResponse> getEloHistory(
            @PathVariable UUID uuid,
            @RequestParam @NotNull Long seasonId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return playerService.getEloHistory(uuid, seasonId, page, size);
    }
}
