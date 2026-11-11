package com.playerscores.service;

import com.playerscores.dto.RankedSeasonRequest;
import com.playerscores.dto.RankedSeasonResponse;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.model.RankedSeason;
import com.playerscores.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankedSeasonServiceTest {

    @Mock
    private RankedSeasonMapper rankedSeasonMapper;

    @InjectMocks
    private RankedSeasonService rankedSeasonService;

    /** REQ-001-02: a new season is always rated with OpenSkill. */
    @Test
    void createSeason_isOpenSkill_REQ_001_02() {
        when(rankedSeasonMapper.findActiveByGameType("bowspleef_1v1")).thenReturn(Optional.empty());

        RankedSeasonResponse response = rankedSeasonService.createSeason(new RankedSeasonRequest("Season 3", "bowspleef_1v1"));

        ArgumentCaptor<RankedSeason> inserted = ArgumentCaptor.forClass(RankedSeason.class);
        verify(rankedSeasonMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getRatingAlgorithm()).isEqualTo(RatingAlgorithm.OPENSKILL);
        assertThat(response.ratingAlgorithm()).isEqualTo(RatingAlgorithm.OPENSKILL);
    }

    /** REQ-001-04, REQ-001-10: deactivating keeps the season's algorithm, and responses expose it. */
    @Test
    void deactivate_keepsAlgorithm_REQ_001_04() {
        when(rankedSeasonMapper.deactivate(1L)).thenReturn(1);
        when(rankedSeasonMapper.findById(1L)).thenReturn(Optional.of(season(1L, RatingAlgorithm.CLASSIC_ELO, false)));

        RankedSeasonResponse response = rankedSeasonService.deactivateSeason(1L);

        assertThat(response.ratingAlgorithm()).isEqualTo(RatingAlgorithm.CLASSIC_ELO);
    }

    /** REQ-001-10: every season returned carries its algorithm. */
    @Test
    void getAllSeasons_exposesAlgorithm_REQ_001_10() {
        when(rankedSeasonMapper.findAll()).thenReturn(List.of(
                season(2L, RatingAlgorithm.OPENSKILL, true),
                season(1L, RatingAlgorithm.CLASSIC_ELO, false)));

        assertThat(rankedSeasonService.getAllSeasons())
                .extracting(RankedSeasonResponse::ratingAlgorithm)
                .containsExactly(RatingAlgorithm.OPENSKILL, RatingAlgorithm.CLASSIC_ELO);
    }

    private static RankedSeason season(Long id, RatingAlgorithm algorithm, boolean active) {
        RankedSeason season = new RankedSeason();
        season.setId(id);
        season.setName("Season " + id);
        season.setGameType("bowspleef_1v1");
        season.setActive(active);
        season.setRatingAlgorithm(algorithm);
        return season;
    }
}
