package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Match {
    private Long id;
    private String gameType;
    private String source;
    private OffsetDateTime playedAt;
    private Long rankedSeasonId;
}
