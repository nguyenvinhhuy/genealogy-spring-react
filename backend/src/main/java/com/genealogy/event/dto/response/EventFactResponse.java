package com.genealogy.event.dto.response;

import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import java.time.LocalDate;

/**
 * One dated event, flattened for whole-graph analysis; the projection `quality` reads through (CLAUDE.md §4).
 *
 * @param subjectType whether the subject is a person or a union
 * @param subjectId the subject id
 * @param type what happened
 * @param year the recorded year, or null
 * @param earliest the first Gregorian day the date allows, or null when it sets no lower bound
 * @param latest the last Gregorian day the date allows, or null when it sets no upper bound
 */
public record EventFactResponse(
        EventSubjectType subjectType,
        Long subjectId,
        EventType type,
        Integer year,
        LocalDate earliest,
        LocalDate latest) {
}
