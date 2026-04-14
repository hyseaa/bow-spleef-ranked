package com.playerscores.controller;

import com.playerscores.dto.RankedSeasonRequest;
import com.playerscores.dto.RankedSeasonResponse;
import com.playerscores.service.RankedSeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/ranked-seasons")
@RequiredArgsConstructor
@Tag(name = "Ranked Seasons", description = "Ranked season management")
public class RankedSeasonController {

    private final RankedSeasonService rankedSeasonService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new ranked season")
    public RankedSeasonResponse createSeason(@Valid @RequestBody RankedSeasonRequest request) {
        log.info("Call to API: POST /api/v1/ranked-seasons with parameters: request={}", request);
        RankedSeasonResponse response = rankedSeasonService.createSeason(request);
        log.info("Call to API: POST /api/v1/ranked-seasons completed");
        return response;
    }

    @GetMapping
    @Operation(summary = "List all ranked seasons")
    public List<RankedSeasonResponse> getAllSeasons() {
        log.info("Call to API: GET /api/v1/ranked-seasons with no parameters");
        List<RankedSeasonResponse> response = rankedSeasonService.getAllSeasons();
        log.info("Call to API: GET /api/v1/ranked-seasons completed");
        return response;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ranked season by ID")
    public RankedSeasonResponse getSeason(@PathVariable Long id) {
        log.info("Call to API: GET /api/v1/ranked-seasons/{} with parameters: id={}", id, id);
        RankedSeasonResponse response = rankedSeasonService.getSeason(id);
        log.info("Call to API: GET /api/v1/ranked-seasons/{} completed", id);
        return response;
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a ranked season")
    public RankedSeasonResponse deactivateSeason(@PathVariable Long id) {
        log.info("Call to API: POST /api/v1/ranked-seasons/{}/deactivate with parameters: id={}", id, id);
        RankedSeasonResponse response = rankedSeasonService.deactivateSeason(id);
        log.info("Call to API: POST /api/v1/ranked-seasons/{}/deactivate completed", id);
        return response;
    }
}
