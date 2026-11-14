package net.bowspleef.ranked.model;

import lombok.Data;

import java.util.UUID;

@Data
public class TeamPlayer {
    private Long id;
    private Long teamId;
    private UUID playerUuid;
}
