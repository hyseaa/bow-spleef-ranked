package com.playerscores.controller;

import com.playerscores.dto.RankedSeasonRequest;
import com.playerscores.dto.RankedSeasonResponse;
import com.playerscores.service.RankedSeasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
        return rankedSeasonService.createSeason(request);
    }

    @GetMapping
    @Operation(summary = "List all ranked seasons")
    public List<RankedSeasonResponse> getAllSeasons() {
        return rankedSeasonService.getAllSeasons();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a ranked season by ID")
    public RankedSeasonResponse getSeason(@PathVariable Long id) {
        return rankedSeasonService.getSeason(id);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a ranked season")
    public RankedSeasonResponse deactivateSeason(@PathVariable Long id) {
        return rankedSeasonService.deactivateSeason(id);
    }
}
