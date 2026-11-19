package net.bowspleef.ranked.controller;

import net.bowspleef.ranked.exception.ScoreOutOfRangeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.MatchListResponse;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.exception.GameTypeNotFoundException;
import net.bowspleef.ranked.exception.IdempotencyKeyReusedException;
import net.bowspleef.ranked.exception.MatchNotFoundException;
import net.bowspleef.ranked.service.MatchRecording;
import net.bowspleef.ranked.service.MatchService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(matchService.recordMatch(any(), isNull())).thenReturn(new MatchRecording(response, true));

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

    /** A match body as the plugin sends it, with the raw JSON of its modifiers (spec 010). */
    private String bodyWithModifiers(String modifiersJson) {
        return "{\"gameType\":\"BEDWARS\",\"source\":\"hysea-server\",\"teams\":["
                + "{\"score\":1,\"playerUuids\":[\"" + UUID.randomUUID() + "\"]},"
                + "{\"score\":0,\"playerUuids\":[\"" + UUID.randomUUID() + "\"]}]"
                + (modifiersJson == null ? "" : ",\"modifiers\":" + modifiersJson) + "}";
    }

    private static String entries(int count) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < count; i++) {
            json.append(i == 0 ? "" : ",").append("\"m").append(i).append("\":true");
        }
        return json.append("}").toString();
    }

    @Test
    void createMatch_withModifiers_passesThemToTheService_REQ_010_08() throws Exception {
        when(matchService.recordMatch(any(), isNull())).thenReturn(new MatchRecording(stored(), true));

        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers("{\"no-collision\":true,\"perks\":20}")))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<CreateMatchRequest> sent = org.mockito.ArgumentCaptor.forClass(CreateMatchRequest.class);
        org.mockito.Mockito.verify(matchService).recordMatch(sent.capture(), isNull());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().modifiers())
                .isEqualTo(java.util.Map.of("no-collision", true, "perks", 20));
    }

    @Test
    void createMatch_withoutModifiers_isStillAccepted_REQ_010_09() throws Exception {
        when(matchService.recordMatch(any(), isNull())).thenReturn(new MatchRecording(stored(), true));

        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers(null)))
                .andExpect(status().isCreated());

        org.mockito.ArgumentCaptor<CreateMatchRequest> sent = org.mockito.ArgumentCaptor.forClass(CreateMatchRequest.class);
        org.mockito.Mockito.verify(matchService).recordMatch(sent.capture(), isNull());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().modifiers()).isNull();
    }

    @Test
    void createMatch_emptyModifiers_isAccepted_REQ_010_08() throws Exception {
        when(matchService.recordMatch(any(), isNull())).thenReturn(new MatchRecording(stored(), true));

        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers("{}")))
                .andExpect(status().isCreated());
    }

    @Test
    void createMatch_twentyModifiers_isAccepted_REQ_010_08() throws Exception {
        when(matchService.recordMatch(any(), isNull())).thenReturn(new MatchRecording(stored(), true));

        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers(entries(20))))
                .andExpect(status().isCreated());
    }

    @Test
    void createMatch_twentyOneModifiers_returns400_REQ_010_08() throws Exception {
        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers(entries(21))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createMatch_aKeyOfFiftyOneCharacters_returns400_REQ_010_08() throws Exception {
        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers("{\"" + "k".repeat(51) + "\":true}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createMatch_aNestedModifierValue_returns400_REQ_010_08() throws Exception {
        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers("{\"perks\":{\"deep\":1}}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createMatch_modifiersThatAreNotAnObject_returns400_REQ_010_08() throws Exception {
        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers("[\"no-collision\"]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMatch_returnsItsModifiers_REQ_010_08() throws Exception {
        MatchResponse withModifiers = new MatchResponse(5L, "BEDWARS", "Bed Wars", "hysea-server", OffsetDateTime.now(),
                List.of(), null, null, java.util.Map.of("perks", 20));
        when(matchService.getMatch(5L)).thenReturn(withModifiers);

        mockMvc.perform(get("/api/v1/matches/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifiers.perks").value(20));
    }

    private String body() throws Exception {
        return objectMapper.writeValueAsString(new CreateMatchRequest("BEDWARS", "hysea-server",
                List.of(new TeamRequest(3, List.of(UUID.randomUUID())),
                        new TeamRequest(1, List.of(UUID.randomUUID()))),
                null));
    }

    private MatchResponse stored() {
        return new MatchResponse(5L, "BEDWARS", "Bed Wars", "hysea-server", OffsetDateTime.now(), List.of(), null, null);
    }

    @Test
    void createMatch_newIdempotencyKey_returns201_REQ_002_01() throws Exception {
        when(matchService.recordMatch(any(), eq("game-1"))).thenReturn(new MatchRecording(stored(), true));

        mockMvc.perform(post("/api/v1/matches").header("Idempotency-Key", "game-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createMatch_replayedIdempotencyKey_returns200WithTheStoredMatch_REQ_002_02() throws Exception {
        when(matchService.recordMatch(any(), eq("game-1"))).thenReturn(new MatchRecording(stored(), false));

        mockMvc.perform(post("/api/v1/matches").header("Idempotency-Key", "game-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createMatch_keyReusedForAnotherMatch_returns409_REQ_002_10() throws Exception {
        when(matchService.recordMatch(any(), eq("game-1")))
                .thenThrow(new IdempotencyKeyReusedException("hysea-server", "game-1"));

        mockMvc.perform(post("/api/v1/matches").header("Idempotency-Key", "game-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void createMatch_blankIdempotencyKey_returns400_REQ_002_08() throws Exception {
        mockMvc.perform(post("/api/v1/matches").header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(matchService);
    }

    @Test
    void createMatch_tooLongIdempotencyKey_returns400_REQ_002_08() throws Exception {
        mockMvc.perform(post("/api/v1/matches").header("Idempotency-Key", "k".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(matchService);
    }

    @Test
    void createMatch_keyOfExactlyTheMaximumLength_isAccepted_REQ_002_08() throws Exception {
        when(matchService.recordMatch(any(), eq("k".repeat(100)))).thenReturn(new MatchRecording(stored(), true));

        mockMvc.perform(post("/api/v1/matches").header("Idempotency-Key", "k".repeat(100))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());
    }

    /** The page and size limits of the signature are enforced: an unbounded size would load every match one by one. */
    @Test
    void getMatchesByGameType_pageAndSizeOutOfBounds_return400() throws Exception {
        mockMvc.perform(get("/api/v1/matches").param("gameType", "BEDWARS").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/matches").param("gameType", "BEDWARS").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/matches").param("gameType", "BEDWARS").param("page", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(matchService);
    }

    @Test
    void getMatchesByGameType_maximumSize_isAccepted() throws Exception {
        when(matchService.getMatchesByGameType("BEDWARS", 0, 100)).thenReturn(MatchListResponse.of("BEDWARS", "Bed Wars", List.of(), 0, 100, 0L));

        mockMvc.perform(get("/api/v1/matches").param("gameType", "BEDWARS").param("size", "100")).andExpect(status().isOk());
    }

    // ---- Spec 034, KI-82: scores ---------------------------------------------------------------------

    @Test
    void createMatch_aNegativeScore_returns400_REQ_034_14() throws Exception {
        String body = "{\"gameType\":\"BEDWARS\",\"source\":\"hysea-server\",\"teams\":["
                + "{\"score\":-1,\"playerUuids\":[\"" + UUID.randomUUID() + "\"]},"
                + "{\"score\":0,\"playerUuids\":[\"" + UUID.randomUUID() + "\"]}]}";
        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createMatch_aRankedScoreAboveTheCap_returns400_REQ_034_07() throws Exception {
        when(matchService.recordMatch(any(), any())).thenThrow(new ScoreOutOfRangeException("DUEL_1V1", 5, 4));
        mockMvc.perform(post("/api/v1/matches").contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithModifiers(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
