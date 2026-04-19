package com.playerscores.service;

import com.playerscores.dto.RankTitleResponse;
import com.playerscores.mapper.RankTitleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class RankTitleService {

    private final RankTitleMapper rankTitleMapper;

    public RankTitleService(RankTitleMapper rankTitleMapper) {
        this.rankTitleMapper = rankTitleMapper;
    }

    @Transactional(readOnly = true)
    public List<RankTitleResponse> getAll() {
        log.debug("Fetching all rank titles");
        List<RankTitleResponse> result = rankTitleMapper.findAll().stream()
                .map(rt -> new RankTitleResponse(rt.getMinPercentile(), rt.getName()))
                .toList();
        log.debug("Found {} rank title(s)", result.size());
        return result;
    }
}
