package com.playerscores.controller;

import com.playerscores.dto.VerifiedPlayerResponse;
import com.playerscores.dto.VerifyRequest;
import com.playerscores.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
@Tag(name = "Players", description = "Player management")
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping("/verify")
    @Operation(summary = "Verify and link a Discord account to a Minecraft UUID via Hypixel")
    public VerifiedPlayerResponse verify(@Valid @RequestBody VerifyRequest request) {
        return verificationService.verify(request);
    }

    @GetMapping("/verified")
    @Operation(summary = "List all verified players")
    public List<VerifiedPlayerResponse> getVerifiedPlayers() {
        return verificationService.getVerifiedPlayers();
    }
}
