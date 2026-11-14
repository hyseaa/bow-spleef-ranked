package net.bowspleef.ranked.service;

import net.bowspleef.ranked.client.MojangClient;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsernameCacheTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private MojangClient mojangClient;

    @InjectMocks
    private UsernameCache usernameCache;

    @Test
    void get_cacheHit_returnsCachedUsername() {
        UUID uuid = UUID.randomUUID();
        Player player = new Player();
        player.setUuid(uuid);
        player.setUsername("Notch");
        player.setUsernameCachedAt(OffsetDateTime.now().minusDays(1));
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.of(player));

        String result = usernameCache.get(uuid);

        assertThat(result).isEqualTo("Notch");
        verify(mojangClient, never()).getUsername(uuid);
    }

    @Test
    void get_cacheExpired_fetchesFromMojang() {
        UUID uuid = UUID.randomUUID();
        Player player = new Player();
        player.setUuid(uuid);
        player.setUsername("OldName");
        player.setUsernameCachedAt(OffsetDateTime.now().minusDays(20));
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.of(player));
        when(mojangClient.getUsername(uuid)).thenReturn("NewName");

        String result = usernameCache.get(uuid);

        assertThat(result).isEqualTo("NewName");
        verify(playerMapper).updateUsernameCache(uuid, "NewName");
    }

    @Test
    void get_noPlayer_fetchesFromMojang() {
        UUID uuid = UUID.randomUUID();
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.empty());
        when(mojangClient.getUsername(uuid)).thenReturn("Notch");

        String result = usernameCache.get(uuid);

        assertThat(result).isEqualTo("Notch");
    }

    /** Mojang failing must not hide a name we already know, even an expired one. */
    @Test
    void get_mojangFails_keepsTheExpiredNameInsteadOfNothing() {
        UUID uuid = UUID.randomUUID();
        Player player = new Player();
        player.setUuid(uuid);
        player.setUsername("OldName");
        player.setUsernameCachedAt(OffsetDateTime.now().minusDays(20));
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.of(player));
        when(mojangClient.getUsername(uuid)).thenReturn(null);

        assertThat(usernameCache.get(uuid)).isEqualTo("OldName");
        verify(playerMapper, never()).updateUsernameCache(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** A name Mojang cannot give is not asked for again at every request: each ask costs up to 8 s and feeds the rate limit. */
    @Test
    void get_afterAFailureMojangIsNotAskedAgainForAWhile() {
        UUID uuid = UUID.randomUUID();
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.empty());
        when(mojangClient.getUsername(uuid)).thenReturn(null);

        assertThat(usernameCache.get(uuid)).isNull();
        assertThat(usernameCache.get(uuid)).isNull();
        assertThat(usernameCache.get(uuid)).isNull();

        verify(mojangClient, times(1)).getUsername(uuid);
    }

    @Test
    void get_afterTheWaitMojangIsAskedAgainAndASuccessIsKept() {
        UUID uuid = UUID.randomUUID();
        java.time.Instant start = java.time.Instant.parse("2026-11-12T10:00:00Z");
        org.springframework.test.util.ReflectionTestUtils.setField(usernameCache, "clock",
                java.time.Clock.fixed(start, java.time.ZoneOffset.UTC));
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.empty());
        when(mojangClient.getUsername(uuid)).thenReturn(null, "Notch");

        assertThat(usernameCache.get(uuid)).isNull();
        org.springframework.test.util.ReflectionTestUtils.setField(usernameCache, "clock",
                java.time.Clock.fixed(start.plusSeconds(6 * 60), java.time.ZoneOffset.UTC));

        assertThat(usernameCache.get(uuid)).isEqualTo("Notch");
        verify(mojangClient, times(2)).getUsername(uuid);
    }
}
