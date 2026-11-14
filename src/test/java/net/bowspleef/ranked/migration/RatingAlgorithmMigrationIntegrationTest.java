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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-001-01, REQ-001-03: V25 adds ranked_season.rating_algorithm and tags existing seasons from
 * their data (Classic Elo when every rating still has the default mu/sigma), without touching any
 * rating or history row. Runs Flyway by hand to stop at V24, seed data, then apply V25.
 */
@Testcontainers
class RatingAlgorithmMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static List<String> ratingsBefore;
    private static List<String> historyBefore;

    @BeforeAll
    static void migrateSeedAndUpgrade() throws SQLException {
        flyway("24").migrate();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO game_types (name, display_name, ranked) VALUES ('gt', 'GT', TRUE)");
            for (int i = 1; i <= 4; i++) {
                st.execute("INSERT INTO player (uuid) VALUES ('00000000-0000-0000-0000-00000000000" + i + "')");
            }
            // 1: classic (default mu/sigma), 2: OpenSkill, 3: mixed, 4: no rating at all
            for (String name : List.of("classic", "openskill", "mixed", "empty")) {
                st.execute("INSERT INTO ranked_season (name, game_type, active) VALUES ('" + name + "', 'gt', FALSE)");
            }
            st.execute("INSERT INTO player_season_elo (player_uuid, ranked_season_id, elo, matches_played, rank_title) VALUES "
                    + "('00000000-0000-0000-0000-000000000001', 1, 1020, 1, 'Gold I'),"
                    + "('00000000-0000-0000-0000-000000000002', 1, 980, 1, 'Bronze I')");
            st.execute("INSERT INTO player_season_elo (player_uuid, ranked_season_id, elo, matches_played, mu, sigma) VALUES "
                    + "('00000000-0000-0000-0000-000000000001', 2, 1034, 1, 27.63523138347365, 8.065506316323548),"
                    + "('00000000-0000-0000-0000-000000000002', 2, 982, 1, 22.36476861652635, 8.065506316323548)");
            st.execute("INSERT INTO player_season_elo (player_uuid, ranked_season_id, elo, matches_played) VALUES "
                    + "('00000000-0000-0000-0000-000000000003', 3, 1000, 0)");
            st.execute("INSERT INTO player_season_elo (player_uuid, ranked_season_id, elo, matches_played, mu, sigma) VALUES "
                    + "('00000000-0000-0000-0000-000000000004', 3, 1012, 1, 26.1, 8.2)");
            st.execute("INSERT INTO match (game_type, source, ranked_season_id) VALUES ('gt', 'test', 1)");
            st.execute("INSERT INTO elo_history (player_uuid, match_id, ranked_season_id, elo_before, elo_after, elo_change) VALUES "
                    + "('00000000-0000-0000-0000-000000000001', 1, 1, 1000, 1020, 20),"
                    + "('00000000-0000-0000-0000-000000000002', 1, 1, 1000, 980, -20)");
        }
        ratingsBefore = rows("SELECT * FROM player_season_elo ORDER BY ranked_season_id, player_uuid");
        historyBefore = rows("SELECT * FROM elo_history ORDER BY id");

        flyway(null).migrate();
    }

    @Test
    void existingSeasonsAreTaggedFromTheirRatings_REQ_001_03() throws SQLException {
        Map<String, String> tags = new LinkedHashMap<>();
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, rating_algorithm FROM ranked_season ORDER BY id")) {
            while (rs.next()) {
                tags.put(rs.getString(1), rs.getString(2));
            }
        }
        assertThat(tags).containsExactly(
                Map.entry("classic", "CLASSIC_ELO"),
                Map.entry("openskill", "OPENSKILL"),
                Map.entry("mixed", "OPENSKILL"),
                Map.entry("empty", "OPENSKILL"));
    }

    @Test
    void ratingsAndHistoryAreUnchanged_REQ_001_03() throws SQLException {
        assertThat(rows("SELECT * FROM player_season_elo ORDER BY ranked_season_id, player_uuid")).isEqualTo(ratingsBefore);
        assertThat(rows("SELECT * FROM elo_history ORDER BY id")).isEqualTo(historyBefore);
    }

    @Test
    void everySeasonHasAnAlgorithmAndOnlyKnownValuesAreAccepted_REQ_001_01() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO ranked_season (name, game_type, active) VALUES ('new', 'gt', FALSE)");
            try (ResultSet rs = st.executeQuery("SELECT rating_algorithm FROM ranked_season WHERE name = 'new'")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("OPENSKILL");
            }
            assertThatThrownBy(() -> st.execute("UPDATE ranked_season SET rating_algorithm = 'TRUESKILL' WHERE name = 'new'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> st.execute("UPDATE ranked_season SET rating_algorithm = NULL WHERE name = 'new'"))
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
