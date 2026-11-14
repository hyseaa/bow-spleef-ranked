package net.bowspleef.ranked.service;

import net.bowspleef.ranked.client.HypixelClient;
import net.bowspleef.ranked.client.MojangClient;
import net.bowspleef.ranked.dto.VerifiedPlayerResponse;
import net.bowspleef.ranked.dto.VerifyRequest;
import net.bowspleef.ranked.exception.DiscordMismatchException;
import net.bowspleef.ranked.exception.MojangUsernameNotFoundException;
import net.bowspleef.ranked.exception.UpstreamUnavailableException;
import net.bowspleef.ranked.exception.VerificationException;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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
    @Mock
    private PlayerLinkService playerLinkService;

    @InjectMocks
    private VerificationService verificationService;

    @Test
    void verify_usernameNotFound_throwsMojangNotFoundException() {
        when(mojangClient.getUuidByUsername("Unknown")).thenReturn(null);

        VerifyRequest req = new VerifyRequest("123", "Notch", "Unknown");
        assertThatThrownBy(() -> verificationService.verify(req))
                .isInstanceOf(MojangUsernameNotFoundException.class);
    }

    /** KI-21: a Mojang or Hypixel failure is not reported as a problem with the player's profile. */
    @Test
    void verify_mojangUnavailable_propagatesInsteadOfSayingTheNameIsUnknown_KI_21() {
        when(mojangClient.getUuidByUsername("Notch")).thenThrow(new UpstreamUnavailableException("Mojang", null));

        assertThatThrownBy(() -> verificationService.verify(new VerifyRequest("123", "Notch", "Notch")))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void verify_hypixelUnavailable_propagatesInsteadOfSayingNoDiscordIsLinked_KI_21() {
        UUID uuid = UUID.randomUUID();
        when(mojangClient.getUuidByUsername("Notch")).thenReturn(uuid);
        when(hypixelClient.getLinkedDiscord(uuid)).thenThrow(new UpstreamUnavailableException("Hypixel", null));

        assertThatThrownBy(() -> verificationService.verify(new VerifyRequest("123", "Notch", "Notch")))
                .isInstanceOf(UpstreamUnavailableException.class);
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
    void verify_discordMismatch_throwsDiscordMismatchException() {
        UUID uuid = UUID.randomUUID();
        when(mojangClient.getUuidByUsername("Notch")).thenReturn(uuid);
        when(hypixelClient.getLinkedDiscord(uuid)).thenReturn("OtherUser");

        VerifyRequest req = new VerifyRequest("123", "Notch", "Notch");
        assertThatThrownBy(() -> verificationService.verify(req))
                .isInstanceOf(DiscordMismatchException.class)
                .extracting(e -> ((DiscordMismatchException) e).getLinkedDiscordUsername())
                .isEqualTo("OtherUser");
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
        verify(playerLinkService).linkDiscord(uuid, "123456789");
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
