package com.playerscores.service;

import com.playerscores.dto.RankTitleResponse;
import com.playerscores.mapper.RankTitleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RankTitleService {

    private final RankTitleMapper rankTitleMapper;

    public RankTitleService(RankTitleMapper rankTitleMapper) {
        this.rankTitleMapper = rankTitleMapper;
    }

    @Transactional(readOnly = true)
    public List<RankTitleResponse> getAll() {
        return rankTitleMapper.findAll().stream()
                .map(rt -> new RankTitleResponse(rt.getMinElo(), rt.getName()))
                .toList();
    }
}
