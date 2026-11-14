package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.dto.PreferencesResponse;
import net.bowspleef.ranked.dto.UpdatePreferencesRequest;
import net.bowspleef.ranked.exception.InvalidPreferenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 016, ADR-0023: the preferences kept in PostgreSQL, through the real mappers. */
@Transactional
class PlayerPreferenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlayerPreferenceService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aPlayerTheApiNeverSawHasTheDefaultsAndNoError_REQ_016_09() {
        assertThat(service.get(UUID.randomUUID())).isEqualTo(new PreferencesResponse(0));
    }

    @Test
    void aWrittenValueIsReadBack_REQ_016_01() {
        UUID uuid = UUID.randomUUID();

        service.update(uuid, new UpdatePreferencesRequest(40));

        assertThat(service.get(uuid)).isEqualTo(new PreferencesResponse(40));
    }

    @Test
    void aWriteForAnUnknownPlayerCreatesItWithNoUsername_REQ_016_09() {
        UUID uuid = UUID.randomUUID();

        service.update(uuid, new UpdatePreferencesRequest(25));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player WHERE uuid = ?", Integer.class, uuid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT username FROM player WHERE uuid = ?", String.class, uuid)).isNull();
    }

    @Test
    void aSecondWriteReplacesTheFirstAndKeepsOneRow_REQ_016_01() {
        UUID uuid = UUID.randomUUID();

        service.update(uuid, new UpdatePreferencesRequest(10));
        service.update(uuid, new UpdatePreferencesRequest(90));

        assertThat(service.get(uuid)).isEqualTo(new PreferencesResponse(90));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_preference WHERE player_uuid = ?", Integer.class, uuid))
                .isEqualTo(1);
    }

    @Test
    void aPlayerThatAlreadyExistsKeepsItsDataWhenItGetsAPreference_REQ_016_09() {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO player (uuid, username) VALUES (?, 'Notch')", uuid);

        service.update(uuid, new UpdatePreferencesRequest(70));

        assertThat(jdbc.queryForObject("SELECT username FROM player WHERE uuid = ?", String.class, uuid)).isEqualTo("Notch");
    }

    @Test
    void aValueOutsideTheRangeStoresNothing_REQ_016_02() {
        UUID uuid = UUID.randomUUID();

        assertThatThrownBy(() -> service.update(uuid, new UpdatePreferencesRequest(101)))
                .isInstanceOf(InvalidPreferenceException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player WHERE uuid = ?", Integer.class, uuid)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_preference WHERE player_uuid = ?", Integer.class, uuid))
                .isZero();
    }
}
