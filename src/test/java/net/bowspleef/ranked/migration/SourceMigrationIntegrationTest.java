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
 * REQ-033-01 and REQ-033-02: V33 creates the table of the sources (name, display name, kind among bot and
 * custom_server) and registers the bot and the two custom servers; match.source keeps no foreign key to it.
 */
@Testcontainers
class SourceMigrationIntegrationTest {

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

    @Test
    void theBotAndTheTwoCustomServersAreRegistered_REQ_033_02() throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, display_name, kind FROM source ORDER BY name")) {
            while (rs.next()) {
                rows.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
            }
        }
        assertThat(rows).containsExactly(
                "discord_bot|Discord bot|bot",
                "eu-test-server|Hy Sea's EU server|custom_server",
                "hysea-server|Hy Sea's server|custom_server");
    }

    @Test
    void aKindOtherThanBotAndCustomServerIsRefused_REQ_033_01() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO source (name, display_name, kind) VALUES ('x', 'X', 'hypixel')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void matchSourceHasNoForeignKeyToTheTable_REQ_033_01() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM information_schema.referential_constraints rc "
                     + "JOIN information_schema.key_column_usage k ON k.constraint_name = rc.constraint_name "
                     + "WHERE k.table_name = 'match' AND k.column_name = 'source'")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }
}
