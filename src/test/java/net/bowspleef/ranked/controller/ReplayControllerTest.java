package net.bowspleef.ranked.controller;

import net.bowspleef.ranked.dto.PlayerSummaryResponse;
import net.bowspleef.ranked.dto.ReplayFile;
import net.bowspleef.ranked.dto.ReplaySummaryResponse;
import net.bowspleef.ranked.exception.InvalidReplayException;
import net.bowspleef.ranked.exception.MatchNotFoundException;
import net.bowspleef.ranked.exception.ReplayAlreadyExistsException;
import net.bowspleef.ranked.exception.ReplayNotFoundException;
import net.bowspleef.ranked.service.ReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 024, ADR-0026: the endpoints of the replays and their answers. */
@WebMvcTest(value = ReplayController.class, excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        MybatisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
})
class ReplayControllerTest {

    private static final byte[] BYTES = {1, 2, 3, 4};

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReplayService service;

    @BeforeEach
    void setUp() {
        when(service.maxSizeBytes()).thenReturn(10L);
    }

    private static MockHttpServletRequestBuilder upload(byte[] body) {
        return put("/api/v1/matches/412/replay")
                .param("map", "dread-pit").param("durationTicks", "690").param("formatVersion", "1")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(body);
    }

    @Test
    void aNewReplayAnswers201_REQ_024_03() throws Exception {
        when(service.store(412L, "dread-pit", 690, 1, BYTES)).thenReturn(true);

        mockMvc.perform(upload(BYTES)).andExpect(status().isCreated());
        verify(service).store(412L, "dread-pit", 690, 1, BYTES);
    }

    @Test
    void theSameReplayAgainAnswers200_REQ_024_14() throws Exception {
        when(service.store(412L, "dread-pit", 690, 1, BYTES)).thenReturn(false);

        mockMvc.perform(upload(BYTES)).andExpect(status().isOk());
    }

    @Test
    void anotherReplayForTheMatchAnswers409_REQ_024_14() throws Exception {
        when(service.store(anyLong(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new ReplayAlreadyExistsException(412L));

        mockMvc.perform(upload(BYTES))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLAY_ALREADY_EXISTS"));
    }

    @Test
    void aMissingMatchAnswers404_REQ_024_14() throws Exception {
        when(service.store(anyLong(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new MatchNotFoundException(412L));

        mockMvc.perform(upload(BYTES))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));
    }

    @Test
    void aBodyAboveTheLimitAnswers413WithoutReachingTheService_REQ_024_14() throws Exception {
        mockMvc.perform(upload(new byte[11]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REPLAY_TOO_LARGE"));
        verify(service, never()).store(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void anEmptyBodyOrAMissingParameterAnswers400_REQ_024_14() throws Exception {
        mockMvc.perform(upload(new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(put("/api/v1/matches/412/replay").param("map", "dread-pit")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(BYTES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(service, never()).store(anyLong(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void aParameterTheServiceRefusesAnswers400_REQ_024_14() throws Exception {
        when(service.store(anyLong(), anyString(), anyInt(), anyInt(), any()))
                .thenThrow(new InvalidReplayException("map must be …"));

        mockMvc.perform(upload(BYTES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aReplayIsReadBackWithItsHeaders_REQ_024_05() throws Exception {
        when(service.read(412L)).thenReturn(new ReplayFile("dread-pit", 690, 1, BYTES));

        mockMvc.perform(get("/api/v1/matches/412/replay"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(BYTES))
                .andExpect(header().string("Replay-Map", "dread-pit"))
                .andExpect(header().string("Replay-Duration-Ticks", "690"))
                .andExpect(header().string("Replay-Format-Version", "1"));
    }

    @Test
    void aMissingReplayAnswers404_REQ_024_10() throws Exception {
        when(service.read(412L)).thenThrow(new ReplayNotFoundException(412L));

        mockMvc.perform(get("/api/v1/matches/412/replay"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPLAY_NOT_FOUND"));
    }

    @Test
    void theListIsTheTenNewestByDefault_REQ_024_04() throws Exception {
        UUID alice = UUID.randomUUID();
        when(service.latest(null, 10)).thenReturn(List.of(new ReplaySummaryResponse(412L,
                OffsetDateTime.parse("2026-11-15T19:39:42Z"), "bowspleef_duel_ranked", "dread-pit", 690,
                List.of(new PlayerSummaryResponse(alice, "Alice")))));

        mockMvc.perform(get("/api/v1/replays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchId").value(412))
                .andExpect(jsonPath("$[0].map").value("dread-pit"))
                .andExpect(jsonPath("$[0].durationTicks").value(690))
                .andExpect(jsonPath("$[0].gameType").value("bowspleef_duel_ranked"))
                .andExpect(jsonPath("$[0].players[0].username").value("Alice"))
                .andExpect(jsonPath("$[0].data").doesNotExist());
    }

    @Test
    void theListOfAPlayerPassesTheNameAndTheSize_REQ_024_20() throws Exception {
        when(service.latest(eq("HySea"), eq(5))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/replays").param("username", "HySea").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verify(service).latest("HySea", 5);
    }

    @Test
    void aSizeOutOfItsRangeAnswers400_REQ_024_04() throws Exception {
        mockMvc.perform(get("/api/v1/replays").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/replays").param("size", "51")).andExpect(status().isBadRequest());
    }
}
