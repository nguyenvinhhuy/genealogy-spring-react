package com.genealogy.search.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.branch.service.BranchService;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.Role;
import com.genealogy.event.service.EventService;
import com.genealogy.person.dto.request.PersonSearchCriteria;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import com.genealogy.search.dto.request.PersonSearchRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Unit tests for advanced person search (docs/analysis.md F20). */
@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    private static final Pageable PAGE = PageRequest.of(0, 20);

    @Mock
    private PersonService personService;

    @Mock
    private EventService eventService;

    @Mock
    private PlaceService placeService;

    @Mock
    private BranchService branchService;

    @Captor
    private ArgumentCaptor<PersonSearchCriteria> criteriaCaptor;

    private SearchServiceImpl search;

    @BeforeEach
    void setUp() {
        search = new SearchServiceImpl(personService, eventService, placeService, branchService);
        // An empty page by default: most tests only inspect the criteria passed on, not the rows that come back.
        lenient().when(personService.search(any(), any())).thenReturn(Page.empty(PAGE));
    }

    @Test
    @DisplayName("a row says 'đã mất' only for a recorded death, not for a birth over a century ago")
    void marksOnlyRecordedDeaths() {
        PersonSummaryResponse recorded = new PersonSummaryResponse(1L, "A", Gender.MALE, 1, false, false);
        PersonSummaryResponse presumed = new PersonSummaryResponse(2L, "B", Gender.MALE, 1, false, false);
        when(personService.search(any(), any())).thenReturn(new PageImpl<>(List.of(recorded, presumed), PAGE, 2));
        when(eventService.findRecordedDead(List.of(1L, 2L))).thenReturn(Set.of(1L));

        Page<PersonSummaryResponse> result = search.searchPersons(nameAndGeneration(null, null), Role.MEMBER, PAGE);

        assertThat(result.getContent()).extracting(PersonSummaryResponse::deathRecorded).containsExactly(true, false);
    }

    /**
     * Builds a request with only the person-side filters set.
     *
     * @param query the name search
     * @param generation the đời filter
     * @return the request
     */
    private static PersonSearchRequest nameAndGeneration(String query, Integer generation) {
        return new PersonSearchRequest(query, null, generation, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("filters are passed through together, not one instead of the others")
    void combinesPersonSideFilters() {
        search.searchPersons(nameAndGeneration("Nguyễn", 3), Role.ADMIN, PAGE);

        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        PersonSearchCriteria criteria = criteriaCaptor.getValue();
        assertThat(criteria.query()).isEqualTo("Nguyễn");
        assertThat(criteria.generation()).isEqualTo(3);
        // No date or place filter must mean no id restriction, not a restriction to the empty set.
        assertThat(criteria.restrictToIds()).isNull();
        // No chi filter given, so no branch subtree lookup and no restriction to it either.
        assertThat(criteria.branchIds()).isNull();
        verify(branchService, never()).findWithDescendants(any());
        verify(eventService, never()).findPersonIdsByEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a chi filter matches that chi and every phái/nhánh beneath it")
    void branchFilterIncludesTheWholeSubtree() {
        when(branchService.findWithDescendants(3L)).thenReturn(Set.of(3L, 5L, 8L));

        search.searchPersons(new PersonSearchRequest(null, 3L, null, null, null, null, null, null, null),
                Role.ADMIN, PAGE);

        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().branchIds()).containsExactlyInAnyOrder(3L, 5L, 8L);
    }

    @Test
    @DisplayName("a birth-year range narrows the search to the people born in it")
    void narrowsByBirthYear() {
        when(eventService.findPersonIdsByEvent(EventType.BIRTH, 1890, 1900, null))
                .thenReturn(Set.of(4L, 7L));

        search.searchPersons(
                new PersonSearchRequest(null, null, null, null, 1890, 1900, null, null, null),
                Role.ADMIN, PAGE);

        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().restrictToIds()).containsExactlyInAnyOrder(4L, 7L);
    }

    @Test
    @DisplayName("two event filters intersect rather than widening the result")
    void intersectsEventFilters() {
        when(eventService.findPersonIdsByEvent(EventType.BIRTH, 1890, null, null))
                .thenReturn(Set.of(4L, 7L, 9L));
        when(eventService.findPersonIdsByEvent(EventType.DEATH, null, 1950, null))
                .thenReturn(Set.of(7L, 9L, 11L));

        search.searchPersons(
                new PersonSearchRequest(null, null, null, null, 1890, null, null, 1950, null),
                Role.ADMIN, PAGE);

        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().restrictToIds()).containsExactlyInAnyOrder(7L, 9L);
    }

    @Test
    @DisplayName("a place filter matches everything beneath it in the hierarchy")
    void matchesPlaceDescendants() {
        when(placeService.findWithDescendants(5L)).thenReturn(Set.of(5L, 6L, 7L));
        when(eventService.findPersonIdsByEvent(null, null, null, Set.of(5L, 6L, 7L)))
                .thenReturn(Set.of(2L));

        search.searchPersons(
                new PersonSearchRequest(null, null, null, null, null, null, null, null, 5L),
                Role.ADMIN, PAGE);

        // Filtering by a tỉnh has to match a xã inside it, or the filter fails on the best-entered records.
        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().restrictToIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("an event filter that matches nobody returns nothing, not everybody")
    void emptyEventMatchReturnsNothing() {
        when(eventService.findPersonIdsByEvent(EventType.BIRTH, 3000, null, null)).thenReturn(Set.of());
        when(personService.search(any(), any())).thenReturn(Page.empty(PAGE));

        search.searchPersons(
                new PersonSearchRequest(null, null, null, null, 3000, null, null, null, null),
                Role.ADMIN, PAGE);

        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().restrictToIds()).isEmpty();
    }

    @Test
    @DisplayName("a MEMBER using a date filter only ever matches the deceased")
    void dateFilterCannotLeakLivingBirthYears() {
        when(eventService.findPersonIdsByEvent(EventType.BIRTH, 1990, 1995, null))
                .thenReturn(Set.of(4L));

        search.searchPersons(
                new PersonSearchRequest(null, null, null, null, 1990, 1995, null, null, null),
                Role.MEMBER, PAGE);

        // Or a MEMBER recovers a hidden birth year by elimination: ask 1990-1995, see who comes back.
        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().living()).isFalse();
    }

    @Test
    @DisplayName("a MEMBER asking for living people by date gets nothing at all")
    void memberCannotCombineLivingWithADateFilter() {
        Page<?> result = search.searchPersons(
                new PersonSearchRequest(null, null, null, true, 1990, null, null, null, null),
                Role.MEMBER, PAGE);

        assertThat(result).isEmpty();
        verify(personService, never()).search(any(), any());
        verify(eventService, never()).findPersonIdsByEvent(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "EDITOR"})
    @DisplayName("an EDITOR and an ADMIN may filter the living by date")
    void editorsMayFilterLivingByDate(Role role) {
        when(eventService.findPersonIdsByEvent(EventType.BIRTH, 1990, null, null)).thenReturn(Set.of(4L));

        search.searchPersons(
                new PersonSearchRequest(null, null, null, true, 1990, null, null, null, null),
                role, PAGE);

        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().living()).isTrue();
    }

    @Test
    @DisplayName("a MEMBER may still filter by living status when no date is involved")
    void memberMayFilterByLivingStatusAlone() {
        search.searchPersons(
                new PersonSearchRequest(null, null, null, true, null, null, null, null, null),
                Role.MEMBER, PAGE);

        // The list already shows who is living; it is their dates that are hidden, not their existence.
        verify(personService).search(criteriaCaptor.capture(), eq(PAGE));
        assertThat(criteriaCaptor.getValue().living()).isTrue();
    }
}
