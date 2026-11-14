package net.bowspleef.ranked.service;

import net.bowspleef.ranked.mapper.QueueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueCleanupScheduler {

    private final QueueMapper queueMapper;

    @Value("${queue.entry-ttl-minutes:60}")
    private int entryTtlMinutes;

    @Scheduled(fixedDelayString = "${queue.cleanup-delay-ms:60000}")
    public void removeExpiredEntries() {
        int removed = queueMapper.deleteExpired(entryTtlMinutes);
        if (removed > 0) {
            log.info("Queue cleanup: removed {} expired entry(-ies) (>{} min)", removed, entryTtlMinutes);
        }
    }
}
