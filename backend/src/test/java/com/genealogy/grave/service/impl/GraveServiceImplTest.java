package com.genealogy.grave.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.GraveKind;
import com.genealogy.common.model.Role;
import com.genealogy.grave.domain.Grave;
import com.genealogy.grave.dto.request.GraveRequest;
import com.genealogy.grave.dto.response.DroppedGrave;
import com.genealogy.grave.dto.response.GraveSaved;
import com.genealogy.grave.mapper.GraveMapper;
import com.genealogy.grave.repository.GraveRepository;
import com.genealogy.grave.service.GraveChanged;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Unit tests for mộ phần: redaction per role, the audit trail, and the living recompute it triggers (§3.6, §8.8). */
@ExtendWith(MockitoExtension.class)
class GraveServiceImplTest {

    private static final Long PERSON = 42L;
    private static final Long ACTOR = 9L;

    @Mock
    private GraveRepository graveRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private PersonService personService;

    @Mock
    private PlaceService placeService;

    private GraveServiceImpl graves;

    @BeforeEach
    void setUp() {
        graves = new GraveServiceImpl(graveRepository, Mappers.getMapper(GraveMapper.class), auditService, events,
                personService, placeService);
    }

    /**
     * Builds a saved grave entity.
     *
     * @param id grave id
     * @param personId whose grave it is
     * @param kind a mộ or a sinh phần
     * @return the entity
     */
    private static Grave grave(Long id, Long personId, GraveKind kind) {
        Grave grave = new Grave();
        grave.setId(id);
        grave.setPersonId(personId);
        grave.setKind(kind);
        grave.setPlot("khu B, hàng 3");
        grave.setPlaceId(5L);
        return grave;
    }

    /**
     * Builds a request that sets every field.
     *
     * @param version the version the form was loaded at, or null
     * @return the request
     */
    private static GraveRequest request(Long version) {
        return new GraveRequest(GraveKind.GRAVE, 5L, "khu B", new BigDecimal("21.02"), new BigDecimal("105.8"),
                null, "vì sao", version);
    }

    @Test
    @DisplayName("a MEMBER gets nothing for a living person's plot, and one 404 for hidden and for none")
    void hidesALivingOwnersGraveFromAMember() {
        when(personService.isLiving(PERSON)).thenReturn(true);

        assertThat(graves.findByPerson(PERSON, Role.MEMBER)).isEmpty();
        // The same 404 as "none recorded", so a refusal cannot confirm that a sinh phần exists.
        assertThatThrownBy(() -> graves.getByPerson(PERSON, Role.MEMBER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Chưa ghi mộ phần cho người này");
        verify(graveRepository, never()).findByPersonId(any());
    }

    @Test
    @DisplayName("an EDITOR gets a living person's plot, and the living guard is never asked")
    void showsALivingOwnersGraveToAnEditor() {
        when(graveRepository.findByPersonId(PERSON)).thenReturn(Optional.of(grave(4L, PERSON, GraveKind.LIVING_PLOT)));

        assertThat(graves.findByPerson(PERSON, Role.EDITOR)).hasValueSatisfying(found -> {
            assertThat(found.kind()).isEqualTo(GraveKind.LIVING_PLOT);
            assertThat(found.placeId()).isEqualTo(5L);
        });
        verify(personService, never()).isLiving(any());
    }

    @Test
    @DisplayName("the map list hands a MEMBER only graves whose owner is known to be dead")
    void mapListFailsClosedForAMember() {
        when(graveRepository.findByLatitudeIsNotNull()).thenReturn(List.of(
                grave(1L, 10L, GraveKind.GRAVE),
                grave(2L, 11L, GraveKind.LIVING_PLOT),
                grave(3L, 12L, GraveKind.GRAVE)));
        // 12 is missing from the map, which must read as living, not as dead.
        when(personService.findAllLiving()).thenReturn(Map.of(10L, false, 11L, true));

        assertThat(graves.findLocated(Role.MEMBER)).extracting(found -> found.id()).containsExactly(1L);
    }

    @Test
    @DisplayName("recording a grave for the first time is a create, audited, and it asks for `living` to be redone")
    void recordsANewGrave() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(graveRepository.findByPersonId(PERSON)).thenReturn(Optional.empty());
        when(graveRepository.saveAndFlush(any(Grave.class))).thenAnswer(call -> {
            Grave saved = call.getArgument(0);
            saved.setId(4L);
            return saved;
        });

        GraveSaved saved = graves.save(PERSON, request(null), ACTOR);

        assertThat(saved.created()).isTrue();
        // The grave's place is kept: until 2026-09-28 every save from the UI erased it (§8.8 #2).
        assertThat(saved.grave().placeId()).isEqualTo(5L);
        verify(placeService).requireExists(5L);
        verify(auditService).record(AuditEntityType.GRAVE, 4L, AuditAction.CREATE, null, saved.grave(), ACTOR,
                "vì sao");
        // A mộ is a recorded death, so the owner's `living` may just have changed (§8.8 D2).
        verify(events).publishEvent(new GraveChanged(PERSON));
    }

    @Test
    @DisplayName("an edit made from a stale form is refused rather than overwriting the newer one")
    void refusesAStaleEdit() {
        Grave existing = grave(4L, PERSON, GraveKind.GRAVE);
        existing.setVersion(3);
        when(personService.exists(PERSON)).thenReturn(true);
        when(graveRepository.findByPersonId(PERSON)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> graves.save(PERSON, request(2L), ACTOR)).isInstanceOf(ConflictException.class);
        verify(graveRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an edit of a grave somebody deleted meanwhile is refused, not quietly recreated")
    void refusesToRecreateADeletedGrave() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(graveRepository.findByPersonId(PERSON)).thenReturn(Optional.empty());

        // A version says the form was an edit; saving it recreated the grave and re-marked its owner dead (§8.10 #9).
        assertThatThrownBy(() -> graves.save(PERSON, request(3L), ACTOR)).isInstanceOf(ConflictException.class);
        verify(graveRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a lone coordinate is refused in Vietnamese before the database constraint trips")
    void refusesALoneCoordinate() {
        when(personService.exists(PERSON)).thenReturn(true);
        GraveRequest lone = new GraveRequest(null, null, null, new BigDecimal("21.02"), null, null, null, null);

        assertThatThrownBy(() -> graves.save(PERSON, lone, ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vĩ độ");
    }

    @Test
    @DisplayName("a merge that finds a grave on both people drops the duplicate's and describes it in full")
    void mergeDropsAndDescribesTheDuplicatesGrave() {
        Grave duplicate = grave(4L, 1L, GraveKind.GRAVE);
        when(graveRepository.findByPersonId(1L)).thenReturn(Optional.of(duplicate));
        when(graveRepository.findByPersonId(PERSON)).thenReturn(Optional.of(grave(5L, PERSON, GraveKind.GRAVE)));

        Optional<DroppedGrave> dropped = graves.reassignPerson(1L, PERSON, ACTOR, "trùng");

        assertThat(dropped).hasValueSatisfying(found -> {
            assertThat(found.graveId()).isEqualTo(4L);
            // Plot and place both, because cải táng may make the dropped one the right one (§8 F11).
            assertThat(found.note()).contains("khu B, hàng 3").contains("#5");
        });
        verify(graveRepository).delete(duplicate);
        verify(auditService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an unknown grave counts as a living owner's, so every guard fails closed")
    void unknownGraveFailsClosed() {
        when(graveRepository.findById(77L)).thenReturn(Optional.empty());

        assertThat(graves.involvesLiving(77L)).isTrue();
    }
}
