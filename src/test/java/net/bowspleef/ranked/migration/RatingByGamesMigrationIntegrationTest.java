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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-034-11, REQ-034-12: V34 accepts OPENSKILL_GAMES, makes it the default, and switches the active OpenSkill seasons
 * to it, marked dirty; finished seasons and classic Elo seasons are untouched. Runs Flyway by hand to stop at V33.
 */
@Testcontainers
class RatingByGamesMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateSeedAndUpgrade() throws SQLException {
        flyway("33").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('one', 'One', TRUE), ('two', 'Two', TRUE)");
            st.execute("INSERT INTO ranked_season (name, game_type, active, rating_algorithm) VALUES "
                    + "('finished', 'one', FALSE, 'OPENSKILL'),"
                    + "('classic', 'one', FALSE, 'CLASSIC_ELO'),"
                    + "('season 3', 'one', TRUE, 'OPENSKILL'),"
                    + "('2v2', 'two', TRUE, 'OPENSKILL')");
        }
        flyway(null).migrate();
    }

    @Test
    void activeOpenSkillSeasonsSwitchAndAreDirty_finishedOnesAreUntouched_REQ_034_11_REQ_034_12() throws SQLException {
        Map<String, String> seasons = new LinkedHashMap<>();
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, rating_algorithm, elo_dirty FROM ranked_season WHERE id <= 4 ORDER BY id")) {
            while (rs.next()) {
                seasons.put(rs.getString(1), rs.getString(2) + " " + rs.getBoolean(3));
            }
        }
        assertThat(seasons).containsExactly(
                Map.entry("finished", "OPENSKILL false"),
                Map.entry("classic", "CLASSIC_ELO false"),
                Map.entry("season 3", "OPENSKILL_GAMES true"),
                Map.entry("2v2", "OPENSKILL_GAMES true"));
    }

    @Test
    void newSeasonsDefaultToOpenSkillGames_REQ_034_11() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO ranked_season (name, game_type, active) VALUES ('new', 'one', FALSE)");
            try (ResultSet rs = st.executeQuery("SELECT rating_algorithm FROM ranked_season WHERE name = 'new'")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("OPENSKILL_GAMES");
            }
        }
    }

    @Test
    void anUnknownAlgorithmIsRefused() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO ranked_season (name, game_type, active, rating_algorithm) VALUES ('x', 'one', FALSE, 'GLICKO')"))
                    .isInstanceOf(SQLException.class);
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
