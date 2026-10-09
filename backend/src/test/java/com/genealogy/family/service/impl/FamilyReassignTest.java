package com.genealogy.family.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.RelationType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.family.domain.Family;
import com.genealogy.family.domain.FamilyChild;
import com.genealogy.family.dto.response.ReassignResult;
import com.genealogy.family.mapper.FamilyMapper;
import com.genealogy.family.repository.FamilyChildRepository;
import com.genealogy.family.repository.FamilyRepository;
import com.genealogy.person.service.PersonService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for moving unions and parentage during a merge (docs/analysis.md F11). */
@ExtendWith(MockitoExtension.class)
class FamilyReassignTest {

    private static final Long KEEP = 1L;
    private static final Long DUPLICATE = 2L;
    private static final Long ACTOR = 9L;
    private static final String REASON = "Cùng một cụ";

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private FamilyChildRepository familyChildRepository;

    @Mock
    private PersonService personService;

    @Mock
    private AuditService auditService;

    @Mock
    private AdvisoryLock advisoryLock;

    private FamilyServiceImpl familyService;

    private final List<Family> families = new ArrayList<>();
    private final List<FamilyChild> links = new ArrayList<>();

    @BeforeEach
    void setUp() {
        FamilyMapper familyMapper = Mappers.getMapper(FamilyMapper.class);
        familyService = new FamilyServiceImpl(
                familyRepository, familyChildRepository, familyMapper, auditService, personService, advisoryLock);

        lenient().when(familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(anyLong(), anyLong()))
                .thenAnswer(call -> {
                    Long id = call.getArgument(0);
                    return families.stream()
                            .filter(f -> id.equals(f.getPartner1Id()) || id.equals(f.getPartner2Id()))
                            .toList();
                });
        lenient().when(familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(anyLong()))
                .thenAnswer(call -> links.stream()
                        .filter(link -> link.getFamilyId().equals(call.getArgument(0)))
                        .toList());
        lenient().when(familyChildRepository.findByChildId(anyLong()))
                .thenAnswer(call -> links.stream()
                        .filter(link -> link.getChildId().equals(call.getArgument(0)))
                        .toList());
        lenient().when(familyChildRepository.findByFamilyIdAndChildId(anyLong(), anyLong()))
                .thenAnswer(call -> links.stream()
                        .filter(link -> link.getFamilyId().equals(call.getArgument(0))
                                && link.getChildId().equals(call.getArgument(1)))
                        .findFirst());
        // The ancestry walk loads the whole graph in two queries rather than one per node.
        lenient().when(familyRepository.findAllPartners()).thenAnswer(call -> GraphRows.partners(families));
        lenient().when(familyChildRepository.findAllLinks()).thenAnswer(call -> GraphRows.links(links));
        lenient().when(familyRepository.findById(anyLong()))
                .thenAnswer(call -> families.stream()
                        .filter(f -> f.getId().equals(call.getArgument(0)))
                        .findFirst());
        lenient().doAnswer(call -> families.remove(call.getArgument(0)))
                .when(familyRepository).delete(any(Family.class));
        lenient().doAnswer(call -> links.remove(call.getArgument(0)))
                .when(familyChildRepository).delete(any(FamilyChild.class));
    }

    /**
     * Adds a union to the fixture.
     *
     * @param id family id
     * @param partner1 first partner, may be null
     * @param partner2 second partner, may be null
     * @return the union
     */
    private Family union(Long id, Long partner1, Long partner2) {
        Family family = new Family();
        family.setId(id);
        family.setPartner1Id(partner1);
        family.setPartner2Id(partner2);
        family.setStatus(FamilyStatus.MARRIED);
        family.setOrderIndex(0);
        families.add(family);
        return family;
    }

    /**
     * Adds a parentage link to the fixture.
     *
     * @param familyId the union
     * @param childId the child
     */
    private void child(Long familyId, Long childId) {
        FamilyChild link = new FamilyChild();
        link.setId((long) (links.size() + 1));
        link.setFamilyId(familyId);
        link.setChildId(childId);
        link.setRelationToP1(RelationType.BIRTH);
        link.setRelationToP2(RelationType.BIRTH);
        links.add(link);
    }

    @Test
    @DisplayName("a union naming the duplicate is repointed at the survivor")
    void repointsPartner() {
        Family family = union(10L, DUPLICATE, 9L);

        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        assertThat(family.getPartner1Id()).isEqualTo(KEEP);
        assertThat(result.unionsMoved()).isEqualTo(1);
    }

    @Test
    @DisplayName("two unions with the same spouse fold into one, and the children come across")
    void collapsesDuplicateUnions() {
        union(10L, KEEP, 9L);
        union(11L, DUPLICATE, 9L);
        child(10L, 20L);
        child(11L, 21L);

        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        assertThat(result.unionsCollapsed()).isEqualTo(1);
        assertThat(families).hasSize(1);
        // The child of the folded union has to land in the surviving one, or the merge loses a person.
        assertThat(links).extracting(FamilyChild::getFamilyId).containsOnly(10L);
        assertThat(links).extracting(FamilyChild::getChildId).containsExactlyInAnyOrder(20L, 21L);
    }

