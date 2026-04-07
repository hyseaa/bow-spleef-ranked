package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ScoreRequest {
    @NotNull
    private Long playerId;

    @NotNull
    @Positive
    private Integer value;

    @NotBlank
    private String game;
}
