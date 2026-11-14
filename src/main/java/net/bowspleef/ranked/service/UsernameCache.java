package net.bowspleef.ranked.service;

import net.bowspleef.ranked.client.MojangClient;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.model.Player;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsernameCache {

    private static final long CACHE_DAYS = 15;
    /** After Mojang failed for a UUID it is not asked again for this long (each ask can cost 8 s and feeds the rate limit). */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(5);
    private static final int MAX_REMEMBERED_FAILURES = 1000;

    private final PlayerMapper playerMapper;
    private final MojangClient mojangClient;

    private final Map<UUID, Instant> recentFailures = new ConcurrentHashMap<>();
    // not a constructor argument (Lombok): replaced by a fixed clock in tests
    private Clock clock = Clock.systemUTC();

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

        String stale = player != null ? player.getUsername() : null;
        Instant now = clock.instant();
        Instant failedAt = recentFailures.get(uuid);
        if (failedAt != null && failedAt.plus(RETRY_AFTER_FAILURE).isAfter(now)) {
            log.debug("Mojang failed recently for uuid={}, not asking again yet", uuid);
            return stale;
        }
        log.debug("Username cache miss for uuid={}, fetching from Mojang", uuid);
        String username = mojangClient.getUsername(uuid);
        if (username != null) {
            recentFailures.remove(uuid);
            playerMapper.updateUsernameCache(uuid, username);
            log.debug("Username cache updated for uuid={}: username={}", uuid, username);
            return username;
        }
        log.warn("Could not resolve username for uuid={}, Mojang returned null{}", uuid,
                stale != null ? " (keeping the expired name " + stale + ")" : "");
        if (recentFailures.size() >= MAX_REMEMBERED_FAILURES) {
            recentFailures.values().removeIf(at -> at.plus(RETRY_AFTER_FAILURE).isBefore(now));
        }
        recentFailures.put(uuid, now);
        // an expired name is better than none
        return stale;
    }
}
