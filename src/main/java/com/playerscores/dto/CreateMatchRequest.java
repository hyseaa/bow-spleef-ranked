package com.playerscores.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateMatchRequest(
        @NotBlank String gameType,
        @NotBlank String source,
        @NotNull @Size(min = 2) List<@Valid TeamRequest> teams,
        Map<UUID, Map<String, Object>> playerStats
) {}
