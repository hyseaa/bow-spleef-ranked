package com.playerscores.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.PlayerRequest;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PlayerController.class, excludeAutoConfiguration = {DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class})
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlayerService playerService;

    @Test
    void createPlayer_returns201() throws Exception {
        when(playerService.createPlayer(any())).thenReturn(new PlayerResponse(1L, "Notch", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlayerRequest("Notch"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("Notch"));
    }

    @Test
    void createPlayer_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlayerRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPlayer_found_returns200() throws Exception {
        when(playerService.getPlayer(1L)).thenReturn(new PlayerResponse(1L, "Notch", LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Notch"));
    }

    @Test
    void getPlayer_notFound_returns404() throws Exception {
        when(playerService.getPlayer(99L)).thenThrow(new PlayerNotFoundException(99L));

        mockMvc.perform(get("/api/v1/players/99"))
                .andExpect(status().isNotFound());
    }
}
