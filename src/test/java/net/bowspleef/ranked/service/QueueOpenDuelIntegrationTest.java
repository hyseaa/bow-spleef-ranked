package net.bowspleef.ranked.service;

import net.bowspleef.ranked.AbstractIntegrationTest;
import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.CreateDuelChallengeRequest;
import net.bowspleef.ranked.dto.QueueResponse;
import net.bowspleef.ranked.mapper.GameTypeMapper;
import net.bowspleef.ranked.mapper.PlayerMapper;
import net.bowspleef.ranked.mapper.RankedSeasonMapper;
import net.bowspleef.ranked.model.GameType;
import net.bowspleef.ranked.model.RankedSeason;
import net.bowspleef.ranked.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two players who already have an open duel of the game type must not be paired by the queue: the pairing removes
 * both from the queue and the duel could then not be created (one open duel per pair and game type).
 */
class QueueOpenDuelIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;
    @MockBean
    private MatchWebhookClient matchWebhookClient;

    @Autowired
    private QueueService queueService;
    @Autowired
    private DuelChallengeService duelChallengeService;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private GameTypeMapper gameTypeMapper;
    @Autowired
    private RankedSeasonMapper rankedSeasonMapper;

    private String gameType;

    private String player() {
        String id = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
        UUID uuid = UUID.randomUUID();
        playerMapper.insertIfAbsent(uuid);
        playerMapper.updateDiscordId(uuid, id);
        return id;
    }

    private void rankedGameTypeWithASeason() {
        gameType = "queue_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gt = new GameType();
        gt.setName(gameType);
        gt.setDisplayName(gameType);
        gt.setRanked(true);
        gt.setTeamSize(1);
        gameTypeMapper.insert(gt);
        RankedSeason season = new RankedSeason();
        season.setName(gameType);
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(gameType);
        season.setActive(true);
        season.setRatingAlgorithm(RatingAlgorithm.OPENSKILL);
        rankedSeasonMapper.insert(season);
    }

    @Test
    void playersWithAnOpenDuelAreNotPairedByTheQueue() {
        rankedGameTypeWithASeason();
        String a = player();
        String b = player();
        String c = player();
        duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType));

        QueueResponse first = queueService.join(a, gameType);
        QueueResponse second = queueService.join(b, gameType);

        assertThat(first.matched()).isFalse();
        assertThat(second.matched()).as("a and b already have an open duel").isFalse();

        // someone else is paired with the waiting player as usual
        QueueResponse third = queueService.join(c, gameType);
        assertThat(third.matched()).isTrue();
    }

    @Test
    void afterTheDuelIsCancelledTheSamePairIsPaired() {
        rankedGameTypeWithASeason();
        String a = player();
        String b = player();
        long duel = duelChallengeService.create(new CreateDuelChallengeRequest(a, b, gameType)).id();
        queueService.join(a, gameType);
        assertThat(queueService.join(b, gameType).matched()).isFalse();

        duelChallengeService.cancel(duel, a);

        assertThat(queueService.join(b, gameType).matched()).isTrue();
    }
}
