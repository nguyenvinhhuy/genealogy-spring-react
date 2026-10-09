package com.genealogy.search.dto.request;

import jakarta.validation.constraints.Min;

/**
 * Every filter a person search accepts, all optional and all combined with AND (F20).
 *
 * @param query accent-insensitive search across all of a person's names
 * @param branchId chi / phái filter
 * @param generation đời filter
 * @param living true for the living, false for the deceased, null for both
 * @param birthYearFrom earliest recorded birth year
 * @param birthYearTo latest recorded birth year
 * @param deathYearFrom earliest recorded death year
 * @param deathYearTo latest recorded death year
 * @param placeId a place, matching that place and everything beneath it in the hierarchy (§3.7)
 */
public record PersonSearchRequest(
        String query,
        Long branchId,
        @Min(1) Integer generation,
        Boolean living,
        Integer birthYearFrom,
        Integer birthYearTo,
        Integer deathYearFrom,
        Integer deathYearTo,
        Long placeId) {

    /**
     * Reports whether any filter needs the event records to answer.
     *
     * @return true when a date range or a place was given
     */
    public boolean touchesEvents() {
        return birthYearFrom != null || birthYearTo != null
                || deathYearFrom != null || deathYearTo != null
                || placeId != null;
    }

    /**
     * Reports whether any filter narrows on a field the redacted view withholds.
     *
     * @return true when a date, a place or a chi was given
     */
    public boolean narrowsPrivateFields() {
        // branchId belongs here too: removing chi from the response is decorative while it stays filterable.
        return touchesEvents() || branchId != null;
    }
}
