package com.playerscores.service;

import com.playerscores.client.HypixelClient;
import com.playerscores.client.MojangClient;
import com.playerscores.dto.VerifiedPlayerResponse;
import com.playerscores.dto.VerifyRequest;
import com.playerscores.exception.DiscordMismatchException;
import com.playerscores.exception.MojangUsernameNotFoundException;
import com.playerscores.exception.VerificationException;
import com.playerscores.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final PlayerMapper playerMapper;
    private final MojangClient mojangClient;
    private final HypixelClient hypixelClient;
    private final UsernameCache usernameCache;

    @Transactional
    public VerifiedPlayerResponse verify(VerifyRequest request) {
        log.info("Starting verification for minecraftUsername={}, discordId={}", request.minecraftUsername(), request.discordId());

        UUID uuid = mojangClient.getUuidByUsername(request.minecraftUsername());
        if (uuid == null) {
            log.warn("Verification failed: Mojang username not found for minecraftUsername={}", request.minecraftUsername());
            throw new MojangUsernameNotFoundException(request.minecraftUsername());
        }
        log.debug("Resolved uuid={} for minecraftUsername={}", uuid, request.minecraftUsername());

        String linkedDiscord = hypixelClient.getLinkedDiscord(uuid);
        if (linkedDiscord == null) {
            log.warn("Verification failed: no Discord linked on Hypixel for uuid={}", uuid);
            throw new VerificationException("No Discord linked on Hypixel profile.");
        }
        if (!linkedDiscord.equalsIgnoreCase(request.discordUsername())) {
            log.warn("Verification failed: Discord mismatch for uuid={} — expected={}, found={}", uuid, request.discordUsername(), linkedDiscord);
            throw new DiscordMismatchException(linkedDiscord);
        }

        playerMapper.insertIfAbsent(uuid);
        playerMapper.updateDiscordId(uuid, request.discordId());
        log.info("Verification successful: uuid={} linked to discordId={}", uuid, request.discordId());

        String username = usernameCache.get(uuid);
        return new VerifiedPlayerResponse(uuid, username, request.discordId());
    }

    @Transactional(readOnly = true)
    public List<VerifiedPlayerResponse> getVerifiedPlayers() {
        log.debug("Fetching all verified players");
        List<VerifiedPlayerResponse> result = playerMapper.findVerifiedPlayers().stream()
                .map(p -> new VerifiedPlayerResponse(p.getUuid(), usernameCache.get(p.getUuid()), p.getDiscordId()))
                .toList();
        log.debug("Found {} verified player(s)", result.size());
        return result;
    }
}
