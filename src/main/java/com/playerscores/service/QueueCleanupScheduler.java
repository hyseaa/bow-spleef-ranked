package com.playerscores.service;

import com.playerscores.mapper.QueueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueCleanupScheduler {

    private final QueueMapper queueMapper;

    @Scheduled(fixedDelayString = "${queue.cleanup-delay-ms:60000}")
    public void removeExpiredEntries() {
        int removed = queueMapper.deleteExpired();
        if (removed > 0) {
            log.info("Queue cleanup: removed {} expired entry(-ies) (>1h)", removed);
        }
    }
}
