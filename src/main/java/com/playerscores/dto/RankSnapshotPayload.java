package com.playerscores.dto;

import java.util.List;

public record RankSnapshotPayload(String event, Long seasonId, List<PlayerRankEntry> playerRanks) {}
