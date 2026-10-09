package com.genealogy.source.dto.request;

import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for citing a source against one recorded fact, naming an existing source or describing a new one.
 *
 * @param sourceId the source being cited, or null when {@code newSource} is given
 * @param newSource a source to create and cite in the same transaction, or null when {@code sourceId} is given
 * @param targetType what kind of record it backs up
 * @param targetId the record id
 * @param locator where inside the source, e.g. "trang 12"
 * @param quote what the source actually says
 * @param changeNote why the citation is being added or changed, for the change history
 * @param version the version the form was loaded at, or null to skip the stale-edit check
 */
public record CitationRequest(
        Long sourceId,
        @Valid SourceRequest newSource,
        @NotNull CitationTargetType targetType,
        @NotNull Long targetId,
        @Size(max = MAX_LOCATOR) String locator,
        @Size(max = MAX_QUOTE) String quote,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {

    // Longest locator the column holds, named so the GEDCOM import can trim to it before saving (§8.8 #4).
    public static final int MAX_LOCATOR = 200;

    // Longest quote accepted; the column is TEXT, so this limit is the request's own.
    public static final int MAX_QUOTE = 10000;
}
