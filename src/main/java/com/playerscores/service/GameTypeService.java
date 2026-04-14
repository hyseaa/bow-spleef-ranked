package com.playerscores.service;

import com.playerscores.dto.GameTypeRequest;
import com.playerscores.dto.GameTypeResponse;
import com.playerscores.exception.GameTypeNotFoundException;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.GameType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GameTypeService {

    private final GameTypeMapper gameTypeMapper;
    private final RankedSeasonMapper rankedSeasonMapper;

    public GameTypeService(GameTypeMapper gameTypeMapper, RankedSeasonMapper rankedSeasonMapper) {
        this.gameTypeMapper = gameTypeMapper;
        this.rankedSeasonMapper = rankedSeasonMapper;
    }

    @Transactional
    public GameTypeResponse create(GameTypeRequest request) {
        GameType gt = new GameType();
        gt.setName(request.name());
        gt.setDisplayName(request.displayName());
        gt.setRanked(request.ranked());
        gameTypeMapper.insert(gt);
        return toResponse(gt);
    }

    @Transactional(readOnly = true)
    public GameTypeResponse getByName(String name) {
        return gameTypeMapper.findByName(name)
                .map(this::toResponse)
                .orElseThrow(() -> new GameTypeNotFoundException(name));
    }

    @Transactional(readOnly = true)
    public List<GameTypeResponse> getAll() {
        return gameTypeMapper.findAll().stream().map(this::toResponse).toList();
    }

    private GameTypeResponse toResponse(GameType gt) {
        boolean active = !gt.isRanked() || rankedSeasonMapper.findActiveByGameType(gt.getName()).isPresent();
        return new GameTypeResponse(gt.getName(), gt.getDisplayName(), gt.isRanked(), active);
    }
}
