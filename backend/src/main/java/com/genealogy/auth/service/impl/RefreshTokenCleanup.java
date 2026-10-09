package com.genealogy.auth.service.impl;

import com.genealogy.auth.repository.RefreshTokenRepository;
import com.genealogy.common.security.JwtProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes refresh tokens that can never be presented again. */
// Without it the table only grows: every login and every rotation leaves a row nothing will ever read.
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanup {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    /** Deletes every token that expired or was revoked longer ago than one refresh lifetime. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void removeSpentTokens() {
        // One TTL of grace, so a row is still there to explain a replay that happened just before the sweep.
        Instant before = Instant.now().minus(jwtProperties.refreshTokenTtl());
        int removed = refreshTokenRepository.deleteSpent(before);
        if (removed > 0) {
            log.info("Removed {} spent refresh tokens older than {}", removed, before);
        }
    }
}
