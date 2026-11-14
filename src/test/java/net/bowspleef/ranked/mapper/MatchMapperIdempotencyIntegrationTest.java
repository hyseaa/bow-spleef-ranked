package net.bowspleef.ranked.mapper;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.model.Match;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** REQ-002-01, REQ-002-07: the key is stored with the match, found per source, and its lock query runs. */
@Transactional
class MatchMapperIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MatchMapper matchMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storedKeyIsFoundBySourceAndKey_REQ_002_01() {
        jdbc.update("INSERT INTO game_types (name, display_name, ranked) VALUES ('idem_gt', 'Idem', FALSE)");
        Match match = new Match();
        match.setGameType("idem_gt");
        match.setSource("src-a");
        match.setIdempotencyKey("key-1");
        matchMapper.insert(match);

        assertThat(matchMapper.findByIdempotencyKey("src-a", "key-1")).get()
                .satisfies(found -> {
                    assertThat(found.getId()).isEqualTo(match.getId());
                    assertThat(found.getIdempotencyKey()).isEqualTo("key-1");
                });
        assertThat(matchMapper.findByIdempotencyKey("src-b", "key-1")).isEmpty();
        assertThat(matchMapper.findByIdempotencyKey("src-a", "key-2")).isEmpty();
    }

    @Test
    void lockQueryRunsInATransaction_REQ_002_01() {
        matchMapper.lockIdempotencyKey("src-a", "key-lock");
        // the lock is re-entrant for the same transaction
        matchMapper.lockIdempotencyKey("src-a", "key-lock");
    }
}
