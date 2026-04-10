package com.playerscores.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.MatchResponse;
import com.playerscores.dto.TeamRequest;
import com.playerscores.exception.MatchNotFoundException;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.MatchPlayerStatMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.Match;
import com.playerscores.model.Team;
import com.playerscores.model.TeamPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchMapper matchMapper;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private TeamPlayerMapper teamPlayerMapper;
    @Mock
    private MatchPlayerStatMapper matchPlayerStatMapper;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private UsernameCache usernameCache;
    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private MatchService matchService;

    @Test
    void createMatch_unknownPlayer_isInsertedAutomatically() {
        UUID uuid = UUID.randomUUID();

        doAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(1L);
            m.setPlayedAt(OffsetDateTime.now());
            return null;
        }).when(matchMapper).insert(any(Match.class));
        doAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(1L);
            return null;
        }).when(teamMapper).insert(any(Team.class));
        doAnswer(inv -> {
            TeamPlayer tp = inv.getArgument(0);
            tp.setId(1L);
            return null;
        }).when(teamPlayerMapper).insert(any(TeamPlayer.class));

        Match match = new Match();
        match.setId(1L);
        match.setGameType("BEDWARS");
        match.setSource("DISCORD_BOT");
        match.setPlayedAt(OffsetDateTime.now());
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));
        when(teamMapper.findByMatchId(1L)).thenReturn(List.of());

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(uuid))), null);

        MatchResponse response = matchService.createMatch(request);

        assertThat(response.gameType()).isEqualTo("BEDWARS");
    }

    @Test
    void createMatch_success_returnsResponse() {
        UUID uuid = UUID.randomUUID();

        doAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(1L);
            m.setPlayedAt(OffsetDateTime.now());
            return null;
        }).when(matchMapper).insert(any(Match.class));
        doAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(1L);
            return null;
        }).when(teamMapper).insert(any(Team.class));
        doAnswer(inv -> {
            TeamPlayer tp = inv.getArgument(0);
            tp.setId(1L);
            return null;
        }).when(teamPlayerMapper).insert(any(TeamPlayer.class));

        Match match = new Match();
        match.setId(1L);
        match.setGameType("BEDWARS");
        match.setSource("DISCORD_BOT");
        match.setPlayedAt(OffsetDateTime.now());
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));

        Team team = new Team();
        team.setId(1L);
        team.setScore(3);
        when(teamMapper.findByMatchId(1L)).thenReturn(List.of(team));
        when(teamPlayerMapper.findPlayerUuidsByTeamId(1L)).thenReturn(List.of(uuid));
        when(usernameCache.get(uuid)).thenReturn("Notch");

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(uuid)),
                        new TeamRequest(1, List.of())), null);

        MatchResponse response = matchService.createMatch(request);

        assertThat(response.gameType()).isEqualTo("BEDWARS");
        assertThat(response.teams()).hasSize(1);
        assertThat(response.teams().getFirst().players()).hasSize(1);
    }

    @Test
    void getMatch_notFound_throwsException() {
        when(matchMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getMatch(99L))
                .isInstanceOf(MatchNotFoundException.class);
    }
}
