package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateMatchRequest;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.dto.SourceRequest;
import net.bowspleef.ranked.dto.SourceResponse;
import net.bowspleef.ranked.dto.TeamRequest;
import net.bowspleef.ranked.exception.SourceAlreadyExistsException;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.model.GameType;
import net.bowspleef.ranked.model.SourceKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 033 against a real PostgreSQL: the sources registered by the migration and through the service, and the name
 * and kind of the source of a match, including a source that is not registered (still recorded).
 */
class SourceIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private SourceService sourceService;
    @Autowired
    private MatchService matchService;
    @Autowired
    private GameTypeMapper gameTypeMapper;

    private String classicGameType() {
        String name = "src_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(false);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
        return name;
    }

    private MatchResponse record(String source) {
        CreateMatchRequest request = new CreateMatchRequest(classicGameType(), source, List.of(
                new TeamRequest(1, List.of(UUID.randomUUID())),
                new TeamRequest(0, List.of(UUID.randomUUID()))), null);
        return matchService.getMatch(matchService.createMatch(request).id());
    }

    @Test
    void theMigrationRegistersTheBotAndTheTwoCustomServers_REQ_033_02() {
        assertThat(sourceService.getAll()).contains(
                new SourceResponse("discord_bot", "Discord bot", SourceKind.BOT),
                new SourceResponse("hysea-server", "Hy Sea's server", SourceKind.CUSTOM_SERVER),
                new SourceResponse("eu-test-server", "Hy Sea's EU server", SourceKind.CUSTOM_SERVER));
    }

    @Test
    void aCreatedSourceIsListedAndCannotBeCreatedTwice_REQ_033_03_REQ_033_04() {
        String name = "server-" + UUID.randomUUID().toString().substring(0, 8);
        SourceRequest request = new SourceRequest(name, "Another server", SourceKind.CUSTOM_SERVER);

        sourceService.create(request);

        assertThat(sourceService.getAll()).contains(new SourceResponse(name, "Another server", SourceKind.CUSTOM_SERVER));
        assertThatThrownBy(() -> sourceService.create(request)).isInstanceOf(SourceAlreadyExistsException.class);
    }

    @Test
    void aMatchOfARegisteredSourceCarriesItsNameAndKind_REQ_033_05() {
        MatchResponse match = record("eu-test-server");

        assertThat(match.sourceDisplayName()).isEqualTo("Hy Sea's EU server");
        assertThat(match.sourceKind()).isEqualTo("custom_server");
    }

    @Test
    void aMatchOfASourceThatIsNotRegisteredIsRecordedWithNoNameAndNoKind_REQ_033_05_REQ_033_06() {
        MatchResponse match = record("nowhere");

        assertThat(match.id()).isNotNull();
        assertThat(match.source()).isEqualTo("nowhere");
        assertThat(match.sourceDisplayName()).isNull();
        assertThat(match.sourceKind()).isNull();
    }
}
