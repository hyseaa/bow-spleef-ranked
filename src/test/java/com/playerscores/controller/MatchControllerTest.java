package com.playerscores.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.MatchListResponse;
import com.playerscores.dto.MatchResponse;
import com.playerscores.dto.TeamRequest;
import com.playerscores.exception.GameTypeNotFoundException;
import com.playerscores.exception.MatchNotFoundException;
import com.playerscores.service.MatchService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = MatchController.class, excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        MybatisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
})
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MatchService matchService;

    @Test
    void createMatch_returns201() throws Exception {
        MatchResponse response = new MatchResponse(1L, "BEDWARS", "Bed Wars", "DISCORD_BOT", OffsetDateTime.now(), List.of(), null, null);
        when(matchService.createMatch(any())).thenReturn(response);

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(UUID.randomUUID())),
                        new TeamRequest(1, List.of(UUID.randomUUID()))),
                null);

        mockMvc.perform(post("/api/v1/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameType").value("BEDWARS"));
    }

    @Test
    void getMatch_found_returns200() throws Exception {
        MatchResponse response = new MatchResponse(1L, "SKYWARS", "Sky Wars", "FRONT", OffsetDateTime.now(), List.of(), null, null);
        when(matchService.getMatch(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/matches/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("SKYWARS"));
    }

    @Test
    void getMatch_notFound_returns404() throws Exception {
        when(matchService.getMatch(99L)).thenThrow(new MatchNotFoundException(99L));

        mockMvc.perform(get("/api/v1/matches/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMatchesByGameType_returns200() throws Exception {
        MatchResponse match = new MatchResponse(1L, "BEDWARS", "Bed Wars", "DISCORD_BOT", OffsetDateTime.now(), List.of(), null, null);
        MatchListResponse response = MatchListResponse.of("BEDWARS", "Bed Wars", List.of(match), 0, 20, 1L);
        when(matchService.getMatchesByGameType("BEDWARS", 0, 20)).thenReturn(response);

        mockMvc.perform(get("/api/v1/matches").param("gameType", "BEDWARS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("BEDWARS"))
                .andExpect(jsonPath("$.gameTypeDisplayName").value("Bed Wars"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getMatchesByGameType_missingGameType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/matches"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMatchesByGameType_unknownGameType_returns404() throws Exception {
        when(matchService.getMatchesByGameType("UNKNOWN", 0, 20)).thenThrow(new GameTypeNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/matches").param("gameType", "UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatch_returns204() throws Exception {
        doNothing().when(matchService).deleteMatch(1L);

        mockMvc.perform(delete("/api/v1/matches/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteMatch_notFound_returns404() throws Exception {
        doThrow(new MatchNotFoundException(99L)).when(matchService).deleteMatch(99L);

        mockMvc.perform(delete("/api/v1/matches/99"))
                .andExpect(status().isNotFound());
    }
}
