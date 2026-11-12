package com.playerscores.client;

import com.playerscores.exception.UpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/** KI-21: "not found" and "the upstream failed" are told apart for Mojang and Hypixel. */
class UpstreamClientsTest {

    private static final UUID UUID_1 = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private MojangClient mojang() {
        return new MojangClient(builder);
    }

    private HypixelClient hypixel() {
        return new HypixelClient(builder, "key");
    }

    // ---- Mojang: username -> UUID ----

    @Test
    void mojang_knownUsername_returnsTheUuid() {
        server.expect(requestTo("https://api.minecraftservices.com/minecraft/profile/lookup/name/Alice"))
                .andRespond(withSuccess("{\"id\":\"11111111222233334444555555555555\",\"name\":\"Alice\"}", MediaType.APPLICATION_JSON));

        assertThat(mojang().getUuidByUsername("Alice")).isEqualTo(UUID_1);
    }

    @Test
    void mojang_unknownUsername_isNotFound_notAFailure() {
        server.expect(requestTo("https://api.minecraftservices.com/minecraft/profile/lookup/name/Nobody"))
                .andRespond(withStatus(NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"path\":\"/minecraft/profile/lookup/name/Nobody\",\"errorMessage\":\"Couldn't find any profile with name Nobody\"}"));

        assertThat(mojang().getUuidByUsername("Nobody")).isNull();
    }

    @Test
    void mojang_rateLimitOrOutage_isAnUpstreamFailure() {
        server.expect(requestTo("https://api.minecraftservices.com/minecraft/profile/lookup/name/A")).andRespond(withStatus(TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.minecraftservices.com/minecraft/profile/lookup/name/B")).andRespond(withStatus(INTERNAL_SERVER_ERROR));
        MojangClient client = mojang();

        assertThatThrownBy(() -> client.getUuidByUsername("A"))
                .isInstanceOf(UpstreamUnavailableException.class).hasMessageContaining("Mojang");
        assertThatThrownBy(() -> client.getUuidByUsername("B")).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void mojang_usernameOfAUuid_staysNullWhenMojangFails() {
        // used to display names (UsernameCache): a failure must not break the page that shows the player
        server.expect(requestTo("https://sessionserver.mojang.com/session/minecraft/profile/11111111222233334444555555555555"))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThat(mojang().getUsername(UUID_1)).isNull();
    }

    // ---- Hypixel: UUID -> linked Discord ----

    @Test
    void hypixel_linkedDiscord_isReturned() {
        server.expect(requestTo("https://api.hypixel.net/v2/player?uuid=" + UUID_1))
                .andRespond(withSuccess("{\"success\":true,\"player\":{\"socialMedia\":{\"links\":{\"DISCORD\":\"alice#0\"}}}}", MediaType.APPLICATION_JSON));

        assertThat(hypixel().getLinkedDiscord(UUID_1)).isEqualTo("alice#0");
    }

    @Test
    void hypixel_playerWithoutAnyLink_isNotLinked_notAFailure() {
        server.expect(requestTo("https://api.hypixel.net/v2/player?uuid=" + UUID_1))
                .andRespond(withSuccess("{\"success\":true,\"player\":{}}", MediaType.APPLICATION_JSON));

        assertThat(hypixel().getLinkedDiscord(UUID_1)).isNull();
    }

    @Test
    void hypixel_playerNeverSeen_isNotLinked_notAFailure() {
        server.expect(requestTo("https://api.hypixel.net/v2/player?uuid=" + UUID_1))
                .andRespond(withSuccess("{\"success\":true,\"player\":null}", MediaType.APPLICATION_JSON));

        assertThat(hypixel().getLinkedDiscord(UUID_1)).isNull();
    }

    @Test
    void hypixel_badKeyRateLimitOrOutage_isAnUpstreamFailure() {
        server.expect(requestTo("https://api.hypixel.net/v2/player?uuid=" + UUID_1)).andRespond(withStatus(FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON).body("{\"success\":false,\"cause\":\"Invalid API key\"}"));
        server.expect(requestTo("https://api.hypixel.net/v2/player?uuid=" + UUID_1)).andRespond(withStatus(TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.hypixel.net/v2/player?uuid=" + UUID_1)).andRespond(withStatus(INTERNAL_SERVER_ERROR));
        HypixelClient client = hypixel();

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> client.getLinkedDiscord(UUID_1))
                    .isInstanceOf(UpstreamUnavailableException.class).hasMessageContaining("Hypixel");
        }
    }
}
