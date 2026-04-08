package com.playerscores.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Match {
    private Long id;
    private String gameType;
    private String source;
    private LocalDateTime playedAt;
}
