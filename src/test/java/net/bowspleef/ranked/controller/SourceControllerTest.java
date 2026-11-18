package net.bowspleef.ranked.controller;

import net.bowspleef.ranked.dto.SourceRequest;
import net.bowspleef.ranked.dto.SourceResponse;
import net.bowspleef.ranked.exception.SourceAlreadyExistsException;
import net.bowspleef.ranked.model.SourceKind;
import net.bowspleef.ranked.service.SourceService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec 033: the two endpoints of the sources of matches. */
@WebMvcTest(value = SourceController.class, excludeAutoConfiguration = {
        DataSourceAutoConfiguration.class,
        MybatisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration.class
})
class SourceControllerTest {

    private static final String URL = "/api/v1/sources";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SourceService service;

    @Test
    void getListsEverySourceWithItsKind_REQ_033_03() throws Exception {
        when(service.getAll()).thenReturn(List.of(
                new SourceResponse("discord_bot", "Discord bot", SourceKind.BOT),
                new SourceResponse("eu-test-server", "Hy Sea's EU server", SourceKind.CUSTOM_SERVER)));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("discord_bot"))
                .andExpect(jsonPath("$[0].kind").value("bot"))
                .andExpect(jsonPath("$[1].displayName").value("Hy Sea's EU server"))
                .andExpect(jsonPath("$[1].kind").value("custom_server"));
    }

    @Test
    void postCreatesTheSource_REQ_033_04() throws Exception {
        SourceRequest request = new SourceRequest("asia-server", "Hy Sea's Asia server", SourceKind.CUSTOM_SERVER);
        when(service.create(request))
                .thenReturn(new SourceResponse("asia-server", "Hy Sea's Asia server", SourceKind.CUSTOM_SERVER));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"asia-server\",\"displayName\":\"Hy Sea's Asia server\",\"kind\":\"custom_server\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("asia-server"))
                .andExpect(jsonPath("$.kind").value("custom_server"));
    }

    @Test
    void aNameAlreadyRegisteredAnswersConflictWithItsCode_REQ_033_04() throws Exception {
        when(service.create(any())).thenThrow(new SourceAlreadyExistsException("hysea-server"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"hysea-server\",\"displayName\":\"X\",\"kind\":\"custom_server\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCE_ALREADY_EXISTS"));
    }

    @Test
    void aBlankNameIsRefused_REQ_033_04() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\" \",\"displayName\":\"X\",\"kind\":\"bot\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void aNameOverFiftyCharactersIsRefused_REQ_033_04() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"" + "a".repeat(51) + "\",\"displayName\":\"X\",\"kind\":\"bot\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void aKindOtherThanBotAndCustomServerIsRefused_REQ_033_04() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"x\",\"displayName\":\"X\",\"kind\":\"hypixel\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }

    @Test
    void aMissingKindIsRefused_REQ_033_04() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(
                        "{\"name\":\"x\",\"displayName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }
}
