package com.genealogy.event.service.impl;

import com.genealogy.common.model.GenealogyDate;
import java.time.LocalDate;
import java.util.Collection;

/** Decides whether a person is treated as living (CLAUDE.md §3.4), the flag §3.6 redacts by. */
final class LivingRule {

    // Nobody without a recorded death is assumed dead before this many years have passed.
    static final int PRESUMED_LIFESPAN_YEARS = 100;

    private LivingRule() {
    }

    /**
     * Reports whether a person is treated as living.
     *
     * @param deathOrBurialRecorded whether a DEATH or a BURIAL is recorded for them
     * @param births every recorded birth date, possibly none
     * @param today the current date in Vietnam
     * @return true unless they are recorded as dead or could only have been born over a lifespan ago
     */
    static boolean isLiving(boolean deathOrBurialRecorded, Collection<GenealogyDate> births, LocalDate today) {
        if (deathOrBurialRecorded) {
            return false;
        }
        LocalDate cutoff = today.minusYears(PRESUMED_LIFESPAN_YEARS);
        // Fails closed: "sau 1900" has no upper bound, and of two recorded births either may be the true one.
        return births.isEmpty()
                || births.stream().anyMatch(birth -> birth.latestPossible() == null
                        || birth.latestPossible().isAfter(cutoff));
    }
}
