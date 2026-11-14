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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-002-09, REQ-002-07: V26 adds match.idempotency_key without touching any existing row, and the
 * key is unique per source. Runs Flyway by hand to stop at V25, seed data, then apply V26.
 */
@Testcontainers
class MatchIdempotencyMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static List<String> matchesBefore;
    private static List<String> historyBefore;

    @BeforeAll
    static void migrateSeedAndUpgrade() throws SQLException {
        flyway("25").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('gt', 'GT', TRUE)");
            st.execute("INSERT INTO player (uuid) VALUES ('00000000-0000-0000-0000-000000000001')");
            st.execute("INSERT INTO ranked_season (name, game_type, active) VALUES ('s', 'gt', TRUE)");
            st.execute("INSERT INTO match (game_type, source, ranked_season_id) VALUES ('gt', 'test', 1)");
            st.execute("INSERT INTO match (game_type, source) VALUES ('gt', 'test')");
            st.execute("INSERT INTO elo_history (player_uuid, match_id, ranked_season_id, elo_before, elo_after, elo_change) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', 1, 1, 1000, 1020, 20)");
        }
        matchesBefore = rows("SELECT * FROM match ORDER BY id");
        historyBefore = rows("SELECT * FROM elo_history ORDER BY id");

        flyway(null).migrate();
    }

    @Test
    void existingRowsAreUnchangedAndHaveNoKey_REQ_002_09() throws SQLException {
        List<String> after = rows("SELECT * FROM match ORDER BY id");
        assertThat(after).hasSameSizeAs(matchesBefore);
        for (int i = 0; i < after.size(); i++) {
            assertThat(after.get(i)).startsWith(matchesBefore.get(i));
            assertThat(after.get(i)).contains("idempotency_key=null;"); // later migrations add columns after it
        }
        assertThat(rows("SELECT * FROM elo_history ORDER BY id")).isEqualTo(historyBefore);
    }

    @Test
    void aKeyIsUniquePerSourceButNotAcrossSources_REQ_002_07() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO match (game_type, source, idempotency_key) VALUES ('gt', 'unique-a', 'k1')");
            st.execute("INSERT INTO match (game_type, source, idempotency_key) VALUES ('gt', 'unique-b', 'k1')");
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO match (game_type, source, idempotency_key) VALUES ('gt', 'unique-a', 'k1')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void matchesWithoutKeyAreNeverConflicting_REQ_002_09() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO match (game_type, source) VALUES ('gt', 'nokey')");
            st.execute("INSERT INTO match (game_type, source) VALUES ('gt', 'nokey')");
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM match WHERE source = 'nokey'")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
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

    private static List<String> rows(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(rs.getMetaData().getColumnName(i)).append('=').append(rs.getString(i)).append(';');
                }
                out.add(row.toString());
            }
        }
        return out;
    }
}
