package com.playerscores.controller;

import com.playerscores.dto.LeaderboardEntryResponse;
import com.playerscores.dto.PageResponse;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PlayerController.class, excludeAutoConfiguration = {DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class})
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerService playerService;

    @Test
    void upsertPlayer_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(playerService.upsertPlayer(uuid)).thenReturn(new PlayerResponse(uuid, "Notch"));

        mockMvc.perform(put("/api/v1/players/" + uuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Notch"));
    }

    @Test
    void getPlayer_found_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(playerService.getPlayer(uuid)).thenReturn(new PlayerResponse(uuid, "Notch"));

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

    @Test
    void getLeaderboard_returnsPage() throws Exception {
        UUID uuid = UUID.randomUUID();
        PageResponse<LeaderboardEntryResponse> page = PageResponse.of(
                List.of(new LeaderboardEntryResponse(uuid, "Notch", 5)), 0, 20, 1);
        when(playerService.getLeaderboard(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/players/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("Notch"))
                .andExpect(jsonPath("$.content[0].wins").value(5))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getLeaderboard_invalidSize_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/players/leaderboard").param("size", "200"))
                .andExpect(status().isBadRequest());
    }
}
