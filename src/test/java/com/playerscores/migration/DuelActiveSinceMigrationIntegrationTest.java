package com.playerscores.migration;

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

/**
 * REQ-006-03: V28 adds duel_challenge.active_since. A duel that is ACTIVE when it runs gets the migration time
 * (so nothing is cancelled by surprise on the day of the deployment), the others keep a null value, and no
 * other column changes. Runs Flyway by hand to stop at V27, seed data, then apply V28.
 */
@Testcontainers
class DuelActiveSinceMigrationIntegrationTest {

    private static final String ALICE = "00000000-0000-0000-0000-000000000001";
    private static final String BOB = "00000000-0000-0000-0000-000000000002";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static List<String> duelsBefore;

    @BeforeAll
    static void migrateSeedAndUpgrade() throws SQLException {
        flyway("27").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('gt', 'GT', TRUE)");
            st.execute("INSERT INTO player (uuid) VALUES ('" + ALICE + "'), ('" + BOB + "')");
            for (String status : List.of("ACTIVE", "REPORTED", "COMPLETED", "PENDING")) {
                st.execute("INSERT INTO duel_challenge (challenger_uuid, challenged_uuid, game_type, status, created_at, expires_at) VALUES "
                        + "('" + ALICE + "', '" + BOB + "', 'gt', '" + status + "', NOW() - INTERVAL '3 days', NOW() + INTERVAL '1 hour')");
            }
        }
        duelsBefore = rows("SELECT * FROM duel_challenge ORDER BY id");

        flyway(null).migrate();
    }

    @Test
    void anActiveDuelStartsItsClockAtTheMigration_REQ_006_03() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT status, active_since IS NOT NULL AS started, "
                     + "active_since > NOW() - INTERVAL '5 minutes' AS recent FROM duel_challenge ORDER BY id")) {
            List<String> seen = new ArrayList<>();
            while (rs.next()) {
                seen.add(rs.getString("status") + ":" + rs.getBoolean("started") + ":" + rs.getBoolean("recent"));
            }
            assertThat(seen).containsExactly("ACTIVE:true:true", "REPORTED:false:false", "COMPLETED:false:false", "PENDING:false:false");
        }
    }

    @Test
    void noOtherColumnChanges_REQ_006_03() throws SQLException {
        List<String> after = rows("SELECT id, challenger_uuid, challenged_uuid, game_type, status, reporter_uuid, score_challenger, "
                + "score_challenged, match_id, created_at, expires_at, cancel_requested_by FROM duel_challenge ORDER BY id");
        assertThat(after).hasSameSizeAs(duelsBefore);
        for (int i = 0; i < after.size(); i++) {
            assertThat(after.get(i)).isEqualTo(duelsBefore.get(i));
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
