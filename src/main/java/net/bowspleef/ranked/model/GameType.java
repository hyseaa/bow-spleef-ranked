package net.bowspleef.ranked.model;

import lombok.Data;

@Data
public class GameType {
    private String name;
    private String displayName;
    private boolean ranked;
    private int teamSize;
}
