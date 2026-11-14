package net.bowspleef.ranked.service;

import net.bowspleef.ranked.client.MatchWebhookClient;
import net.bowspleef.ranked.dto.RankedSeasonRequest;
import net.bowspleef.ranked.dto.RankedSeasonResponse;
import net.bowspleef.ranked.dto.SeasonDeactivatedPayload;
import net.bowspleef.ranked.exception.RankedSeasonNotFoundException;
import net.bowspleef.ranked.mapper.RankedSeasonMapper;
import net.bowspleef.ranked.model.RankedSeason;
import net.bowspleef.ranked.model.RatingAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankedSeasonServiceTest {

    @Mock
    private RankedSeasonMapper rankedSeasonMapper;
    @Mock
    private MatchWebhookClient matchWebhookClient;

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

    /** REQ-005-04: deactivating an active season tells the bot, once, with the season's game type. */
    @Test
    void deactivate_anActiveSeason_tellsTheBotOnce_REQ_005_04() {
        when(rankedSeasonMapper.findById(1L)).thenReturn(
                Optional.of(season(1L, RatingAlgorithm.OPENSKILL, true)),
                Optional.of(season(1L, RatingAlgorithm.OPENSKILL, false)));
        when(rankedSeasonMapper.deactivate(1L)).thenReturn(1);

        rankedSeasonService.deactivateSeason(1L);

        ArgumentCaptor<SeasonDeactivatedPayload> payload = ArgumentCaptor.forClass(SeasonDeactivatedPayload.class);
        verify(matchWebhookClient, times(1)).notifySeasonDeactivated(payload.capture());
        assertThat(payload.getValue().event()).isEqualTo("SEASON_DEACTIVATED");
        assertThat(payload.getValue().seasonId()).isEqualTo(1L);
        assertThat(payload.getValue().gameType()).isEqualTo("bowspleef_1v1");
    }

    /**
     * REQ-005-04: deactivating a season that was already inactive sends nothing: the bot would otherwise remove the
     * roles of a newer season of the same game type.
     */
    @Test
    void deactivate_anAlreadyInactiveSeason_sendsNothing_REQ_005_04() {
        when(rankedSeasonMapper.findById(1L)).thenReturn(Optional.of(season(1L, RatingAlgorithm.OPENSKILL, false)));
        when(rankedSeasonMapper.deactivate(1L)).thenReturn(1);

        rankedSeasonService.deactivateSeason(1L);

        verifyNoInteractions(matchWebhookClient);
    }

    @Test
    void deactivate_anUnknownSeason_sendsNothing_REQ_005_04() {
        when(rankedSeasonMapper.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankedSeasonService.deactivateSeason(9L)).isInstanceOf(RankedSeasonNotFoundException.class);

        verify(rankedSeasonMapper, never()).deactivate(any());
        verifyNoInteractions(matchWebhookClient);
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
