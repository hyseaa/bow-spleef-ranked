package com.playerscores.service;

import com.playerscores.dto.RankedSeasonRequest;
import com.playerscores.dto.RankedSeasonResponse;
import com.playerscores.exception.ActiveSeasonAlreadyExistsException;
import com.playerscores.exception.RankedSeasonNotFoundException;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.RankedSeason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RankedSeasonService {

    private final RankedSeasonMapper rankedSeasonMapper;

    @Transactional
    public RankedSeasonResponse createSeason(RankedSeasonRequest request) {
        if (rankedSeasonMapper.findActiveByGameType(request.gameType()).isPresent()) {
            throw new ActiveSeasonAlreadyExistsException(request.gameType());
        }

        RankedSeason season = new RankedSeason();
        season.setName(request.name());
        season.setStartsAt(OffsetDateTime.now());
        season.setGameType(request.gameType());
        season.setActive(true);
        rankedSeasonMapper.insert(season);
        return toResponse(season);
    }

    @Transactional
    public RankedSeasonResponse deactivateSeason(Long id) {
        if (rankedSeasonMapper.deactivate(id) == 0) {
            throw new RankedSeasonNotFoundException(id);
        }
        return rankedSeasonMapper.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RankedSeasonNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public RankedSeasonResponse getSeason(Long id) {
        return rankedSeasonMapper.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RankedSeasonNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<RankedSeasonResponse> getAllSeasons() {
        return rankedSeasonMapper.findAll().stream()
                .map(this::toResponse)
                .toList();
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
