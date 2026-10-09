package com.genealogy.event.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.event.domain.Event;
import com.genealogy.event.mapper.EventMapper;
import com.genealogy.event.repository.EventRepository;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveChanged;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for a recorded mộ and a cải táng counting as a recorded death (§3.4, §8.8 D2). */
@ExtendWith(MockitoExtension.class)
class EventGraveLivingTest {

    private static final Long PERSON = 42L;

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
        events = new EventServiceImpl(eventRepository, Mappers.getMapper(EventMapper.class), auditService,
                personService, placeService, familyService, graveService);
    }

    @Test
    @DisplayName("recording a mộ for someone with no death event makes them no longer living")
    void aRecordedGraveIsADeath() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(eventRepository.findBySubjectTypeAndSubjectIdAndTypeIn(any(), any(), any())).thenReturn(List.of());
        when(graveService.findBuried(List.of(PERSON))).thenReturn(Set.of(PERSON));

        events.onGraveChanged(new GraveChanged(PERSON));

        verify(personService).applyLiving(PERSON, false);
    }

    @Test
    @DisplayName("a sinh phần counts for nothing: a person with only a living plot and no events stays living")
    void aLivingPlotIsNotADeath() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(eventRepository.findBySubjectTypeAndSubjectIdAndTypeIn(any(), any(), any())).thenReturn(List.of());
        // findBuried leaves a LIVING_PLOT out, so it answers with nobody.
        when(graveService.findBuried(List.of(PERSON))).thenReturn(Set.of());

        events.onGraveChanged(new GraveChanged(PERSON));

        // No birth at all fails closed as living (§3.4).
        verify(personService).applyLiving(PERSON, true);
    }

    @Test
    @DisplayName("a grave change for a person already gone recomputes nothing")
    void ignoresAGoneOwner() {
        when(personService.exists(PERSON)).thenReturn(false);

        events.onGraveChanged(new GraveChanged(PERSON));

        verify(personService, never()).applyLiving(any(), anyBoolean());
    }

    @Test
    @DisplayName("a cải táng alone marks a person dead, like a burial does")
    void aReburialIsADeath() {
        Event reburial = new Event();
        reburial.setSubjectType(EventSubjectType.PERSON);
        reburial.setSubjectId(PERSON);
        reburial.setType(EventType.REBURIAL);
        when(personService.exists(PERSON)).thenReturn(true);
        when(eventRepository.findBySubjectTypeAndSubjectIdAndTypeIn(any(), any(), any()))
                .thenReturn(List.of(reburial));

        events.onGraveChanged(new GraveChanged(PERSON));

        verify(personService).applyLiving(PERSON, false);
    }

    @Test
    @DisplayName("the nightly sweep reads every recorded mộ once, not once per person")
    void sweepCountsGraves() {
        when(eventRepository.findPersonEventsOfTypes(any())).thenReturn(List.of());
        when(graveService.findAllBuried()).thenReturn(Set.of(PERSON));
        when(personService.findAllLiving()).thenReturn(Map.of(PERSON, true, 43L, true));

        int changed = events.recomputeAllLiving();

        assertThat(changed).isEqualTo(1);
        verify(personService).applyLiving(PERSON, false);
        verify(personService, never()).applyLiving(43L, false);
    }

    @Test
    @DisplayName("† follows a recorded mộ as well as a death event, so the tree does not draw a buried cụ as alive")
    void recordedDeadIncludesGraves() {
        when(eventRepository.findPersonIdsWithEventTypes(any(), any())).thenReturn(List.of(1L));
        when(graveService.findBuried(List.of(1L, 2L, 3L))).thenReturn(Set.of(2L));

        assertThat(events.findRecordedDead(List.of(1L, 2L, 3L))).containsExactlyInAnyOrder(1L, 2L);
    }
}
