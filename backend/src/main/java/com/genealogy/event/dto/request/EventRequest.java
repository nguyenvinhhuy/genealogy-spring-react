package com.genealogy.event.dto.request;

import com.genealogy.common.model.EventType;
import com.genealogy.common.util.ChangeNotes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating an event.
 *
 * @param subjectId the person or union the event belongs to
 * @param type what happened; its own subject kind is derived from this
 * @param date when it happened, may be null when only the fact is known
 * @param placeId where it happened, or null
 * @param description free-text notes
 * @param changeNote why this edit was made — the audit trail's "on what basis" (§3.8)
 * @param version the version the edit was made from, or null to skip the stale-edit check
 */
public record EventRequest(
        @NotNull Long subjectId,
        @NotNull EventType type,
        @Valid GenealogyDateRequest date,
        Long placeId,
        String description,
        @Size(max = ChangeNotes.MAX_LENGTH) String changeNote,
        Long version) {
}
