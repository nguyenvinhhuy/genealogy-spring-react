package com.genealogy.person.dto.request;

import java.util.Collection;

/**
 * The filters a person search applies, all optional and all combined with AND (F20).
 *
 * @param query accent-insensitive search across a person's names, or null
 * @param branchIds the chi asked for and every chi beneath it, or null for no chi filter; an empty set matches nobody
 * @param generation đời filter, or null
 * @param living true for the living, false for the deceased, null for both
 * @param restrictToIds ids another filter already narrowed to, or null for none; an empty set matches nobody
 * @param alternateNames whether the query may match a living person's alternate names, which §3.6 withholds
 */
public record PersonSearchCriteria(
        String query,
        Collection<Long> branchIds,
        Integer generation,
        Boolean living,
        Collection<Long> restrictToIds,
        boolean alternateNames) {
}
