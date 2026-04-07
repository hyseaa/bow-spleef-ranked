package com.playerscores.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.LeaderboardEntryDto;
import com.playerscores.dto.ScoreRequest;
import com.playerscores.dto.ScoreResponse;
import com.playerscores.service.ScoreService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ScoreController.class, excludeAutoConfiguration = {DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class})
class ScoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScoreService scoreService;

    @Test
    void recordScore_returns201() throws Exception {
        ScoreResponse response = new ScoreResponse();
        response.setId(1L);
        response.setPlayerId(1L);
        response.setValue(300);
        response.setGame("bedwars");
        response.setCreatedAt(LocalDateTime.now());
        when(scoreService.recordScore(any())).thenReturn(response);

        ScoreRequest request = new ScoreRequest();
        request.setPlayerId(1L);
        request.setValue(300);
        request.setGame("bedwars");

        mockMvc.perform(post("/api/v1/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(300))
                .andExpect(jsonPath("$.game").value("bedwars"));
    }

    @Test
    void getScores_returns200() throws Exception {
        ScoreResponse score = new ScoreResponse();
        score.setId(1L);
        score.setPlayerId(1L);
        score.setValue(100);
        score.setGame("skywars");
        score.setCreatedAt(LocalDateTime.now());

        com.playerscores.dto.PageResponse<ScoreResponse> page =
                com.playerscores.dto.PageResponse.of(List.of(score), 0, 20, 1L);
        when(scoreService.getScores(eq(1L), isNull(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/scores").param("playerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].value").value(100));
    }

    @Test
    void getLeaderboard_returns200() throws Exception {
        LeaderboardEntryDto entry = new LeaderboardEntryDto();
        entry.setPlayerId(1L);
        entry.setUsername("Notch");
        entry.setBestScore(999);
        entry.setGame("bedwars");
        when(scoreService.getLeaderboard(isNull(), eq(10))).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/scores/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Notch"))
                .andExpect(jsonPath("$[0].bestScore").value(999));
    }
}
