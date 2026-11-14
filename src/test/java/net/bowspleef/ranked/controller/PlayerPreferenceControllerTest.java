package net.bowspleef.ranked.controller;

import net.bowspleef.ranked.dto.PreferencesResponse;
import net.bowspleef.ranked.dto.UpdatePreferencesRequest;
import net.bowspleef.ranked.exception.InvalidPreferenceException;
import net.bowspleef.ranked.service.PlayerPreferenceService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 016, ADR-0023: the two endpoints of the preferences of a player. */
@WebMvcTest(value = PlayerPreferenceController.class, excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        MybatisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
})
class PlayerPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlayerPreferenceService service;

    private static String url(UUID uuid) {
        return "/api/v1/players/" + uuid + "/preferences";
    }

    @Test
    void getAnswersTheDefaultsForAPlayerTheApiDoesNotKnow_REQ_016_09() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.get(uuid)).thenReturn(new PreferencesResponse(0));

        mockMvc.perform(get(url(uuid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tntRemovedPercent").value(0));
    }

    @Test
    void putAnswersThePreferencesAfterTheChange_REQ_016_09() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.update(uuid, new UpdatePreferencesRequest(40))).thenReturn(new PreferencesResponse(40));

        mockMvc.perform(put(url(uuid)).contentType(MediaType.APPLICATION_JSON).content("{\"tntRemovedPercent\": 40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tntRemovedPercent").value(40));
        verify(service).update(uuid, new UpdatePreferencesRequest(40));
    }

    @Test
    void theBoundsAreAccepted_REQ_016_02() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.update(any(), any())).thenReturn(new PreferencesResponse(0));

        for (String value : new String[] {"0", "100"}) {
            mockMvc.perform(put(url(uuid)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tntRemovedPercent\": " + value + "}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aValueAboveOneHundredIsRefused_REQ_016_02() throws Exception {
        mockMvc.perform(put(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tntRemovedPercent\": 101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void aNegativeValueIsRefused_REQ_016_02() throws Exception {
        mockMvc.perform(put(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tntRemovedPercent\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void aFractionIsRefusedNotRoundedDown_REQ_016_02() throws Exception {
        mockMvc.perform(put(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tntRemovedPercent\": 3.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void aTextIsRefusedNotConverted_REQ_016_02() throws Exception {
        mockMvc.perform(put(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tntRemovedPercent\": \"40\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void anEmptyBodyChangesNothingAndIsRefused_REQ_016_09() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(service.update(uuid, new UpdatePreferencesRequest(null)))
                .thenThrow(new InvalidPreferenceException("at least one preference is required"));

        mockMvc.perform(put(url(uuid)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void noBodyAtAllIsRefused_REQ_016_09() throws Exception {
        mockMvc.perform(put(url(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }
}
