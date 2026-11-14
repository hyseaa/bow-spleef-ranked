package net.bowspleef.ranked.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bowspleef.ranked.dto.PreferencesResponse;
import net.bowspleef.ranked.dto.UpdatePreferencesRequest;
import net.bowspleef.ranked.service.PlayerPreferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The preferences of a player (spec 016, ADR-0023): read with the defaults, changed field by field. */
@Slf4j
@RestController
@RequestMapping("/api/v1/players/{uuid}/preferences")
@RequiredArgsConstructor
@Tag(name = "Players", description = "Player management")
public class PlayerPreferenceController {

    private final PlayerPreferenceService preferenceService;

    @GetMapping
    @Operation(summary = "Get the preferences of a player (the defaults for what was never set, and for an unknown player)")
    public PreferencesResponse getPreferences(@PathVariable UUID uuid) {
        log.info("Call to API: GET /api/v1/players/{}/preferences with parameters: uuid={}", uuid, uuid);
        PreferencesResponse response = preferenceService.get(uuid);
        log.info("Call to API: GET /api/v1/players/{}/preferences completed", uuid);
        return response;
    }

    @PatchMapping
    @Operation(summary = "Change the preferences of a player (creates the player if the API does not know it)")
    public PreferencesResponse updatePreferences(@PathVariable UUID uuid, @Valid @RequestBody UpdatePreferencesRequest request) {
        log.info("Call to API: PATCH /api/v1/players/{}/preferences with parameters: uuid={}, request={}", uuid, uuid, request);
        PreferencesResponse response = preferenceService.update(uuid, request);
        log.info("Call to API: PATCH /api/v1/players/{}/preferences completed", uuid);
        return response;
    }
}
