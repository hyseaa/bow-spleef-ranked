package com.playerscores.dto;

import java.util.List;

public record TeamResponse(Long id, int score, List<PlayerSummaryResponse> players) {}
