package com.playerscores.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.LeaderboardEntryResponse;
import com.playerscores.dto.PageResponse;
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
        when(scoreService.recordScore(eq(new ScoreRequest(1L, 300, "bedwars"))))
                .thenReturn(new ScoreResponse(1L, 1L, 300, "bedwars", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ScoreRequest(1L, 300, "bedwars"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value(300))
                .andExpect(jsonPath("$.game").value("bedwars"));
    }

    @Test
    void getScores_returns200() throws Exception {
        PageResponse<ScoreResponse> page = PageResponse.of(
                List.of(new ScoreResponse(1L, 1L, 100, "skywars", LocalDateTime.now())), 0, 20, 1L);
        when(scoreService.getScores(eq(1L), isNull(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/scores").param("playerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].value").value(100));
    }

    @Test
    void getLeaderboard_returns200() throws Exception {
        when(scoreService.getLeaderboard(isNull(), eq(10)))
                .thenReturn(List.of(new LeaderboardEntryResponse(1L, "Notch", 999, "bedwars")));

        mockMvc.perform(get("/api/v1/scores/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Notch"))
                .andExpect(jsonPath("$[0].bestScore").value(999));
    }
}
