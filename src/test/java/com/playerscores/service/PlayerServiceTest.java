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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerMapper playerMapper;

    @InjectMocks
    private PlayerService playerService;

    @Test
    void createPlayer_returnsResponse() {
        doAnswer(invocation -> {
            Player p = invocation.getArgument(0);
            p.setId(1L);
            p.setCreatedAt(LocalDateTime.now());
            return null;
        }).when(playerMapper).insert(any(Player.class));

        PlayerResponse response = playerService.createPlayer(new PlayerRequest("Notch"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("Notch");
    }

    @Test
    void getPlayer_notFound_throwsException() {
        when(playerMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.getPlayer(99L))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void getPlayer_found_returnsResponse() {
        Player player = new Player();
        player.setId(1L);
        player.setUsername("Notch");
        player.setCreatedAt(LocalDateTime.now());
        when(playerMapper.findById(1L)).thenReturn(Optional.of(player));

        PlayerResponse response = playerService.getPlayer(1L);

        assertThat(response.username()).isEqualTo("Notch");
    }
}
