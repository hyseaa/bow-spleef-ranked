package net.bowspleef.ranked.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateMatchRequest(
        @NotBlank String gameType,
        @NotBlank String source,
        @NotNull @Size(min = 2) List<@Valid TeamRequest> teams,
        Map<UUID, Map<String, Object>> playerStats,
        /** The modifiers the game was played with (spec 010); null when the client sends none. */
        @ValidModifiers Map<String, Object> modifiers
) {

    /** A match with no modifiers: the form used before spec 010, still what the bot sends. */
    public CreateMatchRequest(String gameType, String source, List<TeamRequest> teams,
                              Map<UUID, Map<String, Object>> playerStats) {
        this(gameType, source, teams, playerStats, null);
    }
}
