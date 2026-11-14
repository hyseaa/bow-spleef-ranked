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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-016-01 and REQ-016-02: V31 creates player_preference, one row per player with a typed column per preference,
 * a default of 0 for the percentage of TNT removed, a check from 0 to 100 and a cascade on the player.
 */
@Testcontainers
class PlayerPreferenceMigrationIntegrationTest {

    private static final String PLAYER = "11111111-1111-1111-1111-111111111111";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void insertPlayer(Statement st, String uuid) throws SQLException {
        st.execute("INSERT INTO player (uuid) VALUES ('" + uuid + "') ON CONFLICT DO NOTHING");
    }

    @Test
    void aRowWithNoValueTakesTheDefaultOfZero_REQ_016_02() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            insertPlayer(st, PLAYER);
            st.execute("INSERT INTO player_preference (player_uuid) VALUES ('" + PLAYER + "')");
            try (ResultSet rs = st.executeQuery("SELECT tnt_removed_percent FROM player_preference WHERE player_uuid = '" + PLAYER + "'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    void zeroAndOneHundredAreAccepted_REQ_016_02() throws SQLException {
        String uuid = "22222222-2222-2222-2222-222222222222";
        try (Connection c = connect(); Statement st = c.createStatement()) {
            insertPlayer(st, uuid);
            st.execute("INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES ('" + uuid + "', 100)");
            st.execute("UPDATE player_preference SET tnt_removed_percent = 0 WHERE player_uuid = '" + uuid + "'");
        }
    }

    @Test
    void aValueOutsideZeroToOneHundredIsRefusedByTheDatabase_REQ_016_02() throws SQLException {
        String uuid = "33333333-3333-3333-3333-333333333333";
        try (Connection c = connect(); Statement st = c.createStatement()) {
            insertPlayer(st, uuid);
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES ('" + uuid + "', 101)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES ('" + uuid + "', -1)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void thereIsOneRowPerPlayer_REQ_016_01() throws SQLException {
        String uuid = "44444444-4444-4444-4444-444444444444";
        try (Connection c = connect(); Statement st = c.createStatement()) {
            insertPlayer(st, uuid);
            st.execute("INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES ('" + uuid + "', 10)");
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES ('" + uuid + "', 20)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void deletingThePlayerDeletesItsPreferences_REQ_016_01() throws SQLException {
        String uuid = "55555555-5555-5555-5555-555555555555";
        try (Connection c = connect(); Statement st = c.createStatement()) {
            insertPlayer(st, uuid);
            st.execute("INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES ('" + uuid + "', 30)");
            st.execute("DELETE FROM player WHERE uuid = '" + uuid + "'");
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM player_preference WHERE player_uuid = '" + uuid + "'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    void aPreferenceCannotExistForAnUnknownPlayer_REQ_016_01() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO player_preference (player_uuid, tnt_removed_percent) VALUES ('66666666-6666-6666-6666-666666666666', 5)"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
