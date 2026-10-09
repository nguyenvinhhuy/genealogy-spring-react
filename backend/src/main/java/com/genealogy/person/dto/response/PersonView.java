package com.genealogy.person.dto.response;

/** What a caller is allowed to see of one person: the full record, or the redacted one (CLAUDE.md §3.6). */
// Sealed, so a caller that forgets the distinction fails to compile instead of leaking.
public sealed interface PersonView permits PersonDetailResponse, PersonRedactedResponse {

    /**
     * Returns the person this view describes.
     *
     * @return person id
     */
    Long id();

    /**
     * Returns the name to show.
     *
     * @return the primary name, rendered surname-first
     */
    String displayName();

    /**
     * Reports whether the person is treated as living.
     *
     * @return true when living
     */
    boolean living();
}
