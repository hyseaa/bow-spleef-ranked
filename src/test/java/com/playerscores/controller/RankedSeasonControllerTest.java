package com.playerscores.controller;

import com.playerscores.dto.RankedSeasonRequest;
import com.playerscores.dto.RankedSeasonResponse;
import com.playerscores.model.RatingAlgorithm;
import com.playerscores.service.RankedSeasonService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REQ-001-10: every season returned by the API carries its rating algorithm. */
@WebMvcTest(value = RankedSeasonController.class, excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        MybatisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
})
class RankedSeasonControllerTest {

    private static final RankedSeasonResponse CLASSIC = new RankedSeasonResponse(
            1L, "Season 1", OffsetDateTime.parse("2026-04-14T20:00:00Z"), OffsetDateTime.parse("2026-07-10T20:00:00Z"),
            "bowspleef_1v1", false, RatingAlgorithm.CLASSIC_ELO);
    private static final RankedSeasonResponse OPENSKILL = new RankedSeasonResponse(
            2L, "Season 2", OffsetDateTime.parse("2026-07-16T20:00:00Z"), null,
            "bowspleef_1v1", true, RatingAlgorithm.OPENSKILL);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RankedSeasonService rankedSeasonService;

    @Test
    void list_exposesRatingAlgorithm_REQ_001_10() throws Exception {
        when(rankedSeasonService.getAllSeasons()).thenReturn(List.of(OPENSKILL, CLASSIC));

        mockMvc.perform(get("/api/v1/ranked-seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ratingAlgorithm").value("OPENSKILL"))
                .andExpect(jsonPath("$[1].ratingAlgorithm").value("CLASSIC_ELO"));
    }

    @Test
    void get_exposesRatingAlgorithm_REQ_001_10() throws Exception {
        when(rankedSeasonService.getSeason(1L)).thenReturn(CLASSIC);

        mockMvc.perform(get("/api/v1/ranked-seasons/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingAlgorithm").value("CLASSIC_ELO"));
    }

    @Test
    void create_exposesRatingAlgorithm_REQ_001_10() throws Exception {
        when(rankedSeasonService.createSeason(any(RankedSeasonRequest.class))).thenReturn(OPENSKILL);

        mockMvc.perform(post("/api/v1/ranked-seasons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Season 2\",\"gameType\":\"bowspleef_1v1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratingAlgorithm").value("OPENSKILL"));
    }

    @Test
    void deactivate_exposesRatingAlgorithm_REQ_001_10() throws Exception {
        when(rankedSeasonService.deactivateSeason(1L)).thenReturn(CLASSIC);

        mockMvc.perform(post("/api/v1/ranked-seasons/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingAlgorithm").value("CLASSIC_ELO"));
    }
}
