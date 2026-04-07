package com.playerscores.service;

import com.playerscores.dto.PlayerRequest;
import com.playerscores.dto.PlayerResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.model.Player;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerMapper playerMapper;

    @Transactional
    public PlayerResponse createPlayer(PlayerRequest request) {
        Player player = new Player();
        player.setUsername(request.getUsername());
        playerMapper.insert(player);
        return toResponse(player);
    }

    public PlayerResponse getPlayer(Long id) {
        return playerMapper.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new PlayerNotFoundException(id));
    }

    private PlayerResponse toResponse(Player player) {
        PlayerResponse response = new PlayerResponse();
        response.setId(player.getId());
        response.setUsername(player.getUsername());
        response.setCreatedAt(player.getCreatedAt());
        return response;
    }
}
