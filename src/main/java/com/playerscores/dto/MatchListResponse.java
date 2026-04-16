package com.playerscores.dto;

import java.util.List;

public record MatchListResponse(String gameType, String gameTypeDisplayName, List<MatchResponse> content, int page, int size, long total, int totalPages) {

    public static MatchListResponse of(String gameType, String gameTypeDisplayName, List<MatchResponse> content, int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new MatchListResponse(gameType, gameTypeDisplayName, content, page, size, total, totalPages);
    }
}
