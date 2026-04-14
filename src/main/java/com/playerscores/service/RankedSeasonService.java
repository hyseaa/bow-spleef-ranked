package com.playerscores.service;

import com.playerscores.dto.RankedSeasonRequest;
import com.playerscores.dto.RankedSeasonResponse;
import com.playerscores.exception.ActiveSeasonAlreadyExistsException;
import com.playerscores.exception.RankedSeasonNotFoundException;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.RankedSeason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankedSeasonService {

    private final RankedSeasonMapper rankedSeasonMapper;

    @Transactional
    public RankedSeasonResponse createSeason(RankedSeasonRequest request) {
        log.info("Creating ranked season: name={}, gameType={}", request.name(), request.gameType());
        if (rankedSeasonMapper.findActiveByGameType(request.gameType()).isPresent()) {
            log.warn("Cannot create season: active season already exists for gameType={}", request.gameType());
            throw new ActiveSeasonAlreadyExistsException(request.gameType());
        }

        RankedSeason season = new RankedSeason();
        season.setName(request.name());
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(request.gameType());
        season.setActive(true);
        rankedSeasonMapper.insert(season);
        log.info("Ranked season created: id={}, name={}, gameType={}", season.getId(), season.getName(), season.getGameType());
        return toResponse(season);
    }

    @Transactional
    public RankedSeasonResponse deactivateSeason(Long id) {
        log.info("Deactivating ranked season: id={}", id);
        if (rankedSeasonMapper.deactivate(id) == 0) {
            log.warn("Cannot deactivate season: not found for id={}", id);
            throw new RankedSeasonNotFoundException(id);
        }
        RankedSeasonResponse response = rankedSeasonMapper.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RankedSeasonNotFoundException(id));
        log.info("Ranked season deactivated: id={}", id);
        return response;
    }

    @Transactional(readOnly = true)
    public RankedSeasonResponse getSeason(Long id) {
        log.debug("Fetching ranked season: id={}", id);
        return rankedSeasonMapper.findById(id)
                .map(s -> {
                    log.debug("Found ranked season: id={}, name={}", id, s.getName());
                    return toResponse(s);
                })
                .orElseThrow(() -> {
                    log.warn("Ranked season not found: id={}", id);
                    return new RankedSeasonNotFoundException(id);
                });
    }

    @Transactional(readOnly = true)
    public List<RankedSeasonResponse> getAllSeasons() {
        log.debug("Fetching all ranked seasons");
        List<RankedSeasonResponse> result = rankedSeasonMapper.findAll().stream()
                .map(this::toResponse)
                .toList();
        log.debug("Found {} ranked season(s)", result.size());
        return result;
    }

    private RankedSeasonResponse toResponse(RankedSeason season) {
        return new RankedSeasonResponse(
                season.getId(),
                season.getName(),
                season.getStartsAt(),
                season.getEndsAt(),
                season.getGameType(),
                season.isActive()
        );
    }
}
