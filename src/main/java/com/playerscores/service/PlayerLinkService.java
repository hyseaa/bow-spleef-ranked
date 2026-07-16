package com.playerscores.service;

import com.playerscores.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
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
        playerMapper.updateDiscordId(uuid, discordId);
    }
}
