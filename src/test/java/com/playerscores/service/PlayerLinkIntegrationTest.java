package com.playerscores.service;

import com.playerscores.AbstractIntegrationTest;
import com.playerscores.exception.DiscordAlreadyLinkedException;
import com.playerscores.mapper.PlayerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A Discord account is linked to one Minecraft account (player.discord_id is unique). */
class PlayerLinkIntegrationTest extends AbstractIntegrationTest {

    @MockBean
    private UsernameCache usernameCache;

    @Autowired
    private PlayerLinkService playerLinkService;
    @Autowired
    private PlayerMapper playerMapper;

    private static String discordId() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000_000_000L, 999_999_999_999_999_999L));
    }

    @Test
    void linkingTheSameAccountAgainIsHarmless() {
        UUID uuid = UUID.randomUUID();
        String discord = discordId();

        playerLinkService.linkDiscord(uuid, discord);
        playerLinkService.linkDiscord(uuid, discord);

        assertThat(playerMapper.findByDiscordId(discord)).get().extracting(p -> p.getUuid()).isEqualTo(uuid);
    }

    @Test
    void aDiscordAccountAlreadyLinkedToAnotherMinecraftAccountIsRefusedWithAClearError() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String discord = discordId();
        playerLinkService.linkDiscord(first, discord);

        assertThatThrownBy(() -> playerLinkService.linkDiscord(second, discord))
                .isInstanceOf(DiscordAlreadyLinkedException.class);

        assertThat(playerMapper.findByDiscordId(discord)).get().extracting(p -> p.getUuid()).as("the first link is untouched").isEqualTo(first);
    }

    @Test
    void aMinecraftAccountCanMoveToTheDiscordAccountHypixelNamesNow() {
        UUID uuid = UUID.randomUUID();
        String before = discordId();
        String after = discordId();
        playerLinkService.linkDiscord(uuid, before);

        playerLinkService.linkDiscord(uuid, after);

        assertThat(playerMapper.findByDiscordId(after)).isPresent();
        assertThat(playerMapper.findByDiscordId(before)).isEmpty();
    }
}
