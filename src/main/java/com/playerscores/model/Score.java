package com.playerscores.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Score {
    private Long id;
    private Long playerId;
    private Integer value;
    private String game;
    private LocalDateTime createdAt;
}
