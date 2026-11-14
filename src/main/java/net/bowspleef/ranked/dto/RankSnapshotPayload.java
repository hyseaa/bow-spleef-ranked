package net.bowspleef.ranked.dto;

import java.util.List;

/**
 * Webhook body sent after a season recompute. {@code gameType} says which mode the titles are for and
 * {@code seasonActive} lets the bot ignore the recompute of a finished season (spec 005).
 */
public record RankSnapshotPayload(String event, Long seasonId, String gameType, boolean seasonActive,
                                  List<PlayerRankEntry> playerRanks) {}
