package com.playerscores.service;

import com.playerscores.client.HypixelClient;
import com.playerscores.client.MojangClient;
import com.playerscores.dto.VerifiedPlayerResponse;
import com.playerscores.dto.VerifyRequest;
import com.playerscores.exception.VerificationException;
import com.playerscores.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final PlayerMapper playerMapper;
    private final MojangClient mojangClient;
    private final HypixelClient hypixelClient;
    private final UsernameCache usernameCache;

    @Transactional
    public VerifiedPlayerResponse verify(VerifyRequest request) {
        UUID uuid = mojangClient.getUuidByUsername(request.minecraftUsername());
        if (uuid == null) {
            throw new VerificationException("Minecraft username not found: " + request.minecraftUsername());
        }

        String linkedDiscord = hypixelClient.getLinkedDiscord(uuid);
        if (linkedDiscord == null) {
            throw new VerificationException("No Discord linked on Hypixel profile.");
        }
        if (!linkedDiscord.equalsIgnoreCase(request.discordUsername())) {
            throw new VerificationException(
                    "Discord linked on Hypixel (" + linkedDiscord + ") does not match your account.");
        }

        playerMapper.insertIfAbsent(uuid);
        playerMapper.updateDiscordId(uuid, request.discordId());

        String username = usernameCache.get(uuid);
        return new VerifiedPlayerResponse(uuid, username, request.discordId());
    }

    @Transactional(readOnly = true)
    public List<VerifiedPlayerResponse> getVerifiedPlayers() {
        return playerMapper.findVerifiedPlayers().stream()
                .map(p -> new VerifiedPlayerResponse(p.getUuid(), usernameCache.get(p.getUuid()), p.getDiscordId()))
                .toList();
    }
}
