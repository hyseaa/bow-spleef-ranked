package com.playerscores.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Player {
    private Long id;
    private String username;
    private LocalDateTime createdAt;
}
