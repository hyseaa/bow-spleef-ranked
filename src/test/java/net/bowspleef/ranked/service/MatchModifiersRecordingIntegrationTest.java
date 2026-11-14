package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.TeamRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 010: the modifiers sent with a match are recorded, returned, and left alone by an idempotent replay. */
class MatchModifiersRecordingIntegrationTest extends AbstractIntegrationTest {

    private static final String SOURCE = "hysea-server";

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private MatchService matchService;
    @Autowired
    private JdbcTemplate jdbc;

    private String labGameType() {
        String name = "lab_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO game_types (name, display_name, ranked) VALUES (?, ?, FALSE)", name, name);
        return name;
    }

    private CreateMatchRequest request(String gameType, Map<String, Object> modifiers) {
        return new CreateMatchRequest(gameType, SOURCE,
                List.of(new TeamRequest(1, List.of(UUID.randomUUID())), new TeamRequest(0, List.of(UUID.randomUUID()))),
                null, modifiers);
    }

    @Test
    void theModifiersOfAMatchAreRecordedAndReturned_REQ_010_08() {
        String gameType = labGameType();

        MatchRecording recording = matchService.recordMatch(
                request(gameType, Map.of("no-collision", true, "perks", 20)), "key-" + UUID.randomUUID());

        assertThat(recording.match().modifiers()).isEqualTo(Map.of("no-collision", true, "perks", 20));
        assertThat(matchService.getMatch(recording.match().id()).modifiers())
                .isEqualTo(Map.of("no-collision", true, "perks", 20));
    }

    @Test
    void aGameWithNoModifierIsRecordedWithAnEmptyObject_REQ_010_08() {
        String gameType = labGameType();

        MatchRecording recording = matchService.recordMatch(request(gameType, Map.of()), "key-" + UUID.randomUUID());

        assertThat(recording.match().modifiers()).isEmpty();
    }

    @Test
    void aMatchSentWithoutTheFieldHasNoModifiers_REQ_010_09() {
        String gameType = labGameType();

        MatchRecording recording = matchService.recordMatch(request(gameType, null), "key-" + UUID.randomUUID());

        assertThat(recording.match().modifiers()).isNull();
    }

    @Test
    void aReplayIgnoresModifiersAndKeepsTheStoredOnes_REQ_010_08() {
        String gameType = labGameType();
        String key = "key-" + UUID.randomUUID();
        UUID winner = UUID.randomUUID();
        UUID loser = UUID.randomUUID();
        List<TeamRequest> teams = List.of(new TeamRequest(1, List.of(winner)), new TeamRequest(0, List.of(loser)));

        MatchRecording first = matchService.recordMatch(
                new CreateMatchRequest(gameType, SOURCE, teams, null, Map.of("perks", 20)), key);
        MatchRecording replay = matchService.recordMatch(
                new CreateMatchRequest(gameType, SOURCE, teams, null, Map.of("perks", 99)), key);

        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.match().id()).isEqualTo(first.match().id());
        assertThat(replay.match().modifiers()).isEqualTo(Map.of("perks", 20));
    }
}
