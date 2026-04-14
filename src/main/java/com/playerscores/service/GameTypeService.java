package com.playerscores.service;

import com.playerscores.dto.GameTypeRequest;
import com.playerscores.dto.GameTypeResponse;
import com.playerscores.exception.GameTypeNotFoundException;
import com.playerscores.mapper.GameTypeMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.GameType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
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
        log.info("Creating game type: name={}, displayName={}, ranked={}", request.name(), request.displayName(), request.ranked());
        GameType gt = new GameType();
        gt.setName(request.name());
        gt.setDisplayName(request.displayName());
        gt.setRanked(request.ranked());
        gameTypeMapper.insert(gt);
        log.info("Game type created: name={}", gt.getName());
        return toResponse(gt);
    }

    @Transactional(readOnly = true)
    public GameTypeResponse getByName(String name) {
        log.debug("Fetching game type by name={}", name);
        return gameTypeMapper.findByName(name)
                .map(gt -> {
                    log.debug("Found game type: name={}", name);
                    return toResponse(gt);
                })
                .orElseThrow(() -> {
                    log.warn("Game type not found: name={}", name);
                    return new GameTypeNotFoundException(name);
                });
    }

    @Transactional(readOnly = true)
    public List<GameTypeResponse> getAll() {
        log.debug("Fetching all game types");
        List<GameTypeResponse> result = gameTypeMapper.findAll().stream().map(this::toResponse).toList();
        log.debug("Found {} game type(s)", result.size());
        return result;
    }

    private GameTypeResponse toResponse(GameType gt) {
        boolean active = !gt.isRanked() || rankedSeasonMapper.findActiveByGameType(gt.getName()).isPresent();
        return new GameTypeResponse(gt.getName(), gt.getDisplayName(), gt.isRanked(), active);
    }
}
