package com.playerscores.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TeamRequest(
        String color,
        @NotNull Integer score,
        @NotNull @Size(min = 1) List<UUID> playerUuids
) {}
