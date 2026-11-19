package net.bowspleef.ranked.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TeamRequest(
        @NotNull @Min(0) Integer score,
        @NotNull @Size(min = 1) List<UUID> playerUuids
) {}
