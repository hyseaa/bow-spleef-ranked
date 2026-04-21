package com.playerscores.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MatchHistoryRow {
    private Long matchId;
    private OffsetDateTime playedAt;
    private int eloChange;
    private int eloAfter;
}
