package com.playerscores.service;

import com.playerscores.AbstractIntegrationTest;
import com.playerscores.client.MatchWebhookClient;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.MatchWebhookPayload;
import com.playerscores.dto.PlayerRankEntry;
import com.playerscores.dto.TeamRequest;
import com.playerscores.mapper.EloMapper;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.GameType;
import com.playerscores.model.RankedSeason;
import com.playerscores.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * KI-06: titles are percentiles, so a ranked match changes the title of players who were not in it, and the
 * webhook must carry them. The oracle is the database itself: the titles of the season before and after.
 */
class TitleChangesIntegrationTest extends AbstractIntegrationTest {

    private static final int PLAYERS = 20;

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private MatchService matchService;
    @Autowired
    private EloMapper eloMapper;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private RankedSeasonMapper rankedSeasonMapper;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private Map<UUID, String> titles(long seasonId) {
        Map<UUID, String> titles = new HashMap<>();
        jdbc.query("SELECT player_uuid, rank_title FROM player_season_elo WHERE ranked_season_id = ?",
                rs -> {
                    titles.put(rs.getObject(1, UUID.class), rs.getString(2));
                }, seasonId);
        return titles;
    }

    @Test
    void theWebhookCarriesEveryTitleThatChanged_KI_06() {
        String name = "titles_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(name);
        gameType.setDisplayName(name);
        gameType.setRanked(true);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);
        RankedSeason season = new RankedSeason();
        season.setName(name);
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(name);
        season.setActive(true);
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL);
        rankedSeasonMapper.insert(season);

        // 20 verified players with distinct ratings and the matching percentile titles
        List<UUID> ladder = new ArrayList<>();
        Map<UUID, String> discordIds = new HashMap<>();
        for (int i = 0; i < PLAYERS; i++) {
            UUID uuid = UUID.randomUUID();
            String discordId = String.valueOf(300_000_000_000_000_000L + Math.abs(uuid.getMostSignificantBits() % 600_000_000_000_000_000L));
            playerMapper.insertIfAbsent(uuid);
            playerMapper.updateDiscordId(uuid, discordId);
            discordIds.put(uuid, discordId);
            jdbc.update("INSERT INTO player_season_elo (player_uuid, ranked_season_id, elo, matches_played, mu, sigma) "
                    + "VALUES (?, ?, ?, 5, 25.0, 8.33)", uuid, season.getId(), 1000 + i * 15);
            ladder.add(uuid);
        }
        eloMapper.updateRankTitlesBySeason(season.getId());
        Map<UUID, String> before = titles(season.getId());
        UUID lowest = ladder.get(0);
        UUID highest = ladder.get(PLAYERS - 1);

        // the lowest player beats the highest: everyone in between moves down one place
        matchService.createMatch(new CreateMatchRequest(name, "test", List.of(
                new TeamRequest(3, List.of(lowest)), new TeamRequest(1, List.of(highest))), null));

        Map<UUID, String> after = titles(season.getId());
        Set<String> expected = new HashSet<>();
        expected.add(discordIds.get(lowest));
        expected.add(discordIds.get(highest));
        after.forEach((uuid, title) -> {
            if (!title.equals(before.get(uuid))) {
                expected.add(discordIds.get(uuid));
            }
        });
        assertThat(expected.size()).as("the scenario really changes the title of bystanders").isGreaterThan(2);

        ArgumentCaptor<MatchWebhookPayload> payload = ArgumentCaptor.forClass(MatchWebhookPayload.class);
        verify(matchWebhookClient).notifyMatchResult(payload.capture());
        assertThat(payload.getValue().getPlayerRanks()).extracting(PlayerRankEntry::discordId)
                .containsExactlyInAnyOrderElementsOf(expected);
        payload.getValue().getPlayerRanks().forEach(entry -> {
            UUID uuid = discordIds.entrySet().stream().filter(e -> e.getValue().equals(entry.discordId())).findFirst().orElseThrow().getKey();
            assertThat(entry.title()).isEqualTo(after.get(uuid));
        });
    }
}
