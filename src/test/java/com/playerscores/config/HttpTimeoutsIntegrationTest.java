package com.playerscores.config;

import com.playerscores.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A call to Mojang or Hypixel must not wait for ever: it runs inside database transactions (the season lock of a
 * ranked match among them) and holds a request thread. The shared RestClient.Builder carries the timeouts.
 */
class HttpTimeoutsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RestClient.Builder builder;

    @Test
    void aServerThatNeverAnswersIsGivenUpOnQuickly() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                release.await(60, TimeUnit.SECONDS); // never answers during the test
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            RestClient client = builder.clone().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
            long start = System.nanoTime();

            assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                    .isInstanceOf(RestClientException.class);

            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
            assertThat(seconds).as("gave up after %d s", seconds).isLessThan(15);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }
}
