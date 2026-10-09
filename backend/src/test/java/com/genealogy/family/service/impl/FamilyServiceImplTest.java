package com.genealogy.family.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.model.RelationType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.family.domain.Family;
import com.genealogy.family.domain.FamilyChild;
import com.genealogy.family.dto.request.AddRelationRequest;
import com.genealogy.family.dto.request.FamilyChildRequest;
import com.genealogy.family.dto.request.FamilyChildUpdateRequest;
import com.genealogy.family.dto.request.FamilyRequest;
import com.genealogy.family.dto.request.LinkRelationRequest;
import com.genealogy.family.dto.response.AddRelationResponse;
import com.genealogy.family.mapper.FamilyMapper;
import com.genealogy.family.repository.FamilyChildRepository;
import com.genealogy.family.repository.FamilyRepository;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.service.PersonService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for generation recompute and ancestry-cycle rejection (CLAUDE.md 4.2). */
@ExtendWith(MockitoExtension.class)
class FamilyServiceImplTest {

    private static final Long ACTOR = 9L;

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

    @Captor
    private ArgumentCaptor<Map<Long, Integer>> generationsCaptor;

    private FamilyServiceImpl familyService;

    private final List<Family> families = new ArrayList<>();
    private final List<FamilyChild> links = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // The real MapStruct mapper is used so mapping is exercised rather than stubbed (CLAUDE.md 4.1).
        FamilyMapper familyMapper = Mappers.getMapper(FamilyMapper.class);
        familyService = new FamilyServiceImpl(
                familyRepository, familyChildRepository, familyMapper, auditService, personService, advisoryLock);

