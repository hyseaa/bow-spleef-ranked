package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ScoreRequest(@NotNull Long playerId, @NotNull @Positive Integer value, @NotBlank String game) {}
