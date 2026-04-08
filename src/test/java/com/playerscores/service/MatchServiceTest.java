package com.playerscores.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.MatchResponse;
import com.playerscores.dto.PlayerSummaryResponse;
import com.playerscores.dto.TeamRequest;
import com.playerscores.exception.MatchNotFoundException;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.MatchPlayerStatMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.Match;
import com.playerscores.model.Player;
import com.playerscores.model.Team;
import com.playerscores.model.TeamPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private MatchService matchService;

    @Test
    void createMatch_playerNotFound_throwsException() {
        UUID uuid = UUID.randomUUID();
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.empty());

        CreateMatchRequest request = new CreateMatchRequest("BEDWARS", "DISCORD_BOT",
                List.of(new TeamRequest(3, List.of(uuid)),
                        new TeamRequest(1, List.of())), null);

        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void createMatch_success_returnsResponse() {
        UUID uuid = UUID.randomUUID();
        Player player = new Player();
        player.setUuid(uuid);
        when(playerMapper.findByUuid(uuid)).thenReturn(Optional.of(player));

        doAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(1L);
            m.setPlayedAt(LocalDateTime.now());
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
        match.setPlayedAt(LocalDateTime.now());
        when(matchMapper.findById(1L)).thenReturn(Optional.of(match));

        Team team = new Team();
        team.setId(1L);
        team.setScore(3);
        when(teamMapper.findByMatchId(1L)).thenReturn(List.of(team));
        when(teamPlayerMapper.findPlayersByTeamId(1L))
                .thenReturn(List.of(new PlayerSummaryResponse(uuid, "Notch")));

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
