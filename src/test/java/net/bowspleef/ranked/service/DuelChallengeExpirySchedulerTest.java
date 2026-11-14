package net.bowspleef.ranked.service;

import net.bowspleef.ranked.mapper.DuelChallengeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DuelChallengeExpirySchedulerTest {

    @Mock
    private DuelChallengeMapper duelChallengeMapper;
    @Mock
    private DuelChallengeService duelChallengeService;

    @InjectMocks
    private DuelChallengeExpiryScheduler scheduler;

    /** REQ-006-01, REQ-006-02: each pass removes expired PENDING challenges and cancels stale ACTIVE duels. */
    @Test
    void eachPassCleansPendingChallengesAndCancelsStaleActiveDuels_REQ_006_01() {
        scheduler.removeExpiredChallenges();

        verify(duelChallengeMapper).deleteExpired();
        verify(duelChallengeService).expireStaleActiveDuels();
    }
}
