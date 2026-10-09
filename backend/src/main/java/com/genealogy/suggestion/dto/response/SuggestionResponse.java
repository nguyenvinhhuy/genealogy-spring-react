package com.genealogy.suggestion.dto.response;

import com.genealogy.suggestion.domain.SuggestionKind;
import com.genealogy.suggestion.domain.SuggestionStatus;
import com.genealogy.suggestion.domain.SuggestionTargetType;
import java.time.Instant;

/**
 * A suggestion as returned to clients.
 *
 * @param id suggestion id
 * @param targetType what kind of record this is about
 * @param targetId the record being changed, or null for a suggested new person
 * @param targetName that record's display name, so the queue reads without a call per row
 * @param kind what is being asked for
 * @param proposal what was proposed, rendered by the server, or null for a note or an unreadable payload
 * @param anchor for a new person, whom they would be added to, or null
 * @param payloadReadable false when the stored proposal could not be read back, so it can still be rejected
 * @param message why the member is asking for this
 * @param status where it is in the review queue
 * @param createdBy id of the member who offered it
 * @param createdByName that member's name
 * @param createdAt when it was offered
 * @param reviewedBy id of the member who reviewed it, or null
 * @param reviewedByName that member's name, or null
 * @param reviewedAt when it was reviewed, or null
 * @param reviewNote what the reviewer said
 */
public record SuggestionResponse(
        Long id,
        SuggestionTargetType targetType,
        Long targetId,
        String targetName,
        SuggestionKind kind,
        PersonSide proposal,
        AnchorView anchor,
        boolean payloadReadable,
        String message,
        SuggestionStatus status,
        Long createdBy,
        String createdByName,
        Instant createdAt,
        Long reviewedBy,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote) {
}
