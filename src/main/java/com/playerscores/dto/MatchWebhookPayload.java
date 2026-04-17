package com.playerscores.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MatchWebhookPayload {
    @JsonUnwrapped
    private final MatchResponse match;
    private final List<PlayerRankEntry> playerRanks;
}
