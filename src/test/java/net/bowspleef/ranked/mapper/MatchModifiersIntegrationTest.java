package net.bowspleef.ranked.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.dto.MatchResponse;
import net.bowspleef.ranked.model.Match;
import net.bowspleef.ranked.service.MatchService;
import net.bowspleef.ranked.service.UsernameCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 010 (ADR-0021): the modifiers of a match are stored as JSONB, read back, and absent when none were sent. */
@Transactional
class MatchModifiersIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;

    @Autowired
    private MatchMapper matchMapper;
    @Autowired
    private MatchService matchService;
    @Autowired
    private JdbcTemplate jdbc;

    private Match insert(String modifiersJson) {
        jdbc.update("INSERT INTO game_types (name, display_name, ranked) VALUES ('mod_gt', 'Mod', FALSE) "
                + "ON CONFLICT DO NOTHING");
        Match match = new Match();
        match.setGameType("mod_gt");
        match.setSource("hysea-server");
        match.setModifiers(modifiersJson);
        matchMapper.insert(match);
        return match;
    }

    @Test
    void modifiersAreStoredAndReadBackAsTheSameObject_REQ_010_08() throws Exception {
        Match match = insert("{\"no-collision\":true,\"perks\":20}");

        Match found = matchMapper.findById(match.getId()).orElseThrow();

        assertThat(new ObjectMapper().readTree(found.getModifiers()))
                .isEqualTo(new ObjectMapper().readTree("{\"perks\":20,\"no-collision\":true}"));
    }

    @Test
    void anEmptyObjectIsKeptAsAnEmptyObject_REQ_010_08() {
        Match match = insert("{}");

        assertThat(matchMapper.findById(match.getId()).orElseThrow().getModifiers()).isEqualTo("{}");
    }

    @Test
    void aMatchWithNoModifiersHasNone_REQ_010_09() {
        Match match = insert(null);

        assertThat(matchMapper.findById(match.getId()).orElseThrow().getModifiers()).isNull();
    }

    @Test
    void theMatchResponseReturnsTheModifiersAsAMap_REQ_010_08() {
        Match match = insert("{\"no-collision\":true,\"perks\":20}");

        MatchResponse response = matchService.getMatch(match.getId());

        assertThat(response.modifiers()).isEqualTo(Map.of("no-collision", true, "perks", 20));
    }

    @Test
    void theMatchResponseHasNullModifiersWhenNoneWereSent_REQ_010_09() {
        Match match = insert(null);

        assertThat(matchService.getMatch(match.getId()).modifiers()).isNull();
    }
}
