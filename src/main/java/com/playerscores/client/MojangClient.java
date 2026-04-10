package com.playerscores.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class MojangClient {

    private final RestClient restClient;

    public MojangClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String getUsername(UUID uuid) {
        String id = uuid.toString().replace("-", "");
        try {
            MojangProfile profile = restClient.get()
                    .uri("https://sessionserver.mojang.com/session/minecraft/profile/{id}", id)
                    .retrieve()
                    .body(MojangProfile.class);
            return profile != null ? profile.name() : null;
        } catch (RestClientException e) {
            return null;
        }
    }

    public UUID getUuidByUsername(String username) {
        try {
            MojangProfile profile = restClient.get()
                    .uri("https://api.minecraftservices.com/minecraft/profile/lookup/name/{username}", username)
                    .retrieve()
                    .body(MojangProfile.class);
            if (profile == null) {
                return null;
            }
            String id = profile.id();
            return UUID.fromString(id.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
        } catch (RestClientException e) {
            return null;
        }
    }

    private record MojangProfile(String id, String name) {}
}
