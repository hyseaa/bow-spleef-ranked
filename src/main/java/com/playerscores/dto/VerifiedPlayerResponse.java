package com.playerscores.dto;

import java.util.UUID;

public record VerifiedPlayerResponse(UUID uuid, String username, String discordId) {}
