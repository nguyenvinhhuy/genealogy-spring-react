package com.genealogy.person.dto.response;

import com.genealogy.common.model.Gender;
import java.time.Instant;
import java.util.List;

/**
 * A person in full.
 *
 * @param id person id
 * @param displayName the primary name, rendered surname-first
 * @param gender recorded sex
 * @param generation đời, or null when not yet computed
 * @param branchId clan branch id, or null
 * @param living whether the person is treated as living
 * @param notes free-text notes
 * @param names every recorded name
 * @param createdAt when the record was created
 * @param version the row version an edit must be made from
 */
public record PersonDetailResponse(
        Long id,
        String displayName,
        Gender gender,
        Integer generation,
        Long branchId,
        boolean living,
        String notes,
        List<PersonNameResponse> names,
        Instant createdAt,
        long version)
        implements PersonView {
}
