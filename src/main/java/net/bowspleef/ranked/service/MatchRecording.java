package net.bowspleef.ranked.service;

import net.bowspleef.ranked.dto.MatchResponse;

/** Outcome of recording a match: {@code created} is false when an idempotency key replayed an existing match. */
public record MatchRecording(MatchResponse match, boolean created) {}
