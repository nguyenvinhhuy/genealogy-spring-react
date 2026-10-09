package com.genealogy.source.dto.response;

import com.genealogy.common.model.SourceType;

/**
 * A source as returned to clients.
 *
 * @param id source id
 * @param title what the source is called
 * @param type what kind of evidence it is
 * @param author who wrote or told it
 * @param dateText when it dates from
 * @param repository where the original is now
 * @param notes free-text notes
 * @param citationCount how many facts it has been used to back up
 * @param version the row version, sent back on the next edit
 */
public record SourceResponse(
        Long id,
        String title,
        SourceType type,
        String author,
        String dateText,
        String repository,
        String notes,
        long citationCount,
        long version) {
}
