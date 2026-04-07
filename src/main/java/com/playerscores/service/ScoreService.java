package com.playerscores.service;

import com.playerscores.dto.LeaderboardEntryDto;
import com.playerscores.dto.PageResponse;
import com.playerscores.dto.ScoreRequest;
import com.playerscores.dto.ScoreResponse;
import com.playerscores.exception.PlayerNotFoundException;
import com.playerscores.mapper.PlayerMapper;
import com.playerscores.mapper.ScoreMapper;
import com.playerscores.model.Score;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreMapper scoreMapper;
    private final PlayerMapper playerMapper;

    @Transactional
    public ScoreResponse recordScore(ScoreRequest request) {
        playerMapper.findById(request.getPlayerId())
                .orElseThrow(() -> new PlayerNotFoundException(request.getPlayerId()));

        Score score = new Score();
        score.setPlayerId(request.getPlayerId());
        score.setValue(request.getValue());
        score.setGame(request.getGame());
        scoreMapper.insert(score);
        return toResponse(score);
    }

    public PageResponse<ScoreResponse> getScores(Long playerId, String game, int page, int size) {
        playerMapper.findById(playerId)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));

        int offset = page * size;

        List<Score> scores;
        long total;

        if (game != null && !game.isBlank()) {
            scores = scoreMapper.findByPlayerIdAndGame(playerId, game, size, offset);
            total = scoreMapper.countByPlayerIdAndGame(playerId, game);
        } else {
            scores = scoreMapper.findByPlayerId(playerId, size, offset);
            total = scoreMapper.countByPlayerId(playerId);
        }

        return PageResponse.of(scores.stream().map(this::toResponse).toList(), page, size, total);
    }

    public List<LeaderboardEntryDto> getLeaderboard(String game, int limit) {
        return scoreMapper.findLeaderboard(game, limit);
    }

    private ScoreResponse toResponse(Score score) {
        ScoreResponse response = new ScoreResponse();
        response.setId(score.getId());
        response.setPlayerId(score.getPlayerId());
        response.setValue(score.getValue());
        response.setGame(score.getGame());
        response.setCreatedAt(score.getCreatedAt());
        return response;
    }
}
