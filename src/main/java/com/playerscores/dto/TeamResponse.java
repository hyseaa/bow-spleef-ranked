package com.playerscores.dto;

import java.util.List;

public record TeamResponse(Long id, String color, int score, List<PlayerSummaryResponse> players) {}
