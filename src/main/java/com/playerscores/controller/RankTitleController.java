package com.playerscores.controller;

import com.playerscores.dto.RankTitleResponse;
import com.playerscores.service.RankTitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        return rankTitleService.getAll();
    }
}
