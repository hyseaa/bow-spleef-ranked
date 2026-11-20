package net.bowspleef.ranked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
 * the SPRING_DATASOURCE_* variables docker-compose provides) and exposes the health endpoint, and the info endpoint
 * that the deployment reads (spec 039).
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
    void infoGivesTheSchemaVersionOfTheLastMigrationWithoutTheKey_REQ_039_10() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(lastMigrationVersion()))
                .andExpect(jsonPath("$.commit").value("unknown"));
    }

    /** The highest {@code V<n>} of the migrations folder: the schema version once every migration has run. */
    private static int lastMigrationVersion() throws IOException {
        Pattern versioned = Pattern.compile("^V(\\d+)__.*\\.sql$");
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            return files.map(f -> versioned.matcher(f.getFileName().toString()))
                    .filter(Matcher::matches)
                    .mapToInt(m -> Integer.parseInt(m.group(1)))
                    .max()
                    .orElseThrow();
        }
    }

    @Test
    void onlyHealthAndInfoAreExposedEvenWithTheKey_KI_20() throws Exception {
        mockMvc.perform(get("/actuator/env").header("X-Api-Key", apiKey)).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/metrics").header("X-Api-Key", apiKey)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/game-types").header("X-Api-Key", apiKey)).andExpect(status().isOk());
    }
}
