package com.playerscores.service;

import com.playerscores.client.MatchWebhookClient;
import com.playerscores.dto.InitialRating;
import com.playerscores.dto.RatingResult;
import com.playerscores.mapper.EloMapper;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.EloHistory;
import com.playerscores.model.Match;
import com.playerscores.model.RankedSeason;
import com.playerscores.model.RatingAlgorithm;
import com.playerscores.model.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.playerscores.rating.GoldenRatings.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EloRecomputeServiceTest {

    private static final InitialRating INITIAL = new InitialRating(1000, 25.0, 25.0 / 3.0);

    @Mock
    private EloMapper eloMapper;
    @Mock
    private RatingEngines ratingEngines;
    @Mock
    private MatchMapper matchMapper;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private TeamPlayerMapper teamPlayerMapper;
    @Mock
    private RankedSeasonMapper rankedSeasonMapper;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private MatchWebhookClient matchWebhookClient;

    @InjectMocks
    private EloRecomputeService eloRecomputeService;

    @Test
    void recomputeSeasonElo_resetsAndReplaysAllMatches() {
        RatingEngine engine = engine(RatingAlgorithm.OPENSKILL);
        when(rankedSeasonMapper.findById(10L)).thenReturn(Optional.of(season(10L, RatingAlgorithm.OPENSKILL)));

        Match m1 = match(1L, 10L);
        Match m2 = match(2L, 10L);
        when(matchMapper.findByRankedSeasonId(10L)).thenReturn(List.of(m1, m2));

        Team team = new Team();
        team.setId(1L);
        team.setScore(3);
        when(teamMapper.findByMatchId(anyLong())).thenReturn(List.of(team));
        when(teamPlayerMapper.findPlayerUuidsByTeamId(1L)).thenReturn(List.of());
        when(engine.rateMatch(any())).thenReturn(Map.of());
        when(eloMapper.findUuidsWithNoMatches(10L)).thenReturn(List.of());
        when(eloMapper.findAllEloBySeasonId(10L)).thenReturn(List.of());
        when(playerMapper.findByUuids(any())).thenReturn(List.of());

        eloRecomputeService.recomputeSeasonElo(10L);

        verify(rankedSeasonMapper).clearEloDirty(10L);
        verify(eloMapper).deleteHistoryBySeasonId(10L);
        verify(eloMapper).resetPlayerSeasonElos(10L, 1000, 25.0, 25.0 / 3.0);
        verify(teamMapper, times(2)).findByMatchId(anyLong());
    }

    @Test
    void recomputeSeasonElo_noMatches_onlyResets() {
        engine(RatingAlgorithm.OPENSKILL);
        when(rankedSeasonMapper.findById(5L)).thenReturn(Optional.of(season(5L, RatingAlgorithm.OPENSKILL)));
        when(matchMapper.findByRankedSeasonId(5L)).thenReturn(List.of());
        when(eloMapper.findUuidsWithNoMatches(5L)).thenReturn(List.of());
        when(eloMapper.findAllEloBySeasonId(5L)).thenReturn(List.of());
        when(playerMapper.findByUuids(any())).thenReturn(List.of());

        eloRecomputeService.recomputeSeasonElo(5L);

        verify(rankedSeasonMapper).clearEloDirty(5L);
        verify(eloMapper).deleteHistoryBySeasonId(5L);
        verify(eloMapper).resetPlayerSeasonElos(5L, 1000, 25.0, 25.0 / 3.0);
        verify(teamMapper, never()).findByMatchId(anyLong());
    }

    /** REQ-001-06: a recompute resets and replays with the season's own algorithm. */
    @ParameterizedTest
    @EnumSource(RatingAlgorithm.class)
    void recomputeSeasonElo_usesTheSeasonsEngine_REQ_001_06(RatingAlgorithm algorithm) {
        RatingEngine engine = engine(algorithm);
        when(engine.initialRating()).thenReturn(new InitialRating(1000, 25.0, 25.0 / 3.0));
        when(rankedSeasonMapper.findById(7L)).thenReturn(Optional.of(season(7L, algorithm)));
        when(matchMapper.findByRankedSeasonId(7L)).thenReturn(List.of(match(1L, 7L)));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        twoTeams(a, b);
        when(eloMapper.findEloByUuidsAndSeason(any(), any())).thenReturn(List.of(
                snapshot(a, 1000, 25.0, 25.0 / 3.0, 0), snapshot(b, 1000, 25.0, 25.0 / 3.0, 0)));
        when(engine.rateMatch(any())).thenReturn(Map.of(
                a, new RatingResult(a, 1020, 25.0, 25.0 / 3.0), b, new RatingResult(b, 980, 25.0, 25.0 / 3.0)));
        when(eloMapper.findUuidsWithNoMatches(7L)).thenReturn(List.of());
        when(eloMapper.findAllEloBySeasonId(7L)).thenReturn(List.of());
        when(playerMapper.findByUuids(any())).thenReturn(List.of());

        eloRecomputeService.recomputeSeasonElo(7L);

        verify(ratingEngines, times(2)).get(algorithm);
        verify(engine).rateMatch(any());
        verify(eloMapper).updateElo(a, 7L, 1020, 25.0, 25.0 / 3.0);
        verify(eloMapper).updateElo(b, 7L, 980, 25.0, 25.0 / 3.0);
    }

    /** REQ-001-05: a live match is rated by the season's engine; its results are stored and historized. */
    @ParameterizedTest
    @EnumSource(RatingAlgorithm.class)
    void applyEloUpdates_usesTheSeasonsEngine_REQ_001_05(RatingAlgorithm algorithm) {
        RatingEngine engine = engine(algorithm);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Team ta = team(1L, 3);
        Team tb = team(2L, 1);
        when(eloMapper.findEloByUuidsAndSeason(any(), any())).thenReturn(List.of(
                snapshot(a, 1040, 26.0, 7.9, 3), snapshot(b, 990, 24.0, 8.0, 5)));
        when(engine.rateMatch(any())).thenReturn(Map.of(
                a, new RatingResult(a, 1055, 26.5, 7.7), b, new RatingResult(b, 975, 23.6, 7.8)));

        eloRecomputeService.applyEloUpdates(42L, season(3L, algorithm), List.of(ta, tb), Map.of(1L, List.of(a), 2L, List.of(b)));

        verify(eloMapper).upsertPlayerSeasonElo(a, 3L, INITIAL.elo(), INITIAL.mu(), INITIAL.sigma());
        verify(eloMapper).updateElo(a, 3L, 1055, 26.5, 7.7);
        verify(eloMapper).updateElo(b, 3L, 975, 23.6, 7.8);
        ArgumentCaptor<EloHistory> history = ArgumentCaptor.forClass(EloHistory.class);
        verify(eloMapper, times(2)).insertHistory(history.capture());
        assertThat(history.getAllValues())
                .extracting(EloHistory::getEloBefore, EloHistory::getEloAfter, EloHistory::getEloChange)
                .containsExactlyInAnyOrder(
                        tuple(1040, 1055, 15),
                        tuple(990, 975, -15));
    }

    private RatingEngine engine(RatingAlgorithm algorithm) {
        RatingEngine engine = mock(RatingEngine.class);
        when(ratingEngines.get(algorithm)).thenReturn(engine);
        lenient().when(engine.initialRating()).thenReturn(INITIAL);
        lenient().when(engine.algorithm()).thenReturn(algorithm);
        return engine;
    }

    private void twoTeams(UUID a, UUID b) {
        Team ta = team(1L, 3);
        Team tb = team(2L, 1);
        when(teamMapper.findByMatchId(anyLong())).thenReturn(List.of(ta, tb));
        when(teamPlayerMapper.findPlayerUuidsByTeamId(1L)).thenReturn(List.of(a));
        when(teamPlayerMapper.findPlayerUuidsByTeamId(2L)).thenReturn(List.of(b));
    }

    private static Team team(Long id, int score) {
        Team team = new Team();
        team.setId(id);
        team.setScore(score);
        return team;
    }

    private static RankedSeason season(Long id, RatingAlgorithm algorithm) {
        RankedSeason season = new RankedSeason();
        season.setId(id);
        season.setRatingAlgorithm(algorithm);
        return season;
    }

    private static Match match(Long id, Long seasonId) {
        Match m = new Match();
        m.setId(id);
        m.setGameType("BEDWARS");
        m.setRankedSeasonId(seasonId);
        m.setPlayedAt(OffsetDateTime.now());
        return m;
    }
}
