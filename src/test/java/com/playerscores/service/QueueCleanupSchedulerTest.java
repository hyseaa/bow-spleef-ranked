package com.playerscores.service;

import com.playerscores.mapper.QueueMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

/** KI-14, KI-11: the lifetime of a queue entry is a setting. */
@ExtendWith(MockitoExtension.class)
class QueueCleanupSchedulerTest {

    @Mock
    private QueueMapper queueMapper;

    @InjectMocks
    private QueueCleanupScheduler scheduler;

    @Test
    void removesEntriesOlderThanTheConfiguredLifetime_KI_14() {
        ReflectionTestUtils.setField(scheduler, "entryTtlMinutes", 45);

        scheduler.removeExpiredEntries();

        verify(queueMapper).deleteExpired(45);
    }
}
