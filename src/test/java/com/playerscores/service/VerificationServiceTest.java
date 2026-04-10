package com.playerscores.service;

import com.playerscores.client.HypixelClient;
import com.playerscores.client.MojangClient;
import com.playerscores.dto.VerifiedPlayerResponse;
import com.playerscores.dto.VerifyRequest;
import com.playerscores.exception.VerificationException;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private MojangClient mojangClient;
    @Mock
    private HypixelClient hypixelClient;
    @Mock
    private UsernameCache usernameCache;

    @InjectMocks
    private VerificationService verificationService;

    @Test
    void verify_usernameNotFound_throwsException() {
        when(mojangClient.getUuidByUsername("Unknown")).thenReturn(null);

        VerifyRequest req = new VerifyRequest("123", "Notch", "Unknown");
        assertThatThrownBy(() -> verificationService.verify(req))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void verify_noDiscordLinked_throwsException() {
        UUID uuid = UUID.randomUUID();
        when(mojangClient.getUuidByUsername("Notch")).thenReturn(uuid);
        when(hypixelClient.getLinkedDiscord(uuid)).thenReturn(null);

        VerifyRequest req = new VerifyRequest("123", "Notch", "Notch");
        assertThatThrownBy(() -> verificationService.verify(req))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("No Discord");
    }

    @Test
    void verify_discordMismatch_throwsException() {
        UUID uuid = UUID.randomUUID();
        when(mojangClient.getUuidByUsername("Notch")).thenReturn(uuid);
        when(hypixelClient.getLinkedDiscord(uuid)).thenReturn("OtherUser");

        VerifyRequest req = new VerifyRequest("123", "Notch", "Notch");
        assertThatThrownBy(() -> verificationService.verify(req))
                .isInstanceOf(VerificationException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void verify_success_returnsResponse() {
        UUID uuid = UUID.randomUUID();
        when(mojangClient.getUuidByUsername("Notch")).thenReturn(uuid);
        when(hypixelClient.getLinkedDiscord(uuid)).thenReturn("Notch");
        when(usernameCache.get(uuid)).thenReturn("Notch");

        VerifyRequest req = new VerifyRequest("123456789", "Notch", "Notch");
        VerifiedPlayerResponse response = verificationService.verify(req);

        assertThat(response.discordId()).isEqualTo("123456789");
        assertThat(response.username()).isEqualTo("Notch");
    }

    @Test
    void getVerifiedPlayers_returnsList() {
        UUID uuid = UUID.randomUUID();
        Player player = new Player();
        player.setUuid(uuid);
        player.setDiscordId("123456789");
        when(playerMapper.findVerifiedPlayers()).thenReturn(List.of(player));
        when(usernameCache.get(uuid)).thenReturn("Notch");

        List<VerifiedPlayerResponse> result = verificationService.getVerifiedPlayers();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().username()).isEqualTo("Notch");
    }
}
