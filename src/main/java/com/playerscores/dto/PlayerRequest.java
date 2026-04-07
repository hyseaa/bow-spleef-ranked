package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlayerRequest {
    @NotBlank
    private String username;
}
