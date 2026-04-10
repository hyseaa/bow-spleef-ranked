package com.playerscores.service;

import com.playerscores.client.MojangClient;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Player;
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
}
