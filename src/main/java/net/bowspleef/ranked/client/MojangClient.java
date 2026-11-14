package net.bowspleef.ranked.client;

import net.bowspleef.ranked.exception.UpstreamUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Slf4j
@Component
public class MojangClient {

    private final RestClient restClient;

    public MojangClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String getUsername(UUID uuid) {
        String id = uuid.toString().replace("-", "");
        log.debug("Fetching Mojang username for uuid={}", uuid);
        try {
            MojangProfile profile = restClient.get()
                    .uri("https://sessionserver.mojang.com/session/minecraft/profile/{id}", id)
                    .retrieve()
                    .body(MojangProfile.class);
            if (profile == null || profile.name() == null) {
                log.warn("Mojang returned no profile for uuid={}", uuid);
                return null;
            }
            log.debug("Resolved username={} for uuid={}", profile.name(), uuid);
            return profile.name();
        } catch (RestClientException e) {
            log.error("Mojang API request failed for uuid={}: {}", uuid, e.getMessage(), e);
            return null;
        }
    }

    /**
     * The UUID of a Minecraft username, or null when Mojang does not know it.
     *
     * @throws UpstreamUnavailableException when Mojang could not answer (outage, rate limit)
     */
    public UUID getUuidByUsername(String username) {
        log.debug("Fetching Mojang UUID for username={}", username);
        try {
            MojangProfile profile = restClient.get()
                    .uri("https://api.minecraftservices.com/minecraft/profile/lookup/name/{username}", username)
                    .retrieve()
                    .body(MojangProfile.class);
            if (profile == null || profile.id() == null) {
                log.warn("Mojang returned no profile for username={}", username);
                return null;
            }
            UUID uuid = UUID.fromString(profile.id().replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            log.debug("Resolved uuid={} for username={}", uuid, username);
            return uuid;
        } catch (HttpClientErrorException.NotFound e) {
            // Mojang answers 404 for a name that does not exist: a normal answer, not a failure
            log.warn("Mojang does not know username={}", username);
            return null;
        } catch (RestClientException e) {
            // outage, rate limit: the player is not at fault (KI-21)
            log.error("Mojang API request failed for username={}: {}", username, e.getMessage(), e);
            throw new UpstreamUnavailableException("Mojang", e);
        }
    }

    private record MojangProfile(String id, String name) {}
}
