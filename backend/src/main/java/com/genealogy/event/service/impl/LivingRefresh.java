package com.genealogy.event.service.impl;

import com.genealogy.common.util.VietnamTime;
import com.genealogy.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Re-derives every person's living flag at startup and every night. */
// The lifespan rule depends on today's date, so a flag can change without anyone editing anything.
@Slf4j
@Component
@RequiredArgsConstructor
public class LivingRefresh {

    private final EventService eventService;

    /** Recomputes every living flag once the app has started. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        // A rule change reaches rows written under the old rule without waiting for the first nightly run.
        refresh();
    }

    /** Recomputes every living flag early each morning, Vietnam time. */
    @Scheduled(cron = "0 40 3 * * *", zone = VietnamTime.ZONE_ID)
    public void nightly() {
        refresh();
    }

    /** Runs the sweep and logs how many flags moved. */
    private void refresh() {
        // A failure at boot must not stop the app: the nightly run, or any event write, repairs the flags.
        try {
            // Logged even at zero: a silent sweep cannot be told apart from one that never ran.
            log.info("Living-flag sweep done: {} changed", eventService.recomputeAllLiving());
        } catch (RuntimeException e) {
            log.error("Living-flag sweep failed; flags stay as stored until the next run", e);
        }
    }
}
