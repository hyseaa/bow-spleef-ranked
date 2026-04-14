package com.playerscores.controller;

import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.MatchResponse;
import com.playerscores.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "Match recording and retrieval")
public class MatchController {

    private final MatchService matchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a match result")
    public MatchResponse createMatch(@Valid @RequestBody CreateMatchRequest request) {
        log.info("Call to API: POST /api/v1/matches with parameters: request={}", request);
        MatchResponse response = matchService.createMatch(request);
        log.info("Call to API: POST /api/v1/matches completed");
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
}
