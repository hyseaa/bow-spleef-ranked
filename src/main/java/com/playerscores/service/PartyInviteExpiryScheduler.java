package com.playerscores.service;

import com.playerscores.mapper.PartyInviteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartyInviteExpiryScheduler {

    private final PartyInviteMapper partyInviteMapper;

    @Scheduled(fixedDelayString = "${party.invite-expiry-check-delay-ms:60000}")
    public void removeExpiredInvites() {
        int removed = partyInviteMapper.deleteExpired();
        if (removed > 0) {
            log.info("Party invite cleanup: removed {} expired invite(s)", removed);
        }
    }
}
