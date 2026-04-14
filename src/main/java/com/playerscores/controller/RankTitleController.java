package com.playerscores.controller;

import com.playerscores.dto.RankTitleResponse;
import com.playerscores.service.RankTitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/rank-titles")
@Tag(name = "Rank Titles", description = "ELO rank title reference data")
public class RankTitleController {

    private final RankTitleService rankTitleService;

    public RankTitleController(RankTitleService rankTitleService) {
        this.rankTitleService = rankTitleService;
    }

    @GetMapping
    @Operation(summary = "List all rank titles ordered by ELO threshold")
    public List<RankTitleResponse> getAll() {
        log.info("Call to API: GET /api/v1/rank-titles with no parameters");
        List<RankTitleResponse> response = rankTitleService.getAll();
        log.info("Call to API: GET /api/v1/rank-titles completed");
        return response;
    }
}
