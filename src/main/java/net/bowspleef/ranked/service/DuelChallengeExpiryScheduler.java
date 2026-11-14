package net.bowspleef.ranked.service;

import net.bowspleef.ranked.mapper.DuelChallengeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DuelChallengeExpiryScheduler {

    private final DuelChallengeMapper duelChallengeMapper;
    private final DuelChallengeService duelChallengeService;

    @Scheduled(fixedDelayString = "${duel.expiry-check-delay-ms:60000}")
    public void removeExpiredChallenges() {
        int removed = duelChallengeMapper.deleteExpired();
        if (removed > 0) {
            log.info("Duel challenge cleanup: removed {} expired challenge(s)", removed);
        }
        // spec 006: accepted duels that nobody plays
        int expired = duelChallengeService.expireStaleActiveDuels();
        if (expired > 0) {
            log.info("Duel challenge cleanup: cancelled {} stale ACTIVE duel(s)", expired);
        }
    }
}
