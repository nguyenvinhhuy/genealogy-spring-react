package com.genealogy.media.dto.request;

import com.genealogy.common.util.ChangeNotes;
import com.genealogy.media.domain.MediaKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Payload for editing what is recorded about an already-uploaded file.
 *
 * @param kind what the file is, or null to leave it as it was
 * @param caption what the family wants written under it; null leaves it alone, blank clears it
 * @param sortOrder its position in the record's gallery, or null to leave it
 * @param changeNote why the edit was made, for the change history (§3.8)
 * @param version the version the edit was made from, or null to skip the stale-edit check
 */
public record MediaUpdateRequest(
        MediaKind kind,
        @Size(max = MAX_CAPTION) String caption,
        @Min(0) Integer sortOrder,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {

    // Longest caption accepted; the column is TEXT, so this limit is the request's own (§8.9 #44).
    public static final int MAX_CAPTION = 1000;
}
