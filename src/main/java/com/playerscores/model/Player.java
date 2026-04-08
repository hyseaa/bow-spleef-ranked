package com.playerscores.model;

import lombok.Data;

import java.util.UUID;

@Data
public class Player {
    private UUID uuid;
    private String username;
}
