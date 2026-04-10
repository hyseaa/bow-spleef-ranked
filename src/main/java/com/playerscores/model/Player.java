package com.playerscores.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class Player {
    private UUID uuid;
    private String discordId;
    private String username;
    private OffsetDateTime usernameCachedAt;
}
