package net.bowspleef.ranked.service;

import lombok.RequiredArgsConstructor;
import net.bowspleef.ranked.dto.PreferencesResponse;
import net.bowspleef.ranked.dto.UpdatePreferencesRequest;
import net.bowspleef.ranked.exception.InvalidPreferenceException;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.mapper.PlayerPreferenceMapper;
import net.bowspleef.ranked.model.PlayerPreference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The preferences of a player (spec 016, ADR-0023). A player with no row has the defaults, whether the API knows
 * them or not; a write creates the player row when it is absent, as a first match does, so a preference never
 * depends on Mojang being reachable.
 */
@Service
@RequiredArgsConstructor
public class PlayerPreferenceService {

    public static final int DEFAULT_TNT_REMOVED_PERCENT = 0;
    private static final int MAX_PERCENT = 100;

    private final PlayerMapper playerMapper;
    private final PlayerPreferenceMapper preferenceMapper;

    public PreferencesResponse get(UUID uuid) {
        return preferenceMapper.findByPlayer(uuid)
                .map(PlayerPreferenceService::toResponse)
                .orElse(new PreferencesResponse(DEFAULT_TNT_REMOVED_PERCENT));
    }

    @Transactional
    public PreferencesResponse update(UUID uuid, UpdatePreferencesRequest request) {
        if (request == null || request.tntRemovedPercent() == null) {
            throw new InvalidPreferenceException("at least one preference is required");
        }
        int percent = request.tntRemovedPercent();
        if (percent < 0 || percent > MAX_PERCENT) {
            throw new InvalidPreferenceException("tntRemovedPercent must be a whole number from 0 to " + MAX_PERCENT);
        }
        playerMapper.insertIfAbsent(uuid);
        preferenceMapper.upsert(uuid, percent);
        return get(uuid);
    }

    private static PreferencesResponse toResponse(PlayerPreference row) {
        return new PreferencesResponse(row.getTntRemovedPercent());
    }
}
