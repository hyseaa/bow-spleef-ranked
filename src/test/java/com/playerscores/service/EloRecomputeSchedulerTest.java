package com.playerscores.service;

import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.RankedSeason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EloRecomputeSchedulerTest {

    @Mock
    private RankedSeasonMapper rankedSeasonMapper;
    @Mock
    private EloRecomputeService eloRecomputeService;

    @InjectMocks
    private EloRecomputeScheduler scheduler;

    @Test
    void processDirtySeasons_callsRecomputeForEachDirtySeason() {
        RankedSeason s1 = season(1L);
        RankedSeason s2 = season(2L);
        when(rankedSeasonMapper.findDirtySeasons()).thenReturn(List.of(s1, s2));

        scheduler.processDirtySeasons();

        verify(eloRecomputeService).recomputeSeasonElo(1L);
        verify(eloRecomputeService).recomputeSeasonElo(2L);
    }

    @Test
    void processDirtySeasons_noDirtySeasons_doesNothing() {
        when(rankedSeasonMapper.findDirtySeasons()).thenReturn(List.of());

        scheduler.processDirtySeasons();

        verify(eloRecomputeService, never()).recomputeSeasonElo(anyLong());
    }

    private RankedSeason season(Long id) {
        RankedSeason s = new RankedSeason();
        s.setId(id);
        s.setEloDirty(true);
        return s;
    }
}
