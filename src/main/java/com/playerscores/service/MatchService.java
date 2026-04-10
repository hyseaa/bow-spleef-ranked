package com.playerscores.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playerscores.dto.CreateMatchRequest;
import com.playerscores.dto.MatchResponse;
import com.playerscores.dto.PlayerSummaryResponse;
import com.playerscores.dto.TeamRequest;
import com.playerscores.dto.TeamResponse;
import com.playerscores.exception.MatchNotFoundException;
import com.playerscores.mapper.MatchMapper;
import com.playerscores.mapper.MatchPlayerStatMapper;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.TeamMapper;
import com.playerscores.mapper.TeamPlayerMapper;
import com.playerscores.model.Match;
import com.playerscores.model.MatchPlayerStat;
import com.playerscores.model.Team;
import com.playerscores.model.TeamPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchMapper matchMapper;
    private final TeamMapper teamMapper;
    private final TeamPlayerMapper teamPlayerMapper;
    private final MatchPlayerStatMapper matchPlayerStatMapper;
    private final PlayerMapper playerMapper;
    private final UsernameCache usernameCache;
    private final ObjectMapper objectMapper;

    @Transactional
    public MatchResponse createMatch(CreateMatchRequest request) {
        for (TeamRequest teamReq : request.teams()) {
            for (UUID uuid : teamReq.playerUuids()) {
                playerMapper.insertIfAbsent(uuid);
            }
        }

        Match match = new Match();
        match.setGameType(request.gameType());
        match.setSource(request.source());
        matchMapper.insert(match);

        for (TeamRequest teamReq : request.teams()) {
            Team team = new Team();
            team.setMatchId(match.getId());
            team.setScore(teamReq.score());
            teamMapper.insert(team);

            for (UUID uuid : teamReq.playerUuids()) {
                TeamPlayer tp = new TeamPlayer();
                tp.setTeamId(team.getId());
                tp.setPlayerUuid(uuid);
                teamPlayerMapper.insert(tp);

                Map<String, Object> stats = request.playerStats() != null ? request.playerStats().get(uuid) : null;
                if (stats != null) {
                    MatchPlayerStat stat = new MatchPlayerStat();
                    stat.setTeamPlayerId(tp.getId());
                    try {
                        stat.setStats(objectMapper.writeValueAsString(stats));
                    } catch (JsonProcessingException e) {
                        throw new IllegalArgumentException("Invalid stats for player " + uuid, e);
                    }
                    matchPlayerStatMapper.insert(stat);
                }
            }
        }

        return getMatch(match.getId());
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long id) {
        Match match = matchMapper.findById(id).orElseThrow(() -> new MatchNotFoundException(id));

        List<TeamResponse> teams = teamMapper.findByMatchId(id).stream()
                .map(team -> {
                    List<PlayerSummaryResponse> players = teamPlayerMapper.findPlayerUuidsByTeamId(team.getId())
                            .stream()
                            .map(uuid -> new PlayerSummaryResponse(uuid, usernameCache.get(uuid)))
                            .toList();
                    return new TeamResponse(team.getId(), team.getScore(), players);
                })
                .toList();

        return new MatchResponse(match.getId(), match.getGameType(), match.getSource(), match.getPlayedAt(), teams);
    }
}
