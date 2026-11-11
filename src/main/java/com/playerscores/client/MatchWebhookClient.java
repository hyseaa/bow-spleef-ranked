package com.playerscores.client;

import com.playerscores.config.WebhookProperties;
import com.playerscores.dto.MatchWebhookPayload;
import com.playerscores.dto.RankSnapshotPayload;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pushes events to the bot (ADR-0008). KI-07: a webhook is never sent from inside a database
 * transaction. When called in a transaction it is sent only after that transaction has committed
 * (nothing is sent if it rolls back), and always from a separate thread, in the order of the calls,
 * so the caller never waits for the bot. Connect and read timeouts are bounded.
 */
@Slf4j
@Component
public class MatchWebhookClient {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final WebhookProperties properties;
    private final RestClient restClient;
    private final Executor executor;

    @Autowired
    public MatchWebhookClient(WebhookProperties properties, RestClient.Builder builder) {
        this(properties, builder.requestFactory(timeoutFactory()), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "webhook-sender");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** For tests: the executor is chosen by the caller and the request factory is left to the builder. */
    MatchWebhookClient(WebhookProperties properties, RestClient.Builder builder, Executor executor) {
        this.properties = properties;
        this.restClient = builder.build();
        this.executor = executor;
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdown();
        }
    }

    public void notifyMatchResult(MatchWebhookPayload payload) {
        if (!properties.enabled()) {
            return;
        }
        afterCommit(() -> post(payload, "Match-result webhook failed: matchId=" + payload.getMatch().id()));
    }

    public void notifyRanksUpdated(RankSnapshotPayload payload) {
        if (!properties.enabled()) {
            return;
        }
        afterCommit(() -> post(payload, "Ranks-updated webhook failed: seasonId=" + payload.seasonId()));
    }

    private void afterCommit(Runnable send) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(send);
                }
            });
        } else {
            executor.execute(send);
        }
    }

    private void post(Object payload, String failureMessage) {
        try {
            restClient.post()
                    .uri(properties.url())
                    .header("Authorization", "Bearer " + properties.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.error("{}, url={}: {}", failureMessage, properties.url(), e.getMessage(), e);
        }
    }
}
