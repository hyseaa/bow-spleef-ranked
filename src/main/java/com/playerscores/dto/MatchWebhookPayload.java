package com.playerscores.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

public record MatchWebhookPayload(
        @JsonUnwrapped MatchResponse match,
        List<PlayerRankEntry> playerRanks
) {}
