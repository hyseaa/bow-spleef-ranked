package com.playerscores.service;

import com.playerscores.dto.ScoreRequest;
import com.playerscores.dto.ScoreResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.ScoreMapper;
import com.playerscores.model.Player;
import com.playerscores.model.Score;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private PlayerMapper playerMapper;

    @InjectMocks
    private ScoreService scoreService;

    @Test
    void recordScore_playerNotFound_throwsException() {
        when(playerMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scoreService.recordScore(new ScoreRequest(99L, 100, "bedwars")))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void recordScore_success_returnsResponse() {
        Player player = new Player();
        player.setId(1L);
        when(playerMapper.findById(1L)).thenReturn(Optional.of(player));

        doAnswer(invocation -> {
            Score s = invocation.getArgument(0);
            s.setId(10L);
            s.setCreatedAt(LocalDateTime.now());
            return null;
        }).when(scoreMapper).insert(any(Score.class));

        ScoreResponse response = scoreService.recordScore(new ScoreRequest(1L, 500, "bedwars"));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.value()).isEqualTo(500);
        assertThat(response.game()).isEqualTo("bedwars");
    }

    @Test
    void getScores_withGame_callsFilteredQuery() {
        Player player = new Player();
        player.setId(1L);
        when(playerMapper.findById(1L)).thenReturn(Optional.of(player));

        Score score = new Score();
        score.setId(1L);
        score.setPlayerId(1L);
        score.setValue(200);
        score.setGame("skywars");
        score.setCreatedAt(LocalDateTime.now());
        when(scoreMapper.findByPlayerIdAndGame(1L, "skywars", 20, 0)).thenReturn(List.of(score));
        when(scoreMapper.countByPlayerIdAndGame(1L, "skywars")).thenReturn(1L);

        var result = scoreService.getScores(1L, "skywars", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().game()).isEqualTo("skywars");
    }
}
