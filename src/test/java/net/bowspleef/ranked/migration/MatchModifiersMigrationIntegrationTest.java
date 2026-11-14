package net.bowspleef.ranked.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-010-09: V29 adds match.modifiers (JSONB) without touching any existing match, which keeps none.
 * Runs Flyway by hand to stop at V28, seed data, then apply V29.
 */
@Testcontainers
class MatchModifiersMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateSeedAndUpgrade() throws SQLException {
        flyway("28").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('gt', 'GT', FALSE)");
            st.execute("INSERT INTO match (game_type, source) VALUES ('gt', 'discord_bot')");
            st.execute("INSERT INTO match (game_type, source) VALUES ('gt', 'hysea-server')");
        }
        flyway(null).migrate();
    }

    @Test
    void existingMatchesKeepNoModifiers_REQ_010_09() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*), COUNT(modifiers) FROM match")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(2);
            assertThat(rs.getInt(2)).isZero();
        }
    }

    @Test
    void theColumnHoldsAJsonObject_REQ_010_08() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO match (game_type, source, modifiers) "
                    + "VALUES ('gt', 'hysea-server', '{\"no-collision\": true, \"perks\": 20}'::jsonb)");
            try (ResultSet rs = st.executeQuery(
                    "SELECT modifiers->>'perks', data_type FROM match, information_schema.columns "
                            + "WHERE table_name = 'match' AND column_name = 'modifiers' AND modifiers IS NOT NULL")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("20");
                assertThat(rs.getString(2)).isEqualTo("jsonb");
            }
        }
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
