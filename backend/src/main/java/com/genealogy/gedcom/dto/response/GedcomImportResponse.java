package com.genealogy.gedcom.dto.response;

import java.util.List;

/**
 * What an import did.
 *
 * @param personsCreated how many people were added
 * @param familiesCreated how many unions were added
 * @param eventsCreated how many events were added
 * @param sourcesCreated how many sources were added
 * @param citationsCreated how many citations were added
 * @param skipped how many records the file carried that this app does not model
 * @param dropped how many records and sub-records this app models but could not keep
 * @param warnings what was lost or guessed, one line each
 */
// `dropped` is separate from `skipped`: the UI calls the latter "not in scope", which a discarded cụ is not.
public record GedcomImportResponse(
        int personsCreated,
        int familiesCreated,
        int eventsCreated,
        int sourcesCreated,
        int citationsCreated,
        int skipped,
        int dropped,
        List<String> warnings) {
}
