package com.playerscores.controller;

import com.playerscores.dto.DuelChallengeResponse;
import com.playerscores.exception.DuelCancelAlreadyRequestedException;
import com.playerscores.exception.DuelCancelNeedsAgreementException;
import com.playerscores.exception.DuelChallengeForbiddenException;
import com.playerscores.exception.DuelChallengeNotPendingException;
import com.playerscores.service.DuelChallengeService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 003: the endpoints of the cancellation by agreement and their error codes. */
@WebMvcTest(value = DuelChallengeController.class, excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        MybatisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
})
class DuelChallengeControllerTest {

    private static final UUID REQUESTER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DuelChallengeService duelChallengeService;

    private static DuelChallengeResponse duel(String status, UUID cancelRequestedBy) {
        return new DuelChallengeResponse(7L, REQUESTER, "Alice", UUID.randomUUID(), "Bob", List.of(), List.of(),
                "gt", status, null, null, null, null, null, null, cancelRequestedBy);
    }

    @Test
    void cancelRequest_returns200WithTheRequester_REQ_003_01() throws Exception {
        when(duelChallengeService.requestCancel(7L, "d-1")).thenReturn(duel("ACTIVE", REQUESTER));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-request").param("discordId", "d-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.cancelRequestedBy").value(REQUESTER.toString()));
    }

    @Test
    void cancelAccept_returns200WithACancelledDuel_REQ_003_02() throws Exception {
        when(duelChallengeService.acceptCancel(7L, "d-2")).thenReturn(duel("CANCELLED", REQUESTER));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-accept").param("discordId", "d-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelDecline_returns200WithoutARequest_REQ_003_03() throws Exception {
        when(duelChallengeService.declineCancel(7L, "d-2")).thenReturn(duel("ACTIVE", null));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-decline").param("discordId", "d-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelRequestedBy").doesNotExist());
    }

    @Test
    void notAParticipantOrTheRequester_returns403_REQ_003_04() throws Exception {
        when(duelChallengeService.acceptCancel(7L, "d-1"))
                .thenThrow(new DuelChallengeForbiddenException("The player who asked cannot answer"));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-accept").param("discordId", "d-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DUEL_CHALLENGE_FORBIDDEN"));
    }

    @Test
    void notCancellable_returns409NotPending_REQ_003_06() throws Exception {
        when(duelChallengeService.requestCancel(7L, "d-1"))
                .thenThrow(new DuelChallengeNotPendingException(7L, "COMPLETED"));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-request").param("discordId", "d-1"))
                .andExpect(status().isConflict());
    }

    @Test
    void secondRequest_returns409WithItsCode_REQ_003_07() throws Exception {
        when(duelChallengeService.requestCancel(7L, "d-2")).thenThrow(new DuelCancelAlreadyRequestedException(7L));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-request").param("discordId", "d-2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUEL_CANCEL_ALREADY_REQUESTED"));
    }

    @Test
    void cancelAloneOnAnActiveDuel_returns409WithItsCode_REQ_003_09() throws Exception {
        when(duelChallengeService.cancel(7L, "d-1")).thenThrow(new DuelCancelNeedsAgreementException(7L, "ACTIVE"));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel").param("discordId", "d-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUEL_CANCEL_NEEDS_AGREEMENT"));
    }

    @Test
    void missingDiscordId_returns400AndCallsNothing() throws Exception {
        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-request")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-accept")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-decline")).andExpect(status().isBadRequest());
        verifyNoInteractions(duelChallengeService);
    }

    @Test
    void cancelPlayerLeft_returns200WithACancelledDuel_REQ_006_08() throws Exception {
        when(duelChallengeService.cancelPlayerLeft(7L, "d-1")).thenReturn(duel("CANCELLED", null));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-player-left").param("discordId", "d-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelPlayerLeft_errors_REQ_006_10() throws Exception {
        when(duelChallengeService.cancelPlayerLeft(7L, "outsider"))
                .thenThrow(new DuelChallengeForbiddenException("Only a participant can perform this action"));
        when(duelChallengeService.cancelPlayerLeft(8L, "d-1"))
                .thenThrow(new DuelChallengeNotPendingException(8L, "COMPLETED"));

        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-player-left").param("discordId", "outsider"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DUEL_CHALLENGE_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/duel-challenges/8/cancel-player-left").param("discordId", "d-1"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelPlayerLeft_missingDiscordId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/duel-challenges/7/cancel-player-left")).andExpect(status().isBadRequest());
        verifyNoInteractions(duelChallengeService);
    }
}
