package com.playerscores.dto;

import jakarta.validation.constraints.NotBlank;

public record PlayerRequest(@NotBlank String username) {}
