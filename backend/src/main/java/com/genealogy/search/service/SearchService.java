package com.genealogy.search.service;

import com.genealogy.common.model.Role;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import com.genealogy.search.dto.request.PersonSearchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Advanced person search across name, chi, đời, dates and places (docs/analysis.md F20). */
public interface SearchService {

    /**
     * Finds the people matching every filter that was given.
     *
     * @param request the filters to apply
     * @param role the calling member's access level
     * @param pageable paging information
     * @return the matching page, ordered by tên then tên đệm then họ (§5.2)
     */
    Page<PersonSummaryResponse> searchPersons(PersonSearchRequest request, Role role, Pageable pageable);
}
