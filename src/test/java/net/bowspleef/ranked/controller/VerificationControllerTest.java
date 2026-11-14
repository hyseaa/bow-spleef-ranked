package net.bowspleef.ranked.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.bowspleef.ranked.dto.VerifiedPlayerResponse;
import net.bowspleef.ranked.dto.VerifyRequest;
import net.bowspleef.ranked.exception.DiscordAlreadyLinkedException;
import net.bowspleef.ranked.exception.UpstreamUnavailableException;
import net.bowspleef.ranked.exception.VerificationException;
import net.bowspleef.ranked.service.VerificationService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = VerificationController.class, excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        MybatisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
})
class VerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VerificationService verificationService;

    @Test
    void verify_success_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        VerifyRequest req = new VerifyRequest("123456789", "Notch", "Notch");
        when(verificationService.verify(any())).thenReturn(new VerifiedPlayerResponse(uuid, "Notch", "123456789"));

        mockMvc.perform(post("/api/v1/players/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discordId").value("123456789"))
                .andExpect(jsonPath("$.username").value("Notch"));
    }

    @Test
    void verify_discordMismatch_returns422() throws Exception {
        VerifyRequest req = new VerifyRequest("123456789", "Notch", "Notch");
        when(verificationService.verify(any())).thenThrow(
                new VerificationException("Discord linked on Hypixel does not match."));

        mockMvc.perform(post("/api/v1/players/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void verify_upstreamUnavailable_returns503WithItsCode_KI_21() throws Exception {
        VerifyRequest req = new VerifyRequest("123456789", "Notch", "Notch");
        when(verificationService.verify(any())).thenThrow(new UpstreamUnavailableException("Hypixel", null));

        mockMvc.perform(post("/api/v1/players/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void verify_discordAlreadyLinkedToAnotherAccount_returns409WithItsCode() throws Exception {
        VerifyRequest req = new VerifyRequest("123456789", "Notch", "Notch");
        when(verificationService.verify(any())).thenThrow(new DiscordAlreadyLinkedException());

        mockMvc.perform(post("/api/v1/players/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISCORD_ALREADY_LINKED"));
    }

    @Test
    void getVerifiedPlayers_returns200() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(verificationService.getVerifiedPlayers())
                .thenReturn(List.of(new VerifiedPlayerResponse(uuid, "Notch", "123456789")));

        mockMvc.perform(get("/api/v1/players/verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("Notch"));
    }
}
