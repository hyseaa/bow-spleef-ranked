package com.playerscores.config;

import com.playerscores.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Over a real HTTP connection (MockMvc does not do the error dispatch): an error raised by the framework itself
 * (malformed JSON, missing parameter, wrong method, unknown path) is forwarded to /error, which used to go through
 * the API key rule and answer 403 with an empty body even for a valid key. A client cannot then tell a malformed
 * request from a wrong key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${api.key}")
    private String apiKey;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> call(String method, String path, String key, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (key != null) {
            request.header("X-Api-Key", key);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void malformedJsonWithAValidKeyIsA400() throws Exception {
        assertThat(call("POST", "/api/v1/matches", apiKey, "{bad").statusCode()).isEqualTo(400);
    }

    @Test
    void aMissingRequiredParameterWithAValidKeyIsA400() throws Exception {
        assertThat(call("GET", "/api/v1/matches", apiKey, null).statusCode()).isEqualTo(400);
    }

    @Test
    void aTypeMismatchWithAValidKeyIsA400() throws Exception {
        assertThat(call("GET", "/api/v1/matches/abc", apiKey, null).statusCode()).isEqualTo(400);
    }

    @Test
    void aWrongMethodWithAValidKeyIsA405() throws Exception {
        assertThat(call("DELETE", "/api/v1/game-types", apiKey, null).statusCode()).isEqualTo(405);
    }

    @Test
    void anUnknownPathWithAValidKeyIsA404() throws Exception {
        assertThat(call("GET", "/api/v1/nothing", apiKey, null).statusCode()).isEqualTo(404);
    }

    @Test
    void withoutAKeyOrWithAWrongKeyItIsStillA403() throws Exception {
        assertThat(call("GET", "/api/v1/game-types", null, null).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/v1/game-types", "wrong", null).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/matches", null, "{bad").statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/v1/nothing", null, null).statusCode()).isEqualTo(403);
        assertThat(call("GET", "/error", null, null).statusCode()).as("the error page itself is not public").isEqualTo(403);
    }
}
