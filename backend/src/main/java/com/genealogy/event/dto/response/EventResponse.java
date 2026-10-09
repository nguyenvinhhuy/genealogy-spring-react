package com.genealogy.event.dto.response;

import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;

/**
 * An event as returned to clients.
 *
 * @param id event id
 * @param subjectType whether the subject is a person or a union
 * @param subjectId the subject id
 * @param type what happened
 * @param date when it happened, or null
 * @param placeId where it happened, or null
 * @param description free-text notes
 * @param version the row version an edit must be made from
 */
public record EventResponse(
        Long id,
        EventSubjectType subjectType,
        Long subjectId,
        EventType type,
        GenealogyDateResponse date,
        Long placeId,
        String description,
        long version) {
}
