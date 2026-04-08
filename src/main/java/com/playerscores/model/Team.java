package com.playerscores.model;

import lombok.Data;

@Data
public class Team {
    private Long id;
    private Long matchId;
    private String color;
    private int score;
}
