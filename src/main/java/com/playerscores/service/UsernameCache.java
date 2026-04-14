package com.playerscores.service;

import com.playerscores.client.MojangClient;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsernameCache {

    private static final long CACHE_DAYS = 15;

    private final PlayerMapper playerMapper;
    private final MojangClient mojangClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String get(UUID uuid) {
        Player player = playerMapper.findByUuid(uuid).orElse(null);
        if (player != null
                && player.getUsername() != null
                && player.getUsernameCachedAt() != null
                && player.getUsernameCachedAt().isAfter(OffsetDateTime.now().minusDays(CACHE_DAYS))) {
            log.debug("Username cache hit for uuid={}: username={}", uuid, player.getUsername());
            return player.getUsername();
        }

        log.debug("Username cache miss for uuid={}, fetching from Mojang", uuid);
        String username = mojangClient.getUsername(uuid);
        if (username != null) {
            playerMapper.updateUsernameCache(uuid, username);
            log.debug("Username cache updated for uuid={}: username={}", uuid, username);
        } else {
            log.warn("Could not resolve username for uuid={}, Mojang returned null", uuid);
        }
        return username;
    }
}
