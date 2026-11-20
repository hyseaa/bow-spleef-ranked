package net.bowspleef.ranked.config;

import net.bowspleef.ranked.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API key protects everything except Swagger, the health probe (KI-18) and the deployment info (spec 039). */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${api.key}")
    private String apiKey;

    @Test
    void healthIsOpenSoThatAContainerHealthCheckNeedsNoKey_KI_18() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsStayBehindTheKey_KI_18() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
    }

    @Test
    void infoNeedsNoKeySoThatTheDeploymentCanReadIt_REQ_039_10() throws Exception {
        // the test configuration does not expose info (404); the point here is that the key is not asked for (no 403).
        // Exposed with its content in the prod profile: ProdProfileIntegrationTest
        mockMvc.perform(get("/actuator/info"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void theApiStillNeedsTheKey() throws Exception {
        mockMvc.perform(get("/api/v1/game-types")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/game-types").header("X-Api-Key", apiKey)).andExpect(status().isOk());
    }
}
