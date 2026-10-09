package com.genealogy.event.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.Role;
import com.genealogy.event.domain.Event;
import com.genealogy.event.mapper.EventMapper;
import com.genealogy.event.repository.EventRepository;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for hiding a living person's dates from callers below EDITOR (CLAUDE.md §3.6, §4.2). */
@ExtendWith(MockitoExtension.class)
class EventRedactionTest {

    private static final Long PERSON_ID = 3L;
    private static final Long UNION_ID = 7L;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private PersonService personService;

    @Mock
    private PlaceService placeService;

    @Mock
    private FamilyService familyService;

    @Mock
    private GraveService graveService;

    @Mock
    private AuditService auditService;

    private EventServiceImpl events;

    @BeforeEach
    void setUp() {
        EventMapper eventMapper = Mappers.getMapper(EventMapper.class);
        events = new EventServiceImpl(
                eventRepository, eventMapper, auditService, personService, placeService, familyService, graveService);
    }

    /**
     * Builds one birth event for the person under test.
     *
     * @return the entity
     */
    private static Event birth() {
        Event event = new Event();
        event.setId(1L);
        event.setSubjectType(EventSubjectType.PERSON);
        event.setSubjectId(PERSON_ID);
        event.setType(EventType.BIRTH);
        return event;
    }

    /**
     * Builds one marriage event for the union under test.
     *
     * @return the entity
     */
    private static Event marriage() {
        Event event = new Event();
        event.setId(2L);
        event.setSubjectType(EventSubjectType.FAMILY);
        event.setSubjectId(UNION_ID);
        event.setType(EventType.MARRIAGE);
        return event;
    }

    @Test
    @DisplayName("a MEMBER gets no events for a living person, so the redacted record is not undone here")
    void memberGetsNoEventsForALivingPerson() {
        when(personService.isLiving(PERSON_ID)).thenReturn(true);

        assertThat(events.findBySubject(EventSubjectType.PERSON, PERSON_ID, Role.MEMBER)).isEmpty();
        // Redacting the person but serving their birth date from the next endpoint would make §3.6 decorative.
        verify(eventRepository, never())
                .findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(any(), any());
    }

    @Test
    @DisplayName("a MEMBER gets a deceased person's events in full")
    void memberGetsDeceasedPersonEvents() {
        when(personService.isLiving(PERSON_ID)).thenReturn(false);
        when(eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
                        EventSubjectType.PERSON, PERSON_ID))
                .thenReturn(List.of(birth()));

        assertThat(events.findBySubject(EventSubjectType.PERSON, PERSON_ID, Role.MEMBER)).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "EDITOR"})
    @DisplayName("an EDITOR and an ADMIN get a living person's events")
    void editorsGetLivingPersonEvents(Role role) {
        when(eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
                        EventSubjectType.PERSON, PERSON_ID))
                .thenReturn(List.of(birth()));

        assertThat(events.findBySubject(EventSubjectType.PERSON, PERSON_ID, role)).hasSize(1);
        // The living check costs a query, so it must not run for a caller allowed to see everything anyway.
        verify(personService, never()).isLiving(any());
    }

    @Test
    @DisplayName("a MEMBER gets no events for a union of living people, because a wedding date is theirs too")
    void memberGetsNoEventsForALivingCouple() {
        when(familyService.involvesLiving(UNION_ID)).thenReturn(true);

        assertThat(events.findBySubject(EventSubjectType.FAMILY, UNION_ID, Role.MEMBER)).isEmpty();
        // Refused before loading; the guard itself (one living partner is enough) is pinned in FamilyServiceImplTest.
        verify(eventRepository, never())
                .findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(any(), any());
    }

    @Test
    @DisplayName("a MEMBER gets a union's events when both partners are deceased")
    void memberGetsDeceasedCoupleEvents() {
        when(familyService.involvesLiving(UNION_ID)).thenReturn(false);
        when(eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
                        EventSubjectType.FAMILY, UNION_ID))
                .thenReturn(List.of(marriage()));

        assertThat(events.findBySubject(EventSubjectType.FAMILY, UNION_ID, Role.MEMBER)).hasSize(1);
    }

    @Test
    @DisplayName("an unknown union is treated as living, so a bad id cannot be used to probe")
    void unknownUnionFailsClosed() {
        when(familyService.involvesLiving(999L)).thenReturn(true);

        assertThat(events.findBySubject(EventSubjectType.FAMILY, 999L, Role.MEMBER)).isEmpty();
    }

    @Test
    @DisplayName("a MEMBER asking for a living person's event by id is told there is no such event")
    void memberCannotFetchALivingPersonEventById() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(birth()));
        when(personService.isLiving(PERSON_ID)).thenReturn(true);

        // Not a refusal: a 403 would confirm the event exists, which is the fact being withheld.
        assertThatThrownBy(() -> events.getById(1L, Role.MEMBER))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không có sự kiện với id 1");
    }

    @Test
    @DisplayName("a MEMBER may fetch a deceased person's event by id")
    void memberMayFetchADeceasedPersonEventById() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(birth()));
        when(personService.isLiving(PERSON_ID)).thenReturn(false);

        assertThat(events.getById(1L, Role.MEMBER).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("an unknown person is treated as living, so a bad id cannot be used to probe")
    void unknownPersonFailsClosed() {
        // PersonService.isLiving is what fails closed; this pins that the event feature honours it.
        when(personService.isLiving(999L)).thenReturn(true);

        assertThat(events.findBySubject(EventSubjectType.PERSON, 999L, Role.MEMBER)).isEmpty();
    }
}
