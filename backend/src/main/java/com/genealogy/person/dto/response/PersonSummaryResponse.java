package com.genealogy.person.dto.response;

import com.genealogy.common.model.Gender;

/**
 * A person in list form.
 *
 * @param id person id
 * @param displayName the primary name, rendered surname-first
 * @param gender recorded sex
 * @param generation đời, or null when not yet computed
 * @param living whether the person is treated as living
 * @param deathRecorded whether a death or burial is recorded, as opposed to presumed after a century
 */
// No branch field: a list is not redacted per row, and §3.6 withholds a living person's chi.
public record PersonSummaryResponse(
        Long id,
        String displayName,
        Gender gender,
        Integer generation,
        boolean living,
        boolean deathRecorded) {

    /**
     * Returns a copy with the recorded-death flag set.
     *
     * @param recorded whether a death or burial is recorded
     * @return the copy
     */
    // Filled in by `search`, which can ask `event`; `person` cannot, or the two features form a bean cycle.
    public PersonSummaryResponse withDeathRecorded(boolean recorded) {
        return new PersonSummaryResponse(id, displayName, gender, generation, living, recorded);
    }
}
