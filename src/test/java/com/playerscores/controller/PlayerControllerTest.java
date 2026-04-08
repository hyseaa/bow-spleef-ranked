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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void upsertPlayer_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(playerService.upsertPlayer(eq(uuid), any()))
                .thenReturn(new PlayerResponse(uuid, "Notch", LocalDateTime.now()));

        mockMvc.perform(put("/api/v1/players/" + uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlayerRequest("Notch"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Notch"));
    }

    @Test
    void upsertPlayer_blankUsername_returns400() throws Exception {
        UUID uuid = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/players/" + uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlayerRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPlayer_found_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(playerService.getPlayer(uuid))
                .thenReturn(new PlayerResponse(uuid, "Notch", LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/players/" + uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Notch"));
    }

    @Test
    void getPlayer_notFound_returns404() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(playerService.getPlayer(uuid)).thenThrow(new PlayerNotFoundException(uuid));

        mockMvc.perform(get("/api/v1/players/" + uuid))
                .andExpect(status().isNotFound());
    }
}
