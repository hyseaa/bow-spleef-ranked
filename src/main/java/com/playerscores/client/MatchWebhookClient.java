package com.playerscores.client;

import com.playerscores.config.WebhookProperties;
import com.playerscores.dto.MatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class MatchWebhookClient {

    private final WebhookProperties properties;
    private final RestClient restClient;

    public MatchWebhookClient(WebhookProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    public void notifyMatchResult(MatchResponse match) {
        if (!properties.enabled()) {
            return;
        }
        log.debug("Sending match-result webhook: matchId={}, url={}", match.id(), properties.url());
        try {
            restClient.post()
                    .uri(properties.url())
                    .header("Authorization", "Bearer " + properties.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(match)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Match-result webhook delivered: matchId={}", match.id());
        } catch (RestClientException e) {
            log.error("Match-result webhook failed: matchId={}, url={}: {}", match.id(), properties.url(), e.getMessage(), e);
        }
    }
}
