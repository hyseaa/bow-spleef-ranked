package net.bowspleef.ranked.migration;

import org.flywaydb.core.Flyway;
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
 * REQ-011-02: V30 replaces the game type {@code bowspleef_duel_casual} by {@code bowspleef_classic} and keeps every
 * match, queue entry, duel challenge and season that pointed to it. Each scenario runs on its own database of one
 * container: Flyway stops at V29, rows are seeded, then V30 is applied.
 */
@Testcontainers
class RenameCasualMigrationIntegrationTest {

    private static final String OLD = "bowspleef_duel_casual";
    private static final String NEW = "bowspleef_classic";
    private static final String P1 = "00000000-0000-0000-0000-000000000001";
    private static final String P2 = "00000000-0000-0000-0000-000000000002";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // ---- the production case: the old type, with rows in the four tables

    @Test
    void everyReferenceFollowsTheTypeAndNoRowIsLost_REQ_011_02() throws SQLException {
        String db = database("renamed");
        flyway(db, "29").migrate();
        try (Connection c = connect(db); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('" + OLD + "', 'Bow Spleef Duel Casual', FALSE)");
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('bowspleef_duel_ranked', 'Ranked', TRUE)");
            st.execute("INSERT INTO player (uuid) VALUES ('" + P1 + "'), ('" + P2 + "')");
            st.execute("INSERT INTO ranked_season (name, game_type, active) VALUES ('old season', '" + OLD + "', FALSE)");
            st.execute("INSERT INTO ranked_season (name, game_type, active) VALUES ('ranked season', 'bowspleef_duel_ranked', TRUE)");
            st.execute("INSERT INTO match (game_type, source) VALUES ('" + OLD + "', 'discord_bot')");
            st.execute("INSERT INTO match (game_type, source) VALUES ('" + OLD + "', 'hysea-server')");
            st.execute("INSERT INTO match (game_type, source) VALUES ('bowspleef_duel_ranked', 'discord_bot')");
            st.execute("INSERT INTO queue (player_uuid, game_type, ranked_season_id, elo) VALUES ('" + P1 + "', '" + OLD + "', 2, 1000)");
            st.execute("INSERT INTO queue (player_uuid, game_type, ranked_season_id, elo) VALUES ('" + P2 + "', 'bowspleef_duel_ranked', 2, 1000)");
            st.execute("INSERT INTO duel_challenge (challenger_uuid, challenged_uuid, game_type, status, match_id, expires_at) "
                    + "VALUES ('" + P1 + "', '" + P2 + "', '" + OLD + "', 'ACTIVE', 1, NOW() + INTERVAL '1 day')");
            st.execute("INSERT INTO duel_challenge (challenger_uuid, challenged_uuid, game_type, status, expires_at) "
                    + "VALUES ('" + P2 + "', '" + P1 + "', 'bowspleef_duel_ranked', 'PENDING', NOW() + INTERVAL '1 day')");
        }

        flyway(db, null).migrate();

        assertThat(text(db, "SELECT string_agg(id || ':' || game_type, ',' ORDER BY id) FROM match"))
                .isEqualTo("1:" + NEW + ",2:" + NEW + ",3:bowspleef_duel_ranked");
        assertThat(text(db, "SELECT string_agg(player_uuid || ':' || game_type, ',' ORDER BY player_uuid) FROM queue"))
                .isEqualTo(P1 + ":" + NEW + "," + P2 + ":bowspleef_duel_ranked");
        assertThat(text(db, "SELECT string_agg(id || ':' || game_type || ':' || status, ',' ORDER BY id) FROM duel_challenge"))
                .isEqualTo("1:" + NEW + ":ACTIVE,2:bowspleef_duel_ranked:PENDING");
        assertThat(text(db, "SELECT string_agg(id || ':' || game_type, ',' ORDER BY id) FROM ranked_season"))
                .isEqualTo("1:" + NEW + ",2:bowspleef_duel_ranked");
        // the duel keeps pointing to its match
        assertThat(text(db, "SELECT match_id FROM duel_challenge WHERE id = 1")).isEqualTo("1");
    }

    @Test
    void theOldTypeIsGoneAndTheNewOneIsNotRankedWithTheSameTeamSize_REQ_011_02() throws SQLException {
        String db = database("types");
        flyway(db, "29").migrate();
        try (Connection c = connect(db); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked, team_size) VALUES ('" + OLD + "', 'Bow Spleef Duel Casual', FALSE, 1)");
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('bowspleef_duel_ranked', 'Ranked', TRUE)");
        }

        flyway(db, null).migrate();

        assertThat(text(db, "SELECT COUNT(*) FROM game_types WHERE name = '" + OLD + "'")).isEqualTo("0");
        assertThat(text(db, "SELECT display_name || '|' || ranked || '|' || team_size FROM game_types WHERE name = '" + NEW + "'"))
                .isEqualTo("Bow Spleef Classic|false|1");
        assertThat(text(db, "SELECT display_name FROM game_types WHERE name = 'bowspleef_duel_ranked'")).isEqualTo("Ranked");
    }

    // ---- a database that has nothing to rename

    @Test
    void aDatabaseWithNoOldTypeGainsNothing_REQ_011_02() throws SQLException {
        String db = database("empty");
        flyway(db, "29").migrate();
        try (Connection c = connect(db); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('bowspleef_duel_ranked', 'Ranked', TRUE)");
            st.execute("INSERT INTO match (game_type, source) VALUES ('bowspleef_duel_ranked', 'discord_bot')");
        }

        flyway(db, null).migrate();

        assertThat(text(db, "SELECT string_agg(name, ',') FROM game_types")).isEqualTo("bowspleef_duel_ranked");
        assertThat(text(db, "SELECT COUNT(*) FROM match")).isEqualTo("1");
    }

    @Test
    void aClassicTypeCreatedByAnAdminIsKeptAndTheReferencesStillMove_REQ_011_02() throws SQLException {
        String db = database("existing");
        flyway(db, "29").migrate();
        try (Connection c = connect(db); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('" + OLD + "', 'Old', FALSE)");
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('" + NEW + "', 'Made by an admin', FALSE)");
            st.execute("INSERT INTO match (game_type, source) VALUES ('" + OLD + "', 'discord_bot')");
        }

        flyway(db, null).migrate();

        assertThat(text(db, "SELECT display_name FROM game_types WHERE name = '" + NEW + "'")).isEqualTo("Made by an admin");
        assertThat(text(db, "SELECT game_type FROM match WHERE id = 1")).isEqualTo(NEW);
        assertThat(text(db, "SELECT COUNT(*) FROM game_types WHERE name = '" + OLD + "'")).isEqualTo("0");
    }

    // ---- helpers

    /** A new database of the container, so that each scenario starts from nothing. */
    private static String database(String name) throws SQLException {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + name);
        }
        return name;
    }

    private static String url(String db) {
        return postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + db);
    }

    private static Flyway flyway(String db, String target) {
        var config = Flyway.configure()
                .dataSource(url(db), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    private static Connection connect(String db) throws SQLException {
        return DriverManager.getConnection(url(db), postgres.getUsername(), postgres.getPassword());
    }

    private static String text(String db, String sql) throws SQLException {
        try (Connection c = connect(db); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
