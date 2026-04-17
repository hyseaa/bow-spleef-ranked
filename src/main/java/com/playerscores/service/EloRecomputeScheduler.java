package com.playerscores.service;

import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.RankedSeason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EloRecomputeScheduler {

    private final RankedSeasonMapper rankedSeasonMapper;
    private final EloRecomputeService eloRecomputeService;

    @Scheduled(fixedDelayString = "${elo.recompute-delay-ms:5000}")
    public void processDirtySeasons() {
        List<RankedSeason> dirty = rankedSeasonMapper.findDirtySeasons();
        if (!dirty.isEmpty()) {
            log.info("Processing {} dirty season(s)", dirty.size());
        }
        for (RankedSeason season : dirty) {
            eloRecomputeService.recomputeSeasonElo(season.getId());
        }
    }
}
