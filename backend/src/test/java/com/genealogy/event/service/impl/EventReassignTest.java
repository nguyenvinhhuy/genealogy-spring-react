package com.genealogy.event.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.event.domain.Event;
import com.genealogy.event.mapper.EventMapper;
import com.genealogy.event.repository.EventRepository;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for moving one person's events onto another during a merge (CLAUDE.md §3.4, F11). */
@ExtendWith(MockitoExtension.class)
class EventReassignTest {

    private static final Long DUPLICATE = 11L;
    private static final Long KEEP = 10L;
    private static final Long ACTOR = 9L;
    private static final String REASON = "Cùng một cụ";

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
     * Builds one event of the duplicate's.
     *
     * @param type what the event records
     * @return the entity
     */
    private static Event event(EventType type) {
        Event entity = new Event();
        entity.setId(1L);
        entity.setSubjectType(EventSubjectType.PERSON);
        entity.setSubjectId(DUPLICATE);
        entity.setType(type);
        return entity;
    }

    @Test
    @DisplayName("absorbing a death event marks the survivor as no longer living")
    void recomputesLivingAfterMovingADeath() {
        Event death = event(EventType.DEATH);
        when(eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
                        EventSubjectType.PERSON, DUPLICATE))
                .thenReturn(List.of(death));
        when(eventRepository.findBySubjectTypeAndSubjectIdAndTypeIn(eq(EventSubjectType.PERSON), eq(KEEP), anyList()))
                .thenReturn(List.of(death));

        int moved = events.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        assertThat(moved).isEqualTo(1);
        // Without this the survivor keeps living = true and §3.6 hides a cụ who died, from every con cháu.
        verify(personService).applyLiving(KEEP, false);
        // A merge that moved a ngày mất left no revision until 2026-09-24 (§3.8).
        verify(auditService)
                .record(eq(AuditEntityType.EVENT), eq(1L), eq(AuditAction.UPDATE), any(), any(), eq(ACTOR), eq(REASON));
    }

    @Test
    @DisplayName("forgetting a deleted subject's events writes a DELETE revision for each one")
    void forgettingEventsRecordsEachDeletion() {
        when(eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(EventSubjectType.PERSON, DUPLICATE))
                .thenReturn(List.of(event(EventType.DEATH)));

        assertThat(events.forgetSubject(EventSubjectType.PERSON, DUPLICATE, ACTOR, REASON)).isEqualTo(1);

        verify(auditService).record(
                eq(AuditEntityType.EVENT), eq(1L), eq(AuditAction.DELETE), any(), isNull(), eq(ACTOR), eq(REASON));
    }

    @Test
    @DisplayName("absorbing a birth from over a century ago also settles the living flag")
    void recomputesLivingFromAnAbsorbedBirth() {
        Event birth = event(EventType.BIRTH);
        birth.getDate().setYear(1890);
        when(eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
                        EventSubjectType.PERSON, DUPLICATE))
                .thenReturn(List.of(birth));
        when(eventRepository.findBySubjectTypeAndSubjectIdAndTypeIn(eq(EventSubjectType.PERSON), eq(KEEP), anyList()))
                .thenReturn(List.of(birth));

        events.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        verify(personService).applyLiving(KEEP, false);
    }

    @Test
    @DisplayName("a duplicate with no events leaves the survivor's living flag alone")
    void leavesLivingAloneWhenNothingMoved() {
        when(eventRepository.findBySubjectTypeAndSubjectIdOrderByDateSortDateAsc(
                        EventSubjectType.PERSON, DUPLICATE))
                .thenReturn(List.of());

        assertThat(events.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON)).isZero();
        verify(personService, never()).applyLiving(anyLong(), anyBoolean());
    }
}
