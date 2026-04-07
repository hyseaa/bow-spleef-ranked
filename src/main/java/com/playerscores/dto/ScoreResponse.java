package com.playerscores.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScoreResponse {
    private Long id;
    private Long playerId;
    private Integer value;
    private String game;
    private LocalDateTime createdAt;
}
