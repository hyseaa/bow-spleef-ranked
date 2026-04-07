package com.playerscores.controller;

import com.playerscores.dto.LeaderboardEntryDto;
import com.playerscores.dto.PageResponse;
import com.playerscores.dto.ScoreRequest;
import com.playerscores.dto.ScoreResponse;
import com.playerscores.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scores")
@RequiredArgsConstructor
@Tag(name = "Scores", description = "Score recording and retrieval")
public class ScoreController {

    private final ScoreService scoreService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a score for a player")
    public ScoreResponse recordScore(@Valid @RequestBody ScoreRequest request) {
        return scoreService.recordScore(request);
    }

    @GetMapping
    @Operation(summary = "Get paginated scores for a player, optionally filtered by game")
    public PageResponse<ScoreResponse> getScores(
            @RequestParam Long playerId,
            @RequestParam(required = false) String game,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scoreService.getScores(playerId, game, page, size);
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Get leaderboard (best score per player), optionally filtered by game")
    public List<LeaderboardEntryDto> getLeaderboard(
            @RequestParam(required = false) String game,
            @RequestParam(defaultValue = "10") int limit) {
        return scoreService.getLeaderboard(game, limit);
    }
}