    @Test
    @DisplayName("the duplicate's own divorce and remarriage to one spouse are not folded into one marriage")
    void keepsTheDuplicatesOwnRemarriage() {
        union(11L, DUPLICATE, 9L);
        union(12L, DUPLICATE, 9L);

        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        // The survivor has no union with spouse 9, so nothing here is evidence of a duplicate marriage (§8.12 #5).
        assertThat(result.unionsCollapsed()).isZero();
        assertThat(families).hasSize(2);
    }

    @Test
    @DisplayName("a merge writes a revision for each union it deletes or changes, with the merge's reason")
    void mergeRecordsEveryUnionItTouches() {
        union(10L, KEEP, 9L);
        union(11L, DUPLICATE, 9L);
        union(12L, 30L, 31L);
        child(10L, 20L);
        child(11L, 21L);

        familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        // Unaudited until 2026-09-24: a merge could fold a marriage away and the trail said nothing (§3.8).
        verify(auditService).record(
                eq(AuditEntityType.FAMILY), eq(11L), eq(AuditAction.DELETE), any(), isNull(), eq(ACTOR), eq(REASON));
        verify(auditService).record(
                eq(AuditEntityType.FAMILY), eq(10L), eq(AuditAction.UPDATE), any(), any(), eq(ACTOR), eq(REASON));
        verify(auditService, never()).record(any(), eq(12L), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a child recorded under both is kept once, not twice")
    void dropsDuplicateChildLink() {
        union(10L, KEEP, 9L);
        union(11L, DUPLICATE, 9L);
        child(10L, 20L);
        child(11L, 20L);

        familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        // uq_family_children is UNIQUE (family_id, child_id), so a second row would fail on flush.
        assertThat(links).hasSize(1);
    }

    @Test
    @DisplayName("a child link on the duplicate is repointed at the survivor")
    void repointsChildLink() {
        union(10L, 8L, 9L);
        child(10L, DUPLICATE);

        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        assertThat(result.childLinksMoved()).isEqualTo(1);
        assertThat(links.getFirst().getChildId()).isEqualTo(KEEP);
    }

    @Test
    @DisplayName("a union recording the two as married to each other is dropped, and says so")
    void dropsUnionBetweenTheTwo() {
        union(10L, KEEP, DUPLICATE);
        child(10L, 20L);

        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        // Repointing trips ck_families_distinct_partners, and a merge may not guess which is wrong.
        assertThat(families).isEmpty();
        assertThat(result.notes()).hasSize(1);
        assertThat(result.notes().getFirst()).contains("vợ chồng của nhau");
    }

    @Test
    @DisplayName("a link making the survivor their own child is dropped rather than written")
    void dropsSelfParentLink() {
        union(10L, KEEP, 9L);
        child(10L, DUPLICATE);

        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        assertThat(links).isEmpty();
        assertThat(result.childLinksMoved()).isZero();
        assertThat(result.notes().getFirst()).contains("cha/mẹ của hôn nhân");
    }

    @Test
    @DisplayName("a union with an unrecorded spouse is not folded into another")
    void keepsUnionsWithUnknownSpouse() {
        union(10L, KEEP, null);
        union(11L, DUPLICATE, null);

        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        // Two unknown spouses are not evidence of one union; folding them would invent a marriage.
        assertThat(result.unionsCollapsed()).isZero();
        assertThat(families).hasSize(2);
    }

    @Test
    @DisplayName("a line of descent is found whichever of the two is asked about first, and nowhere else")
    void reportsLineOfDescentEitherWay() {
        union(10L, 5L, 6L);
        child(10L, 7L);
        union(11L, 8L, null);

        assertThat(familyService.inOneLineOfDescent(5L, 7L)).isTrue();
        assertThat(familyService.inOneLineOfDescent(7L, 5L)).isTrue();
        // A spouse is not a line of descent, and neither is a person with themselves.
        assertThat(familyService.inOneLineOfDescent(5L, 6L)).isFalse();
        assertThat(familyService.inOneLineOfDescent(5L, 5L)).isFalse();
        assertThat(familyService.inOneLineOfDescent(7L, 8L)).isFalse();
    }

    @Test
    @DisplayName("nothing to move is not an error")
    void handlesNothingToMove() {
        ReassignResult result = familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);

        assertThat(result.unionsMoved()).isZero();
        assertThat(result.childLinksMoved()).isZero();
        assertThat(result.notes()).isEmpty();
        assertThat(Optional.ofNullable(result.notes())).isPresent();
    }
}
