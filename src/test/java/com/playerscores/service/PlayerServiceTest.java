package com.playerscores.service;

import com.playerscores.dto.PlayerRequest;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerMapper playerMapper;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void upsertPlayer_returnsResponse() {
        UUID uuid = UUID.randomUUID();
        Player player = new Player();
        player.setUuid(uuid);
        player.setUsername("Notch");
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.of(player));

        PlayerResponse response = playerService.upsertPlayer(uuid, new PlayerRequest("Notch"));

        assertThat(response.uuid()).isEqualTo(uuid);
        assertThat(response.username()).isEqualTo("Notch");
    }

    @Test
    void getPlayer_notFound_throwsException() {
        UUID uuid = UUID.randomUUID();
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayer(uuid))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void getPlayer_found_returnsResponse() {
        UUID uuid = UUID.randomUUID();
        Player player = new Player();
        player.setUuid(uuid);
        player.setUsername("Notch");
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.of(player));

        PlayerResponse response = playerService.getPlayer(uuid);

        assertThat(response.username()).isEqualTo("Notch");
    }
}