        lenient().when(familyRepository.findAllPartners()).thenAnswer(call -> GraphRows.partners(families));
        lenient().when(familyChildRepository.findAllLinks()).thenAnswer(call -> GraphRows.links(links));
        lenient().when(familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(anyLong()))
                .thenAnswer(call -> links.stream()
                        .filter(link -> link.getFamilyId().equals(call.getArgument(0)))
                        .toList());
        lenient().when(familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(anyLong(), anyLong()))
                .thenAnswer(call -> {
                    Long personId = call.getArgument(0);
                    return families.stream()
                            .filter(family -> personId.equals(family.getPartner1Id())
                                    || personId.equals(family.getPartner2Id()))
                            .toList();
                });
    }

    /**
     * Registers a union in the fake repository.
     *
     * @param id family id
     * @param partner1Id first partner, may be null
     * @param partner2Id second partner, may be null
     * @return the union
     */
    private Family family(long id, Long partner1Id, Long partner2Id) {
        Family family = new Family();
        family.setId(id);
        family.setPartner1Id(partner1Id);
        family.setPartner2Id(partner2Id);
        families.add(family);
        lenient().when(familyRepository.findById(id)).thenReturn(Optional.of(family));
        return family;
    }

    /**
     * Registers a child link in the fake repository.
     *
     * @param familyId the union
     * @param childId the child
     */
    private void child(long familyId, long childId) {
        FamilyChild link = new FamilyChild();
        link.setFamilyId(familyId);
        link.setChildId(childId);
        links.add(link);
    }

    /**
     * Runs the recompute and returns what was written.
     *
     * @return the generation of each person
     */
    private Map<Long, Integer> recompute() {
        familyService.recomputeGenerations();
        verify(personService).applyGenerations(generationsCaptor.capture());
        return generationsCaptor.getValue();
    }

    @Test
    @DisplayName("a straight line of descent numbers the generations 1, 2, 3")
    void straightLineOfDescent() {
        family(10, 1L, 2L);
        child(10, 3L);
        family(11, 3L, 4L);
        child(11, 5L);

        Map<Long, Integer> generations = recompute();

        assertThat(generations).containsEntry(1L, 1).containsEntry(2L, 1);
        assertThat(generations).containsEntry(3L, 2);
        assertThat(generations).containsEntry(5L, 3);
    }

    @Test
    @DisplayName("a con dâu with no recorded parents sits at her husband's generation, not at đời 1")
    void marriedInSpouseTakesPartnerGeneration() {
        family(10, 1L, 2L);
        child(10, 3L);
        // Person 4 is the daughter-in-law: she appears only as a partner, with no parents in this clan.
        family(11, 3L, 4L);
        child(11, 5L);

        Map<Long, Integer> generations = recompute();

        assertThat(generations).containsEntry(4L, 2);
    }

    @Test
    @DisplayName("a child sits below its deepest parent, not its shallowest")
    void childSitsBelowDeepestParent() {
        family(10, 1L, 2L);
        child(10, 3L);
        family(11, 3L, 4L);
        child(11, 5L);
        // Person 5 (đời 3) has a child with person 6, who married in at that same union.
        family(12, 5L, 6L);
        child(12, 7L);

        Map<Long, Integer> generations = recompute();

        assertThat(generations).containsEntry(6L, 3);
        assertThat(generations).containsEntry(7L, 4);
    }

    @Test
    @DisplayName("an isolated union with no children still numbers both partners đời 1")
    void isolatedUnion() {
        family(10, 1L, 2L);

        assertThat(recompute()).containsEntry(1L, 1).containsEntry(2L, 1);
    }

    @Test
    @DisplayName("linking a child whose descendant is a partner of that union is rejected")
    void ancestryCycleIsRejected() {
        // 1 -> 3 -> 5. Trying to make 1 a child of 5's union would close the loop.
        family(10, 1L, 2L);
        child(10, 3L);
        family(11, 3L, null);
        child(11, 5L);
        Family loopFamily = family(12, 5L, null);

        when(personService.exists(1L)).thenReturn(true);
        when(familyChildRepository.findByFamilyIdAndChildId(12L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyService.addChild(
                        loopFamily.getId(), new FamilyChildRequest(1L, null, null, null), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tổ tiên của chính mình");

        verify(familyChildRepository, never()).save(any(FamilyChild.class));
    }

    @Test
    @DisplayName("editing a union to name one of its children's descendants as a partner is rejected")
    void ancestryCycleThroughAnEditIsRejected() {
        // 1 -> 3 -> 5. Making 5 the second partner of 1's union makes 5 their own grandparent.
        family(10, 1L, null);
        child(10, 3L);
        family(11, 3L, null);
        child(11, 5L);

        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(5L)).thenReturn(true);

        assertThatThrownBy(() -> familyService.update(10L, request(1L, 5L), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tổ tiên của chính mình");

        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("editing a union's partners is allowed when no child's line leads back to them")
    void harmlessPartnerEditIsAccepted() {
        family(10, 1L, null);
        child(10, 3L);

        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(2L)).thenReturn(true);

        familyService.update(10L, request(1L, 2L), ACTOR);

        verify(auditService).record(
                eq(AuditEntityType.FAMILY), eq(10L), eq(AuditAction.UPDATE), any(), any(), eq(ACTOR), isNull());
    }

    @Test
    @DisplayName("an edit made from an older version of the union is refused rather than overwriting the newer one")
    void staleEditIsRefused() {
        Family union = family(10, 1L, null);
        union.setVersion(3);
        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(2L)).thenReturn(true);

        FamilyRequest stale = new FamilyRequest(1L, 2L, null, null, null, 2L);

        assertThatThrownBy(() -> familyService.update(10L, stale, ACTOR)).isInstanceOf(ConflictException.class);
        assertThat(union.getPartner2Id()).isNull();
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the parentage lock is taken before the graph the cycle check reads is loaded")
    void lockIsTakenBeforeTheCycleCheckReads() {
        Family target = family(10, 1L, 2L);
        when(personService.exists(3L)).thenReturn(true);
        when(familyChildRepository.findByFamilyIdAndChildId(10L, 3L)).thenReturn(Optional.empty());

        familyService.addChild(target.getId(), new FamilyChildRequest(3L, null, null, null), ACTOR);

        // Taken after the read, two editors would each pass the check on the same snapshot and close a loop.
        InOrder order = inOrder(advisoryLock, familyChildRepository);
        order.verify(advisoryLock).lock(AdvisoryLock.PARENTAGE);
        order.verify(familyChildRepository).findAllLinks();
    }

    @Test
    @DisplayName("dropping a person's partnerless unions returns their ids and writes a DELETE revision for each")
    void droppedUnionsAreReportedAndAudited() {
        family(10, 1L, null);
        family(11, 1L, 2L);

        List<Long> dropped = familyService.dropUnionsWithOnlyThisPartner(1L, ACTOR, "trùng");

        assertThat(dropped).containsExactly(10L);
        verify(auditService).record(
                eq(AuditEntityType.FAMILY), eq(10L), eq(AuditAction.DELETE), any(), isNull(), eq(ACTOR), eq("trùng"));
    }

    @Test
    @DisplayName("a person cannot be linked as a child of their own union")
    void personCannotBeTheirOwnParent() {
        Family target = family(10, 1L, 2L);
        when(personService.exists(1L)).thenReturn(true);

        assertThatThrownBy(() -> familyService.addChild(
                        target.getId(), new FamilyChildRequest(1L, null, null, null), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cha/mẹ của chính mình");
    }

    @Test
    @DisplayName("the same child cannot be linked into one union twice")
    void duplicateChildLinkIsRejected() {
        Family target = family(10, 1L, 2L);
        child(10, 3L);

        when(personService.exists(3L)).thenReturn(true);
        when(familyChildRepository.findByFamilyIdAndChildId(10L, 3L))
                .thenReturn(Optional.of(links.getFirst()));

        assertThatThrownBy(() -> familyService.addChild(
                        target.getId(), new FamilyChildRequest(3L, null, null, null), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã được ghi là con");
    }

    @Test
    @DisplayName("creating a union writes a revision naming who did it")
    void createWritesARevision() {
        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(2L)).thenReturn(true);
        when(familyRepository.save(any(Family.class))).thenAnswer(call -> {
            Family saved = call.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        familyService.create(request(1L, 2L), ACTOR);

        // Unaudited until 2026-09-18: a ngày cưới could be changed with nothing recording who or when (§3.8).
        verify(auditService).record(
                eq(AuditEntityType.FAMILY), eq(10L), eq(AuditAction.CREATE), isNull(), any(), eq(ACTOR), isNull());
    }

    @Test
    @DisplayName("a union needs at least one partner")
    void unionNeedsAPartner() {
        assertThatThrownBy(() -> familyService.create(request(null, null), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ít nhất một người");
    }

    @Test
    @DisplayName("a person cannot be their own partner")
    void personCannotBeTheirOwnPartner() {
        assertThatThrownBy(() -> familyService.create(request(1L, 1L), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vợ/chồng của chính mình");
    }

    @Test
    @DisplayName("adding a spouse creates the person and a union placed after the person's existing ones")
    void addSpouseCreatesPersonAndNextUnion() {
        family(10, 1L, 2L);
        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(50L)).thenReturn(true);
        when(personService.create(any(), eq(ACTOR))).thenReturn(created(50L));
        when(familyRepository.save(any(Family.class))).thenAnswer(call -> {
            Family saved = call.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        AddRelationResponse response =
                familyService.addRelation(1L, relation(AddRelationRequest.Kind.SPOUSE, null), ACTOR);

        assertThat(response.personId()).isEqualTo(50L);
        assertThat(response.family().partner1Id()).isEqualTo(1L);
        assertThat(response.family().partner2Id()).isEqualTo(50L);
        // Decided on the server: the client counted a union list that could still be loading.
        assertThat(response.family().orderIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("a new spouse or child defaults to the anchor person's chi, still editable afterward")
    void addRelationInheritsTheAnchorsBranch() {
        family(10, 1L, 2L);
        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(50L)).thenReturn(true);
        when(personService.branchIdOf(1L)).thenReturn(7L);
        when(personService.create(any(), eq(ACTOR))).thenReturn(created(50L));
        when(familyRepository.save(any(Family.class))).thenAnswer(call -> {
            Family saved = call.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        familyService.addRelation(1L, relation(AddRelationRequest.Kind.SPOUSE, null), ACTOR);

        ArgumentCaptor<PersonRequest> captor = ArgumentCaptor.forClass(PersonRequest.class);
        verify(personService).create(captor.capture(), eq(ACTOR));
        assertThat(captor.getValue().branchId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("adding a child into someone else's union is refused before anyone is created")
    void addChildIntoAnotherPersonsUnionIsRefused() {
        family(10, 3L, 4L);
        when(personService.exists(1L)).thenReturn(true);

        assertThatThrownBy(() -> familyService.addRelation(1L, relation(AddRelationRequest.Kind.CHILD, 10L), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("#10");
        verify(personService, never()).create(any(), any());
    }

    @Test
    @DisplayName("linking two existing people as spouses creates a union after the first person's own")
    void linkExistingSpouse() {
        family(10, 1L, 2L);
        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(60L)).thenReturn(true);
        when(familyRepository.save(any(Family.class))).thenAnswer(call -> {
            Family saved = call.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        AddRelationResponse response = familyService.linkExisting(
                1L, new LinkRelationRequest(LinkRelationRequest.Kind.SPOUSE, 60L, null, null), ACTOR);

        assertThat(response.personId()).isEqualTo(60L);
        assertThat(response.family().partner2Id()).isEqualTo(60L);
        assertThat(response.family().orderIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("two people who already share a union are not linked as spouses a second time")
    void linkExistingSpouseRefusesAnExistingMarriage() {
        family(10, 1L, 60L);
        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(60L)).thenReturn(true);

        assertThatThrownBy(() -> familyService.linkExisting(
                        1L, new LinkRelationRequest(LinkRelationRequest.Kind.SPOUSE, 60L, null, null), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã có hôn nhân");
        verify(familyRepository, never()).save(any());
    }

    @Test
    @DisplayName("linking an existing person as a parent opens a one-parent union for them and links the child in")
    void linkExistingParent() {
        when(personService.exists(1L)).thenReturn(true);
        when(personService.exists(70L)).thenReturn(true);
        when(familyRepository.save(any(Family.class))).thenAnswer(call -> {
            Family saved = call.getArgument(0);
            saved.setId(12L);
            families.add(saved);
            lenient().when(familyRepository.findById(12L)).thenReturn(Optional.of(saved));
            return saved;
        });

        familyService.linkExisting(
                1L, new LinkRelationRequest(LinkRelationRequest.Kind.PARENT, 70L, null, null), ACTOR);

        ArgumentCaptor<Family> union = ArgumentCaptor.forClass(Family.class);
        verify(familyRepository).save(union.capture());
        // The other person is the parent, so they sit in the union and the person in the path is the child.
        assertThat(union.getValue().getPartner1Id()).isEqualTo(70L);
        ArgumentCaptor<FamilyChild> link = ArgumentCaptor.forClass(FamilyChild.class);
        verify(familyChildRepository).save(link.capture());
        assertThat(link.getValue().getChildId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a person cannot be linked to themselves")
    void linkExistingRefusesTheSamePerson() {
        assertThatThrownBy(() -> familyService.linkExisting(
                        1L, new LinkRelationRequest(LinkRelationRequest.Kind.CHILD, 1L, null, null), ACTOR))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("correcting a child's link rewrites both relations and the birth order, with the reason recorded")
    void updateChildRewritesTheLink() {
        family(10, 1L, 2L);
        child(10, 3L);
        FamilyChild link = links.getFirst();
        when(familyChildRepository.findByFamilyIdAndChildId(10L, 3L)).thenReturn(Optional.of(link));

        familyService.updateChild(
                10L,
                3L,
                new FamilyChildUpdateRequest(RelationType.BIRTH, RelationType.STEP, 2, "theo gia phả cũ", null),
                ACTOR);

        assertThat(link.getRelationToP2()).isEqualTo(RelationType.STEP);
        assertThat(link.getBirthOrder()).isEqualTo(2);
        verify(auditService).record(
                eq(AuditEntityType.FAMILY), eq(10L), eq(AuditAction.UPDATE), any(), any(), eq(ACTOR),
                eq("theo gia phả cũ"));
        // Serialised with every other link write, and the union touched so its version moves.
        verify(advisoryLock).lock(AdvisoryLock.PARENTAGE);
        assertThat(families.getFirst().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("a child link edited from a stale form is refused, not silently overwritten")
    void updateChildRefusesAStaleForm() {
        Family union = family(10, 1L, 2L);
        union.setVersion(4);
        child(10, 3L);
        lenient().when(familyChildRepository.findByFamilyIdAndChildId(10L, 3L))
                .thenReturn(Optional.of(links.getFirst()));

        // Two editors correcting one child: the second used to overwrite the first's birth order (§8.10 #5).
        assertThatThrownBy(() -> familyService.updateChild(
                        10L, 3L, new FamilyChildUpdateRequest(RelationType.BIRTH, RelationType.BIRTH, 1, null, 3L),
                        ACTOR))
                .isInstanceOf(ConflictException.class);
        assertThat(links.getFirst().getBirthOrder()).isNull();
    }

    @Test
    @DisplayName("a union involves the living when either partner is living, and an unknown union fails closed")
    void involvesLivingIsTheOneUnionGuard() {
        family(10, 1L, 2L);
        family(11, 3L, 4L);
        when(personService.isLiving(1L)).thenReturn(false);
        when(personService.isLiving(2L)).thenReturn(true);
        when(personService.isLiving(3L)).thenReturn(false);
        when(personService.isLiving(4L)).thenReturn(false);
        when(familyRepository.findById(999L)).thenReturn(Optional.empty());

        // One living partner is enough: a wedding date is a fact about them as much as about the other (§3.6).
        assertThat(familyService.involvesLiving(10L)).isTrue();
        assertThat(familyService.involvesLiving(11L)).isFalse();
        assertThat(familyService.involvesLiving(999L)).isTrue();
    }

    /**
     * Builds a request to add a new person with one name.
     *
     * @param kind spouse or child
     * @param familyId for a child, the union to join, or null
     * @return the request
     */
    private static AddRelationRequest relation(AddRelationRequest.Kind kind, Long familyId) {
        PersonNameRequest name = new PersonNameRequest(PersonNameType.BIRTH, "Nguyễn", "Văn", "Mới", true);
        return new AddRelationRequest(kind, null, List.of(name), familyId, null);
    }

    /**
     * Builds the response the person service gives for a newly created person.
     *
     * @param id the new person's id
     * @return the response
     */
    private static PersonDetailResponse created(Long id) {
        return new PersonDetailResponse(id, "Nguyễn Văn Mới", Gender.UNKNOWN, null, null, true, null, List.of(),
                Instant.now(), 0);
    }

    /**
     * Builds a union payload naming only its partners.
     *
     * @param partner1Id first partner, may be null
     * @param partner2Id second partner, may be null
     * @return the request, with no version so the stale-edit check is skipped
     */
    private static FamilyRequest request(Long partner1Id, Long partner2Id) {
        return new FamilyRequest(partner1Id, partner2Id, null, null, null, null);
    }
}
