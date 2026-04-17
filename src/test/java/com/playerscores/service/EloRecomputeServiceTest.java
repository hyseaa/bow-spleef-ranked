package com.playerscores.service;

import com.playerscores.client.MatchWebhookClient;
import com.playerscores.config.EloProperties;
import com.playerscores.mapper.EloMapper;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.RankTitleMapper;
import com.playerscores.mapper.RankedSeasonMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.Match;
import com.playerscores.model.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EloRecomputeServiceTest {

    @Mock
    private EloMapper eloMapper;
    @Mock
    private EloCalculatorService eloCalculator;
    @Mock
    private MatchMapper matchMapper;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private TeamPlayerMapper teamPlayerMapper;
    @Mock
    private RankedSeasonMapper rankedSeasonMapper;
    @Mock
    private EloProperties eloProperties;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private RankTitleMapper rankTitleMapper;
    @Mock
    private MatchWebhookClient matchWebhookClient;

    @InjectMocks
    private EloRecomputeService eloRecomputeService;

    @Test
    void recomputeSeasonElo_resetsAndReplaysAllMatches() {
        when(eloProperties.startingElo()).thenReturn(1000);

        Match m1 = match(1L, 10L);
        Match m2 = match(2L, 10L);
        when(matchMapper.findByRankedSeasonId(10L)).thenReturn(List.of(m1, m2));

        Team team = new Team();
        team.setId(1L);
        team.setScore(3);
        when(teamMapper.findByMatchId(anyLong())).thenReturn(List.of(team));
        when(teamPlayerMapper.findPlayerUuidsByTeamId(1L)).thenReturn(List.of());
        when(eloMapper.findUuidsWithNoMatches(10L)).thenReturn(List.of());
        when(eloMapper.findAllEloBySeasonId(10L)).thenReturn(List.of());
        when(rankTitleMapper.findAll()).thenReturn(List.of());
        when(playerMapper.findByUuids(any())).thenReturn(List.of());

        eloRecomputeService.recomputeSeasonElo(10L);

        verify(rankedSeasonMapper).clearEloDirty(10L);
        verify(eloMapper).deleteHistoryBySeasonId(10L);
        verify(eloMapper).resetPlayerSeasonElos(10L, 1000);
        verify(teamMapper, times(2)).findByMatchId(anyLong());
    }

    @Test
    void recomputeSeasonElo_noMatches_onlyResets() {
        when(eloProperties.startingElo()).thenReturn(1000);
        when(matchMapper.findByRankedSeasonId(5L)).thenReturn(List.of());
        when(eloMapper.findUuidsWithNoMatches(5L)).thenReturn(List.of());
        when(eloMapper.findAllEloBySeasonId(5L)).thenReturn(List.of());
        when(rankTitleMapper.findAll()).thenReturn(List.of());
        when(playerMapper.findByUuids(any())).thenReturn(List.of());

        eloRecomputeService.recomputeSeasonElo(5L);

        verify(rankedSeasonMapper).clearEloDirty(5L);
        verify(eloMapper).deleteHistoryBySeasonId(5L);
        verify(eloMapper).resetPlayerSeasonElos(5L, 1000);
        verify(teamMapper, never()).findByMatchId(anyLong());
    }

    private Match match(Long id, Long seasonId) {
        Match m = new Match();
        m.setId(id);
        m.setGameType("BEDWARS");
        m.setRankedSeasonId(seasonId);
        m.setPlayedAt(OffsetDateTime.now());
        return m;
    }

}
