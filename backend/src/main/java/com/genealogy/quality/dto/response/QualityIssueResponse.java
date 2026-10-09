package com.genealogy.quality.dto.response;

import com.genealogy.quality.domain.IssueCode;
import com.genealogy.quality.domain.IssueSeverity;
import java.util.Map;

/**
 * One data-quality finding.
 *
 * @param code which rule fired
 * @param severity how seriously to take it
 * @param personId the person the finding is about
 * @param personName their primary name
 * @param relatedPersonId the other person involved, or null
 * @param relatedPersonName that person's primary name, or null
 * @param familyId the union the finding concerns, or null when it concerns a person alone
 * @param params the numbers the message needs, e.g. an age or a year
 */
// A code plus its numbers rather than a sentence, so the client builds the message in the reader's language.
public record QualityIssueResponse(
        IssueCode code,
        IssueSeverity severity,
        Long personId,
        String personName,
        Long relatedPersonId,
        String relatedPersonName,
        Long familyId,
        Map<String, Object> params) {
}
