package com.playerscores.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class HypixelClient {

    private final RestClient restClient;
    private final String apiKey;

    public HypixelClient(RestClient.Builder builder, @Value("${hypixel.api-key}") String apiKey) {
        this.restClient = builder.baseUrl("https://api.hypixel.net").build();
        this.apiKey = apiKey;
    }

    public String getLinkedDiscord(UUID uuid) {
        try {
            HypixelResponse resp = restClient.get()
                    .uri("/v2/player?uuid={uuid}", uuid)
                    .header("API-Key", apiKey)
                    .retrieve()
                    .body(HypixelResponse.class);
            if (resp == null || resp.player() == null) {
                return null;
            }
            HypixelSocialMedia socialMedia = resp.player().socialMedia();
            if (socialMedia == null || socialMedia.links() == null) {
                return null;
            }
            return socialMedia.links().discord();
        } catch (RestClientException e) {
            return null;
        }
    }

    private record HypixelResponse(HypixelPlayer player) {}
    private record HypixelPlayer(HypixelSocialMedia socialMedia) {}
    private record HypixelSocialMedia(HypixelLinks links) {}
    private record HypixelLinks(@JsonProperty("DISCORD") String discord) {}
}
