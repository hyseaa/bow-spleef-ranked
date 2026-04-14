package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class RankedSeason {
    private Long id;
    private String name;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private String gameType;
    private boolean active;
}
