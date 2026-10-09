package com.genealogy.source.dto.request;

import com.genealogy.common.model.SourceType;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating a source; only the title is required.
 *
 * @param title what the source is called
 * @param type what kind of evidence it is, defaults to OTHER
 * @param author who wrote or told it
 * @param dateText when it dates from, as free text
 * @param repository where the original is now
 * @param notes free-text notes
 * @param changeNote why the change is being made, for the change history
 * @param version the version the form was loaded at, or null to skip the stale-edit check
 */
public record SourceRequest(
        @NotBlank @Size(max = MAX_TITLE) String title,
        SourceType type,
        @Size(max = MAX_AUTHOR) String author,
        @Size(max = MAX_DATE_TEXT) String dateText,
        @Size(max = MAX_REPOSITORY) String repository,
        @Size(max = MAX_NOTES) String notes,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {

    // Longest title the column holds, named so the GEDCOM import can trim to it before saving (§8.8 #4).
    public static final int MAX_TITLE = 300;

    // Longest author the column holds.
    public static final int MAX_AUTHOR = 200;

    // Longest free-text date the column holds.
    public static final int MAX_DATE_TEXT = 100;

    // Longest repository name the column holds.
    public static final int MAX_REPOSITORY = 300;

    // Longest note accepted; the column is TEXT, so this limit is the request's own.
    public static final int MAX_NOTES = 10000;
}
