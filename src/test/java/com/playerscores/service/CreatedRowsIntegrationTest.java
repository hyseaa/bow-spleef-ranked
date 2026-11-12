package com.playerscores.service;

import com.playerscores.AbstractIntegrationTest;
import com.playerscores.dto.CreateDuelChallengeRequest;
import com.playerscores.dto.CreatePartyRequest;
import com.playerscores.dto.DuelChallengeResponse;
import com.playerscores.dto.PartyResponse;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.GameType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** KI-23: a freshly created duel challenge or party comes back with the values the database filled in. */
class CreatedRowsIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;

    @Autowired
    private DuelChallengeService duelChallengeService;
    @Autowired
    private PartyService partyService;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private GameTypeMapper gameTypeMapper;

    /** A verified player with a unique snowflake-like Discord ID (player.discord_id is VARCHAR(20)). */
    private String verifiedPlayer() {
        String discordId = String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
        UUID uuid = UUID.randomUUID();
        playerMapper.insertIfAbsent(uuid);
        playerMapper.updateDiscordId(uuid, discordId);
        return discordId;
    }

    @Test
    void aNewDuelChallengeComesBackWithItsStatusAndCreationDate_KI_23() {
        String gameTypeName = "CREATED_" + UUID.randomUUID().toString().substring(0, 8);
        GameType gameType = new GameType();
        gameType.setName(gameTypeName);
        gameType.setDisplayName(gameTypeName);
        gameType.setRanked(false);
        gameType.setTeamSize(1);
        gameTypeMapper.insert(gameType);

        DuelChallengeResponse response = duelChallengeService.create(
                new CreateDuelChallengeRequest(verifiedPlayer(), verifiedPlayer(), gameTypeName));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.expiresAt()).isNotNull();
        assertThat(duelChallengeService.getChallenge(response.id()).createdAt()).isEqualTo(response.createdAt());
    }

    @Test
    void aNewPartyComesBackWithItsCreationDate_KI_23() {
        PartyResponse response = partyService.create(new CreatePartyRequest(verifiedPlayer()));

        assertThat(response.createdAt()).isNotNull();
        assertThat(partyService.getParty(response.id()).createdAt()).isEqualTo(response.createdAt());
    }
}
