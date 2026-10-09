package com.genealogy.search.service.impl;

import com.genealogy.branch.service.BranchService;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Role;
import com.genealogy.event.service.EventService;
import com.genealogy.person.dto.request.PersonSearchCriteria;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import com.genealogy.search.dto.request.PersonSearchRequest;
import com.genealogy.search.service.SearchService;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link SearchService}. */
// Its own feature like `merge`: `event` depends on `person`, so searching across both closes a cycle.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    private final PersonService personService;
    private final EventService eventService;
    private final PlaceService placeService;
    private final BranchService branchService;

    /**
     * Finds the people matching every filter that was given.
     *
     * @param request the filters to apply
     * @param role the calling member's access level
     * @param pageable paging information
     * @return the matching page, ordered by tên then tên đệm then họ
     */
    @Override
    public Page<PersonSummaryResponse> searchPersons(
            PersonSearchRequest request, Role role, Pageable pageable) {

        Boolean living = request.living();
        if (request.narrowsPrivateFields() && !Role.maySeeLivingDetails(role)) {
            // Or a MEMBER recovers a hidden birth year by elimination: ask 1990–1995, see who comes back.
            if (Boolean.TRUE.equals(living)) {
                return Page.empty(pageable);
            }
            living = false;
        }

        // A chi filter also matches every phái and nhánh beneath it, or a precisely-entered record goes unfound.
        Set<Long> branchIds = request.branchId() == null ? null : branchService.findWithDescendants(request.branchId());
        PersonSearchCriteria criteria = new PersonSearchCriteria(
                request.query(), branchIds, request.generation(), living, matchingPersonIds(request),
                // A living person's tên húy is withheld (§3.6), so it must not be findable by guessing it either.
                Role.maySeeLivingDetails(role));
        Page<PersonSummaryResponse> page = personService.search(criteria, pageable);
        // "Đã mất" is said only of a recorded death; `living = false` alone may mean born over a century ago.
        Set<Long> dead = eventService.findRecordedDead(page.map(PersonSummaryResponse::id).getContent());
        return page.map(person -> person.withDeathRecorded(dead.contains(person.id())));
    }

    /**
     * Works out which people the event-based filters allow, if any were given.
     *
     * @param request the filters to apply
     * @return the ids every event filter agrees on, or null when no event filter was given
     */
    private Set<Long> matchingPersonIds(PersonSearchRequest request) {
        if (!request.touchesEvents()) {
            return null;
        }
        Set<Long> allowed = null;

        if (request.birthYearFrom() != null || request.birthYearTo() != null) {
            allowed = intersect(allowed, eventService.findPersonIdsByEvent(
                    EventType.BIRTH, request.birthYearFrom(), request.birthYearTo(), null));
        }
        if (request.deathYearFrom() != null || request.deathYearTo() != null) {
            allowed = intersect(allowed, eventService.findPersonIdsByEvent(
                    EventType.DEATH, request.deathYearFrom(), request.deathYearTo(), null));
        }
        if (request.placeId() != null) {
            // Any event, not just birth: a residence or a burial answers "ai liên quan tới làng này" too.
            Set<Long> places = placeService.findWithDescendants(request.placeId());
            allowed = intersect(allowed, eventService.findPersonIdsByEvent(null, null, null, places));
        }
        return allowed;
    }

    /**
     * Narrows a running set of ids by another filter's result.
     *
     * @param current the ids allowed so far, or null when nothing has narrowed them yet
     * @param next the ids this filter allows
     * @return the ids both agree on
     */
    private static Set<Long> intersect(Set<Long> current, Set<Long> next) {
        if (current == null) {
            return next;
        }
        Set<Long> both = new HashSet<>(current);
        both.retainAll(next);
        return both;
    }
}
