package com.playerscores.service;

import com.playerscores.mapper.DuelChallengeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DuelChallengeExpiryScheduler {

    private final DuelChallengeMapper duelChallengeMapper;

    @Scheduled(fixedDelayString = "${duel.expiry-check-delay-ms:60000}")
    public void removeExpiredChallenges() {
        int removed = duelChallengeMapper.deleteExpired();
        if (removed > 0) {
            log.info("Duel challenge cleanup: removed {} expired challenge(s)", removed);
        }
    }
}
