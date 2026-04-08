package com.playerscores.service;

import com.playerscores.dto.PlayerRequest;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerMapper playerMapper;

    @Transactional
    public PlayerResponse upsertPlayer(UUID uuid, PlayerRequest request) {
        Player player = new Player();
        player.setUuid(uuid);
        player.setUsername(request.username());
        playerMapper.upsert(player);
        return playerMapper.findByUuid(uuid).map(this::toResponse).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PlayerResponse getPlayer(UUID uuid) {
        return playerMapper.findByUuid(uuid)
                .map(this::toResponse)
                .orElseThrow(() -> new PlayerNotFoundException(uuid));
    }

    private PlayerResponse toResponse(Player player) {
        return new PlayerResponse(player.getUuid(), player.getUsername());
    }
}
