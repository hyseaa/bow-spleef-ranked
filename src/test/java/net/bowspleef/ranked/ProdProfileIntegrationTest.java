package net.bowspleef.ranked;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KI-20: the Docker setup runs the {@code prod} profile. The application starts with it (its datasource comes from
 * the SPRING_DATASOURCE_* variables docker-compose provides) and exposes the health endpoint only.
 */
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProdProfileIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void dockerStyleDatasource(DynamicPropertyRegistry registry) {
        registry.add("SPRING_DATASOURCE_URL", POSTGRES::getJdbcUrl);
        registry.add("SPRING_DATASOURCE_USERNAME", POSTGRES::getUsername);
        registry.add("SPRING_DATASOURCE_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Value("${api.key}")
    private String apiKey;

    @Test
    void startsWithTheProdProfileAndAnswersTheHealthProbe_KI_20() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void onlyHealthIsExposedEvenWithTheKey_KI_20() throws Exception {
        mockMvc.perform(get("/actuator/env").header("X-Api-Key", apiKey)).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/metrics").header("X-Api-Key", apiKey)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/game-types").header("X-Api-Key", apiKey)).andExpect(status().isOk());
    }
}
