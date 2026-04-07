package com.playerscores.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlayerResponse {
    private Long id;
    private String username;
    private LocalDateTime createdAt;
}
