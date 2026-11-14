package net.bowspleef.ranked.service;

import net.bowspleef.ranked.exception.DiscordAlreadyLinkedException;
import net.bowspleef.ranked.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerLinkService {

    private final PlayerMapper playerMapper;

    @Transactional
    public void linkDiscord(UUID uuid, String discordId) {
        playerMapper.insertIfAbsent(uuid);
        // player.discord_id is unique: without this check a second Minecraft account for the same Discord account
        // ended in a database error (500) instead of a clear answer
        playerMapper.findByDiscordId(discordId)
                .filter(owner -> !owner.getUuid().equals(uuid))
                .ifPresent(owner -> {
                    throw new DiscordAlreadyLinkedException();
                });
        try {
            playerMapper.updateDiscordId(uuid, discordId);
        } catch (DuplicateKeyException e) {
            // two verifications of the same Discord account at the same time
            throw new DiscordAlreadyLinkedException();
        }
    }
}
