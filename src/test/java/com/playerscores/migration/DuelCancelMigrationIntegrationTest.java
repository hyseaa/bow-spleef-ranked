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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-003-01: V27 adds duel_challenge.cancel_requested_by without touching an existing duel, and the
 * column refers to a player. Runs Flyway by hand to stop at V26, seed data, then apply V27.
 */
@Testcontainers
class DuelCancelMigrationIntegrationTest {

    private static final String ALICE = "00000000-0000-0000-0000-000000000001";
    private static final String BOB = "00000000-0000-0000-0000-000000000002";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static List<String> duelsBefore;

    @BeforeAll
    static void migrateSeedAndUpgrade() throws SQLException {
        flyway("26").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('gt', 'GT', TRUE)");
            st.execute("INSERT INTO player (uuid) VALUES ('" + ALICE + "'), ('" + BOB + "')");
            st.execute("INSERT INTO duel_challenge (challenger_uuid, challenged_uuid, game_type, status, expires_at) VALUES "
                    + "('" + ALICE + "', '" + BOB + "', 'gt', 'ACTIVE', NOW() + INTERVAL '1 hour'),"
                    + "('" + ALICE + "', '" + BOB + "', 'gt', 'COMPLETED', NOW() + INTERVAL '1 hour')");
        }
        duelsBefore = rows("SELECT * FROM duel_challenge ORDER BY id");

        flyway(null).migrate();
    }

    @Test
    void existingDuelsAreUnchangedAndHaveNoRequest_REQ_003_01() throws SQLException {
        List<String> after = rows("SELECT * FROM duel_challenge ORDER BY id");
        assertThat(after).hasSameSizeAs(duelsBefore);
        for (int i = 0; i < after.size(); i++) {
            assertThat(after.get(i)).startsWith(duelsBefore.get(i));
            assertThat(after.get(i)).contains("cancel_requested_by=null;");
        }
    }

    @Test
    void aRequestRefersToAKnownPlayer_REQ_003_01() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("UPDATE duel_challenge SET cancel_requested_by = '" + BOB + "' WHERE id = 1");
            assertThatThrownBy(() -> st.execute(
                    "UPDATE duel_challenge SET cancel_requested_by = '00000000-0000-0000-0000-0000000000ff' WHERE id = 2"))
                    .isInstanceOf(SQLException.class);
            try (ResultSet rs = st.executeQuery("SELECT cancel_requested_by FROM duel_challenge WHERE id = 1")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo(BOB);
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
