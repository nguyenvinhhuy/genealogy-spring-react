package com.genealogy.family.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.RelationType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.common.util.StaleEdit;
import com.genealogy.family.domain.Family;
import com.genealogy.family.domain.FamilyChild;
import com.genealogy.family.dto.request.AddRelationRequest;
import com.genealogy.family.dto.request.FamilyChildRequest;
import com.genealogy.family.dto.request.FamilyChildUpdateRequest;
import com.genealogy.family.dto.request.FamilyRequest;
import com.genealogy.family.dto.request.LinkRelationRequest;
import com.genealogy.family.dto.response.AddRelationResponse;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.FamilyResponse;
import com.genealogy.family.dto.response.ImportedUnion;
import com.genealogy.family.dto.response.ReassignResult;
import com.genealogy.family.dto.response.SharedUnion;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.mapper.FamilyMapper;
import com.genealogy.family.repository.FamilyChildRepository;
import com.genealogy.family.repository.FamilyRepository;
import com.genealogy.family.service.FamilyService;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.service.PersonService;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link FamilyService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilyServiceImpl implements FamilyService {

    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.FAMILY;

    private final FamilyRepository familyRepository;
    private final FamilyChildRepository familyChildRepository;
    private final FamilyMapper familyMapper;
    private final AuditService auditService;
    // Cross-feature by interface only (CLAUDE.md §4): the person entity is never imported here.
    private final PersonService personService;
    private final AdvisoryLock advisoryLock;

    /**
     * Lists the unions one person belongs to.
     *
     * @param personId the person id
     * @return that person's unions
     */
    @Override
    public List<FamilyResponse> findByPerson(Long personId) {
        List<Family> unions = familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(personId, personId);
        if (unions.isEmpty()) {
            return List.of();
        }
        // Every union's children in one query, not one per union: the person page asks for this on every visit.
        Map<Long, List<FamilyChild>> childrenByUnion = familyChildRepository
                .findByFamilyIdInOrderByBirthOrderAsc(unions.stream().map(Family::getId).toList()).stream()
                .collect(Collectors.groupingBy(FamilyChild::getFamilyId));
        return unions.stream()
                .map(family -> familyMapper.toResponse(family, childrenByUnion.getOrDefault(family.getId(), List.of())))
                .toList();
    }

    /**
     * Returns one union.
     *
     * @param id family id
     * @return the union
     */
    @Override
    public FamilyResponse getById(Long id) {
        return toResponse(require(id));
    }

    /**
     * Creates a union.
     *
     * @param request the union to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created union
     */
    @Override
    @Transactional
    public FamilyResponse create(FamilyRequest request, Long actorId) {
        FamilyResponse created = createWithoutRecompute(request, actorId);
        // A new couple is exactly what places a married-in spouse at their partner's đời (§3.4).
        recomputeGenerations();
        return created;
    }

    /**
     * Creates a union and records it, leaving đời for the caller, who is about to change the graph again.
     *
     * @param request the union to create
     * @param actorId the member making the change
     * @return the created union
     */
    private FamilyResponse createWithoutRecompute(FamilyRequest request, Long actorId) {
        lockParentage();
        validatePartners(request);

        Family family = new Family();
        familyMapper.applyRequest(request, family);
        FamilyResponse created = toResponse(familyRepository.save(family));
        auditService.record(
                ENTITY_TYPE, created.id(), AuditAction.CREATE, null, created, actorId, request.changeNote());
        return created;
    }

    /**
     * Updates a union.
     *
     * @param id family id
     * @param request the new values
     * @param actorId the member making the change, may be null for a system change
     * @return the updated union
     */
    @Override
    @Transactional
    public FamilyResponse update(Long id, FamilyRequest request, Long actorId) {
        lockParentage();
        validatePartners(request);

        Family family = require(id);
        StaleEdit.refuseIfStale(request.version(), family.getVersion(), AuditEntityType.FAMILY);
        FamilyResponse before = toResponse(family);
        boolean swapped = Objects.equals(request.partner1Id(), family.getPartner2Id())
                && Objects.equals(request.partner2Id(), family.getPartner1Id());
        boolean partnersChanged = !Objects.equals(request.partner1Id(), family.getPartner1Id())
                || !Objects.equals(request.partner2Id(), family.getPartner2Id());
        if (partnersChanged) {
            requirePartnersOutsideDescendants(id, request);
        }
        familyMapper.applyRequest(request, family);
        if (swapped) {
            swapRelations(id);
        }
        // Status and order do not touch parentage, so only a change of partner is worth redoing the whole graph.
        if (partnersChanged) {
            recomputeGenerations();
        }
        // Flushed so the response carries the bumped version, or the form's next save is refused as stale.
        familyRepository.flush();
        FamilyResponse after = toResponse(family);
        auditService.record(ENTITY_TYPE, id, AuditAction.UPDATE, before, after, actorId, request.changeNote());
        return after;
    }

    /**
     * Swaps each child's relation to the two partners, after the partners themselves swapped slots.
     *
     * @param familyId the union whose partners traded places
     */
    private void swapRelations(Long familyId) {
        // Relations are stored per slot, so reordering the couple would otherwise hand a con riêng to the other one.
        for (FamilyChild link : familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(familyId)) {
            RelationType toFirst = link.getRelationToP1();
            link.setRelationToP1(link.getRelationToP2());
            link.setRelationToP2(toFirst);
        }
    }

    /**
     * Deletes a union and its child links.
     *
     * @param id family id
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the union was deleted, or null
     */
    @Override
    @Transactional
    public void delete(Long id, Long actorId, String changeNote) {
        lockParentage();
        Family family = require(id);
        FamilyResponse before = toResponse(family);
        familyChildRepository.deleteAll(familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(id));
        familyRepository.delete(family);
        recomputeGenerations();
        // Recorded after the delete, and the trail outlives its subject (§3.8): this is the union's last state.
        auditService.record(ENTITY_TYPE, id, AuditAction.DELETE, before, null, actorId, changeNote);
    }

    /**
     * Adds a new spouse or child to a person, creating the new person and the link in one transaction.
     *
     * @param personId the person being added to
     * @param request the new person and the kind of relation
     * @param actorId the member making the change
     * @return the new person and the union they joined
     */
    @Override
    @Transactional
    public AddRelationResponse addRelation(Long personId, AddRelationRequest request, Long actorId) {
        lockParentage();
        if (!personService.exists(personId)) {
            throw new NotFoundException("Không có người với id " + personId);
        }
        // Checked before anyone is created, so a wrong union is refused with nothing left behind.
        Family target = request.kind() == AddRelationRequest.Kind.CHILD && request.familyId() != null
                ? requirePartnerOf(request.familyId(), personId)
                : null;

        // Defaulted, not forced: the common case is the same chi, and the person-edit screen can still correct it.
        Long inheritedBranchId = personService.branchIdOf(personId);
        PersonRequest newPerson = new PersonRequest(
                request.gender(), inheritedBranchId, null, request.names(), request.changeNote(), null);
        Long createdId = personService.create(newPerson, actorId).id();

        if (request.kind() == AddRelationRequest.Kind.SPOUSE) {
            FamilyResponse union = create(new FamilyRequest(
                    personId, createdId, FamilyStatus.MARRIED, nextOrderIndex(personId), request.changeNote(), null),
                    actorId);
            return new AddRelationResponse(createdId, union);
        }
        // A person with no union to hang the child off gets a one-parent union, as §3.1 prescribes.
        // Created without a recompute: addChild below recomputes once, on the graph that has both the union and the child.
        Long familyId = target != null
                ? target.getId()
                : createWithoutRecompute(new FamilyRequest(
                        personId, null, FamilyStatus.MARRIED, nextOrderIndex(personId), request.changeNote(), null),
                        actorId).id();
        FamilyResponse union = addChild(familyId, new FamilyChildRequest(createdId, null, null, null), actorId);
        return new AddRelationResponse(createdId, union);
    }

    /**
     * Links two people who are both already recorded, as spouses or as parent and child.
     *
     * @param personId the person the link is made from
     * @param request what the other person is to them, and which union
     * @param actorId the member making the change
     * @return the other person and the union that now links them
     */
    @Override
    @Transactional
    public AddRelationResponse linkExisting(Long personId, LinkRelationRequest request, Long actorId) {
        lockParentage();
        Long otherId = request.otherPersonId();
        if (personId.equals(otherId)) {
            throw new BadRequestException("Một người không thể được nối với chính mình");
        }
        if (!personService.exists(personId)) {
            throw new NotFoundException("Không có người với id " + personId);
        }
        if (!personService.exists(otherId)) {
            throw new BadRequestException("Không có người với id " + otherId);
        }
        // Checked before anything is written, so a wrong union is refused with nothing left behind.
        Long keeperId = request.kind() == LinkRelationRequest.Kind.PARENT ? otherId : personId;
        Long childId = request.kind() == LinkRelationRequest.Kind.PARENT ? personId : otherId;
        Family target = request.kind() != LinkRelationRequest.Kind.SPOUSE && request.familyId() != null
                ? requirePartnerOf(request.familyId(), keeperId)
                : null;

        if (request.kind() == LinkRelationRequest.Kind.SPOUSE) {
            List<Family> unions = familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(personId, personId);
            boolean alreadyMarried = unions.stream()
                    .anyMatch(union -> otherId.equals(union.getPartner1Id()) || otherId.equals(union.getPartner2Id()));
            if (alreadyMarried) {
                throw new BadRequestException("Hai người này đã có hôn nhân với nhau. Hãy sửa hôn nhân đó.");
            }
            FamilyResponse union = create(new FamilyRequest(
                    personId, otherId, FamilyStatus.MARRIED, nextOrderIndex(personId), request.changeNote(), null),
                    actorId);
            return new AddRelationResponse(otherId, union);
        }
        // A person with no union to hang the child off gets a one-parent union, as §3.1 prescribes.
        Long familyId = target != null
                ? target.getId()
                : createWithoutRecompute(new FamilyRequest(
                        keeperId, null, FamilyStatus.MARRIED, nextOrderIndex(keeperId), request.changeNote(), null),
                        actorId).id();
        // addChild refuses a loop, a repeat, and a person who is their own parent, in this same transaction.
        FamilyResponse union = addChild(
                familyId, new FamilyChildRequest(childId, null, null, null), actorId);
        return new AddRelationResponse(otherId, union);
    }

    /**
     * Corrects how a linked child relates to each partner and where they fall in the birth order.
     *
     * @param familyId family id
     * @param childId the linked child
     * @param request the corrected relations and birth order
     * @param actorId the member making the change
     * @return the updated union
     */
    @Override
    @Transactional
    public FamilyResponse updateChild(Long familyId, Long childId, FamilyChildUpdateRequest request, Long actorId) {
        // Locked like every other link write, so two editors' corrections are serialised, not interleaved.
        lockParentage();
        Family family = require(familyId);
        // The link has no version of its own; the union's stands for its child list, as its revisions do.
        StaleEdit.refuseIfStale(request.version(), family.getVersion(), AuditEntityType.FAMILY);
        FamilyResponse before = toResponse(family);
        FamilyChild link = requireLink(familyId, childId);
        // No đời recompute: the đời walk follows every link whatever its relation, and birth order is not part of it.
        link.setRelationToP1(request.relationToP1());
        link.setRelationToP2(request.relationToP2());
        link.setBirthOrder(request.birthOrder());
        // Touched so the union's version moves: a second stale form of the same link is then refused (§8.10 #5).
        family.setUpdatedAt(Instant.now());
        familyChildRepository.flush();
        familyRepository.flush();
        FamilyResponse after = toResponse(family);
        // Recorded against the union, as a new link is: the union's child list is what a reader sees change.
        auditService.record(ENTITY_TYPE, familyId, AuditAction.UPDATE, before, after, actorId, request.changeNote());
        return after;
    }

    /**
     * Loads the link of one child into one union or fails.
     *
     * @param familyId the union
     * @param childId the child
     * @return the link
     */
    private FamilyChild requireLink(Long familyId, Long childId) {
        return familyChildRepository.findByFamilyIdAndChildId(familyId, childId)
                .orElseThrow(() -> new NotFoundException("Người này không được ghi là con của hôn nhân #" + familyId));
    }

    /**
     * Loads a union and checks that a given person is one of its partners.
     *
     * @param familyId the union
     * @param personId the person who must be a partner
     * @return the union
     */
    private Family requirePartnerOf(Long familyId, Long personId) {
        Family family = require(familyId);
        if (!personId.equals(family.getPartner1Id()) && !personId.equals(family.getPartner2Id())) {
            throw new BadRequestException("Hôn nhân #" + familyId + " không phải của người này");
        }
        return family;
    }

    /**
     * Finds the position a person's next union takes among their unions.
     *
     * @param personId the person
     * @return one past the highest position already used, or 0 for a first union
     */
    private int nextOrderIndex(Long personId) {
        // Decided here rather than by the client, which counted a list that could still be loading.
        return familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(personId, personId).stream()
                .mapToInt(Family::getOrderIndex)
                .max()
                .orElse(-1) + 1;
    }

    /**
     * Links a child into a union, rejecting anything that would make a person their own ancestor.
     *
     * @param familyId family id
     * @param request the child to link
     * @param actorId the member making the change, may be null for a system change
     * @return the updated union
     */
    @Override
    @Transactional
    public FamilyResponse addChild(Long familyId, FamilyChildRequest request, Long actorId) {
        lockParentage();
        Family family = require(familyId);
        FamilyResponse before = toResponse(family);

        validateChild(family, request, childrenByParent());
        familyChildRepository.save(linkOf(familyId, request));

        recomputeGenerations();
        FamilyResponse after = toResponse(family);
        // Recorded against the union, not the link: the union's child list is what a reader sees change.
        auditService.record(ENTITY_TYPE, familyId, AuditAction.UPDATE, before, after, actorId, null);
        return after;
    }

    /**
     * Creates a union and links its children for an import, leaving đời for the caller to recompute once.
     *
     * @param request the union to create
     * @param children the children to link, in the order they should be tried
     * @param actorId the member running the import
     * @return the union as created, and the child links refused
     */
    @Override
    @Transactional
    public ImportedUnion importUnion(FamilyRequest request, List<FamilyChildRequest> children, Long actorId) {
        lockParentage();
        validatePartners(request);
        Family family = new Family();
        familyMapper.applyRequest(request, family);
        familyRepository.save(family);

        List<String> refusals = new ArrayList<>();
        // Loaded once and grown as links are accepted, rather than reread from both tables for every child.
        Map<Long, List<Long>> childrenByParent = childrenByParent();
        for (FamilyChildRequest child : children) {
            try {
                validateChild(family, child, childrenByParent);
            } catch (BadRequestException refused) {
                // Refused one at a time so one bad link does not cost the others, as a loop of addChild would.
                refusals.add(refused.getMessage());
                continue;
            }
            familyChildRepository.save(linkOf(family.getId(), child));
            // Flushed so the next child's duplicate check sees the ones already added.
            familyChildRepository.flush();
            addEdge(childrenByParent, family, child.childId());
        }

        FamilyResponse created = toResponse(family);
        // One revision with the children in it: to a reader of the trail the import created the union whole.
        auditService.record(
                ENTITY_TYPE, created.id(), AuditAction.CREATE, null, created, actorId, request.changeNote());
        return new ImportedUnion(created, List.copyOf(refusals));
    }

    /**
     * Unlinks a child from a union.
     *
     * @param familyId family id
     * @param childId the person to unlink
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the child was unlinked, or null
     * @return the updated union
     */
    @Override
    @Transactional
    public FamilyResponse removeChild(Long familyId, Long childId, Long actorId, String changeNote) {
        lockParentage();
        Family family = require(familyId);
        FamilyResponse before = toResponse(family);
        FamilyChild link = requireLink(familyId, childId);

        familyChildRepository.delete(link);
        recomputeGenerations();
        FamilyResponse after = toResponse(family);
        auditService.record(ENTITY_TYPE, familyId, AuditAction.UPDATE, before, after, actorId, changeNote);
        return after;
    }

    /** Recomputes every person's generation from the current parentage graph. */
    @Override
    @Transactional
    public void recomputeGenerations() {
        lockParentage();
        // The whole graph is redone, not patched: an incremental update is where a wrong đời creeps in unnoticed.
        personService.applyGenerations(GenerationCalculator.compute(loadUnions(), personService.findAllGenders()));
    }

    /**
     * Finds the thuỷ tổ of the clan: the founder đời is counted from, in the largest connected family.
     *
     * @return the founder's person id, or empty when no union is recorded
     */
    @Override
    public Optional<Long> findFounderId() {
        return GenerationCalculator.founderOfLargestFamily(loadUnions(), personService.findAllGenders());
    }

    /** Serialises every write that reads the parentage graph and then changes it, until the transaction ends. */
    private void lockParentage() {
        // Two editors each passing the cycle check on the same snapshot could otherwise close a loop between them.
        advisoryLock.lock(AdvisoryLock.PARENTAGE);
    }

    /**
     * Reads every union's partners and children from the tables, for a whole-graph walk.
     *
     * @return one entry per union
     */
    private List<GenerationCalculator.Union> loadUnions() {
        // Projections, not entities: a purge nulls a partner in the database while the session still holds the old row.
        // Each child keeps its kind of link to each partner: only a birth link is blood for đời and the founder (§3.4).
        Map<Long, List<GenerationCalculator.Child>> childrenByFamily = familyChildRepository.findAllLinks().stream()
                .collect(Collectors.groupingBy(
                        FamilyChildRepository.LinkRow::getFamilyId,
                        Collectors.mapping(
                                row -> new GenerationCalculator.Child(
                                        row.getChildId(),
                                        row.getRelationToP1() == RelationType.BIRTH,
                                        row.getRelationToP2() == RelationType.BIRTH),
                                Collectors.toList())));
        return familyRepository.findAllPartners().stream()
                .map(row -> new GenerationCalculator.Union(
                        row.getPartner1Id(),
                        row.getPartner2Id(),
                        childrenByFamily.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    /**
     * Loads every union these people are a partner in, with their children, in one pass.
     *
     * @param personIds the people to expand downward from
     * @return their unions
     */
    @Override
    public List<UnionEdgeResponse> findUnionsByPartners(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        return toUnionEdges(familyRepository.findByAnyPartnerIn(personIds));
    }

    /**
     * Loads every union these people appear in as a child, with their children, in one pass.
     *
     * @param childIds the people to expand upward from
     * @return the unions they were born into
     */
    @Override
    public List<UnionEdgeResponse> findUnionsByChildren(Collection<Long> childIds) {
        if (childIds.isEmpty()) {
            return List.of();
        }
        Set<Long> familyIds = familyChildRepository.findByChildIdIn(childIds).stream()
                .map(FamilyChild::getFamilyId)
                .collect(Collectors.toSet());
        return familyIds.isEmpty() ? List.of() : toUnionEdges(familyRepository.findAllById(familyIds));
    }

    /**
     * Loads every union in the clan with its children.
     *
     * @return every union
     */
    @Override
    public List<UnionEdgeResponse> findAllUnions() {
        return toUnionEdges(familyRepository.findAll());
    }

    /**
     * Reports whether either of two people descends from the other.
     *
     * @param personA one person
     * @param personB the other
     * @return true when a parent chain runs from one down to the other, in either direction
     */
    @Override
    public boolean inOneLineOfDescent(Long personA, Long personB) {
        if (personA.equals(personB)) {
            return false;
        }
        // One graph load for both directions: the merge used to ask twice and load it twice.
        Map<Long, List<Long>> childrenByParent = childrenByParent();
        return descendantsOf(personA, childrenByParent).contains(personB)
                || descendantsOf(personB, childrenByParent).contains(personA);
    }

    /**
     * Reports whether a union involves anyone still treated as living, failing closed for an unknown union.
     *
     * @param familyId the union id
     * @return true when either partner is living, and true as well when there is no such union
     */
    @Override
    public boolean involvesLiving(Long familyId) {
        List<Long> partners = partnerIds(familyId);
        // A wedding date is as private as a birth date (§3.6), and an unknown union fails closed like a person.
        return partners.isEmpty() || partners.stream().anyMatch(personService::isLiving);
    }

    /**
     * Lists the people recorded as partners of one union.
     *
     * @param familyId the union id
     * @return the partner ids that are recorded, empty when there is no such union
     */
    @Override
    public List<Long> partnerIds(Long familyId) {
        return familyRepository.findById(familyId)
                .map(family -> Stream.of(family.getPartner1Id(), family.getPartner2Id())
                        .filter(Objects::nonNull)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Reports whether a union exists.
     *
     * @param familyId the union id
     * @return true if it exists
     */
    @Override
    public boolean exists(Long familyId) {
        return familyRepository.existsById(familyId);
    }

    /**
     * Counts the children linked into one union.
     *
     * @param familyId the union id
     * @return how many children it has
     */
    @Override
    public int childCount(Long familyId) {
        return familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(familyId).size();
    }

    /**
     * Finds the unions that name both of two people as partners.
     *
     * @param personA one of the two people
     * @param personB the other one
     * @return each such union with its child count, empty when the two share none
     */
    @Override
    public List<SharedUnion> findSharedUnions(Long personA, Long personB) {
        return withChildCounts(
                familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(personA, personA).stream()
                        .filter(family ->
                                personB.equals(family.getPartner1Id()) || personB.equals(family.getPartner2Id()))
                        .toList());
    }

    /**
     * Finds the unions where one person is the only recorded partner and children are linked in.
     *
     * @param personId the person about to be deleted
     * @return each such union with its child count, empty when there is none
     */
    @Override
    public List<SharedUnion> soleParentUnionsWithChildren(Long personId) {
        return withChildCounts(soleParentUnions(personId)).stream()
                .filter(union -> union.childCount() > 0)
                .toList();
    }

    /**
     * Deletes the unions that would be left with no partner at all once one person is gone.
     *
     * @param personId the person about to be deleted
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the person is being deleted, recorded on each union dropped with them
     * @return the ids of the unions deleted
     */
    @Override
    @Transactional
    public List<Long> dropUnionsWithOnlyThisPartner(Long personId, Long actorId, String changeNote) {
        lockParentage();
        // ON DELETE SET NULL (V3) would null both slots and trip ck_families_has_partner: a 500 on delete.
        List<Family> orphaned = soleParentUnions(personId);
        List<FamilyResponse> before = orphaned.stream().map(this::toResponse).toList();
        familyRepository.deleteAll(orphaned);
        familyRepository.flush();
        before.forEach(union ->
                auditService.record(ENTITY_TYPE, union.id(), AuditAction.DELETE, union, null, actorId, changeNote));
        return before.stream().map(FamilyResponse::id).toList();
    }

    /**
     * Lists the unions naming one person where no second partner is recorded.
     *
     * @param personId the person to look for
     * @return the matching unions
     */
    private List<Family> soleParentUnions(Long personId) {
        return familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(personId, personId).stream()
                .filter(family -> family.getPartner1Id() == null || family.getPartner2Id() == null)
                .toList();
    }

    /**
     * Pairs each union with how many children are linked into it, in one query.
     *
     * @param unions the unions to count for
     * @return the unions with their child counts
     */
    private List<SharedUnion> withChildCounts(List<Family> unions) {
        if (unions.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = familyChildRepository
                .findByFamilyIdInOrderByBirthOrderAsc(unions.stream().map(Family::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(FamilyChild::getFamilyId, Collectors.counting()));
        return unions.stream()
                .map(family -> new SharedUnion(family.getId(), counts.getOrDefault(family.getId(), 0L).intValue()))
                .toList();
    }

    /**
     * Moves every union and parentage link off one person onto another, for a merge.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every union it changes
     * @return what was moved, folded or dropped
     */
    @Override
    @Transactional
    public ReassignResult reassignPerson(Long fromId, Long toId, Long actorId, String changeNote) {
        lockParentage();
        Map<Long, FamilyResponse> before = snapshotUnionsOf(fromId, toId);
        ReassignResult result = repointEverything(fromId, toId);
        // Diffed as a whole rather than logged branch by branch, so no repoint, fold or drop can go unrecorded (§3.8).
        recordChanges(before, actorId, changeNote);
        return result;
    }

    /**
     * Captures every union either person is a partner or a child in, before a merge changes them.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @return each union's state, by id
     */
    private Map<Long, FamilyResponse> snapshotUnionsOf(Long fromId, Long toId) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Long personId : List.of(fromId, toId)) {
            familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(personId, personId)
                    .forEach(family -> ids.add(family.getId()));
            familyChildRepository.findByChildId(personId).forEach(link -> ids.add(link.getFamilyId()));
        }
        Map<Long, FamilyResponse> snapshot = new LinkedHashMap<>();
        ids.forEach(id -> familyRepository.findById(id).ifPresent(family -> snapshot.put(id, toResponse(family))));
        return snapshot;
    }

    /**
     * Writes a revision for every captured union that a merge deleted or changed.
     *
     * @param before each union's state before the merge
     * @param actorId the member running the merge
     * @param changeNote the merge's reason
     */
    private void recordChanges(Map<Long, FamilyResponse> before, Long actorId, String changeNote) {
        before.forEach((id, was) -> {
            Family now = familyRepository.findById(id).orElse(null);
            if (now == null) {
                auditService.record(ENTITY_TYPE, id, AuditAction.DELETE, was, null, actorId, changeNote);
                return;
            }
            FamilyResponse after = toResponse(now);
            if (!after.equals(was)) {
                auditService.record(ENTITY_TYPE, id, AuditAction.UPDATE, was, after, actorId, changeNote);
            }
        });
    }

    /**
     * Repoints, folds and drops the absorbed person's unions and child links onto the survivor.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @return what was moved, folded or dropped
     */
    private ReassignResult repointEverything(Long fromId, Long toId) {
        List<String> notes = new ArrayList<>();
        List<Long> dropped = new ArrayList<>();
        Map<Long, Long> foldedInto = new LinkedHashMap<>();
        Set<Long> moved = new HashSet<>();
        int unionsMoved = repointPartner(fromId, toId, notes, dropped, moved);
        int childLinksMoved = repointChildLinks(fromId, toId, notes);
        // Repointing runs first, so unions naming the two separately are both on the survivor when compared.
        int unionsCollapsed = collapseDuplicateUnions(toId, moved, notes, foldedInto);
        // The caller is told which union ids died, because events, citations and media key on them with no FK.
        return new ReassignResult(
                unionsMoved, unionsCollapsed, childLinksMoved, foldedInto, List.copyOf(dropped), notes);
    }

    /**
     * Repoints every union where the absorbed person is a partner.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param notes collects what was dropped rather than moved
     * @param dropped collects the ids of unions deleted outright
     * @param moved collects the ids of unions that now name the survivor
     * @return how many unions were repointed
     */
    private int repointPartner(Long fromId, Long toId, List<String> notes, List<Long> dropped, Set<Long> moved) {
        int count = 0;
        for (Family family : familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(fromId, fromId)) {
            boolean otherIsTarget = toId.equals(family.getPartner1Id()) || toId.equals(family.getPartner2Id());
            if (otherIsTarget) {
                // Repointing trips ck_families_distinct_partners, and a merge may not guess which is wrong.
                notes.add("Bỏ hôn nhân #" + family.getId() + ": hai người này đang được ghi là vợ chồng của nhau");
                dropped.add(family.getId());
                familyRepository.delete(family);
                continue;
            }
            if (fromId.equals(family.getPartner1Id())) {
                family.setPartner1Id(toId);
            } else {
                family.setPartner2Id(toId);
            }
            moved.add(family.getId());
            count++;
        }
        familyRepository.flush();
        return count;
    }

    /**
     * Repoints every parentage link where the absorbed person is the child.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param notes collects what was dropped rather than moved
     * @return how many links were repointed
     */
    private int repointChildLinks(Long fromId, Long toId, List<String> notes) {
        int moved = 0;
        for (FamilyChild link : familyChildRepository.findByChildId(fromId)) {
            if (familyChildRepository.findByFamilyIdAndChildId(link.getFamilyId(), toId).isPresent()) {
                // Both were already children of this union; keeping the survivor's row is the whole point.
                familyChildRepository.delete(link);
                continue;
            }
            Family family = require(link.getFamilyId());
            if (toId.equals(family.getPartner1Id()) || toId.equals(family.getPartner2Id())) {
                notes.add("Bỏ liên kết con ở hôn nhân #" + family.getId()
                        + ": người giữ lại là cha/mẹ của hôn nhân đó");
                familyChildRepository.delete(link);
                continue;
            }
            link.setChildId(toId);
            moved++;
        }
        familyChildRepository.flush();
        return moved;
    }

    /**
     * Folds each union the merge brought across into the survivor's own union with the same spouse.
     *
     * @param personId the surviving person whose unions may now be duplicated
     * @param moved the unions that came across from the absorbed person
     * @param notes collects what was folded
     * @param foldedInto records each folded union against the one that survived it
     * @return how many unions were folded away
     */
    private int collapseDuplicateUnions(
            Long personId, Set<Long> moved, List<String> notes, Map<Long, Long> foldedInto) {
        Map<Long, Family> keptByPartner = new HashMap<>();
        List<Family> unions = familyRepository.findByPartner1IdOrPartner2IdOrderByOrderIndexAsc(personId, personId);
        // The survivor's own unions are placed first, so a union that came across is what gets folded away.
        List<Family> ordered = Stream.concat(
                        unions.stream().filter(family -> !moved.contains(family.getId())),
                        unions.stream().filter(family -> moved.contains(family.getId())))
                .toList();
        int collapsed = 0;
        for (Family family : ordered) {
            Long other = personId.equals(family.getPartner1Id()) ? family.getPartner2Id() : family.getPartner1Id();
            // A union with an unrecorded spouse is not evidence of the same union, so it is left alone.
            if (other == null) {
                continue;
            }
            if (!moved.contains(family.getId())) {
                // Only the survivor's own unions are kept: two of them with one spouse are a divorce and a remarriage.
                keptByPartner.putIfAbsent(other, family);
                continue;
            }
            // A moved union folds into the survivor's own only: two moved ones are the duplicate's own remarriage.
            Family kept = keptByPartner.get(other);
            if (kept == null) {
                continue;
            }
            moveChildren(family, kept);
            notes.add("Gộp hôn nhân #" + family.getId() + " vào #" + kept.getId() + ": cùng một cặp vợ chồng");
            foldedInto.put(family.getId(), kept.getId());
            familyRepository.delete(family);
            collapsed++;
        }
        familyRepository.flush();
        return collapsed;
    }

    /**
     * Moves the children of one union into another, skipping any the target already has.
     *
     * @param from the union being folded away
     * @param to the union being kept
     */
    private void moveChildren(Family from, Family to) {
        // Relations are stored per slot, so a couple recorded in opposite slots must swap each child's relations.
        boolean reversed = !Objects.equals(from.getPartner1Id(), to.getPartner1Id());
        for (FamilyChild link : familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(from.getId())) {
            if (familyChildRepository.findByFamilyIdAndChildId(to.getId(), link.getChildId()).isPresent()) {
                familyChildRepository.delete(link);
                continue;
            }
            link.setFamilyId(to.getId());
            if (reversed) {
                RelationType toFirst = link.getRelationToP1();
                link.setRelationToP1(link.getRelationToP2());
                link.setRelationToP2(toFirst);
            }
        }
        familyChildRepository.flush();
    }

    /**
     * Attaches the child links of a batch of unions in a single extra query.
     *
     * @param families the unions to flatten
     * @return the union projections
     */
    private List<UnionEdgeResponse> toUnionEdges(List<Family> families) {
        List<Long> familyIds = families.stream().map(Family::getId).toList();
        // One query for every union's children, so expanding a BFS layer stays two round trips, not N.
        Map<Long, List<ChildEdgeResponse>> childrenByFamily =
                familyChildRepository.findByFamilyIdInOrderByBirthOrderAsc(familyIds).stream()
                        .collect(Collectors.groupingBy(
                                FamilyChild::getFamilyId,
                                Collectors.mapping(familyMapper::toChildEdge, Collectors.toList())));

        return families.stream()
                .map(family -> familyMapper.toUnionEdge(
                        family, childrenByFamily.getOrDefault(family.getId(), List.of())))
                .toList();
    }

    /**
     * Fails when a child cannot be linked into a union.
     *
     * @param family the union
     * @param request the child to link
     * @param childrenByParent each parent mapped to their children, including links accepted earlier in this call
     */
    private void validateChild(Family family, FamilyChildRequest request, Map<Long, List<Long>> childrenByParent) {
        if (!personService.exists(request.childId())) {
            throw new BadRequestException("Không có người với id " + request.childId());
        }
        if (request.childId().equals(family.getPartner1Id()) || request.childId().equals(family.getPartner2Id())) {
            throw new BadRequestException("Một người không thể là cha/mẹ của chính mình");
        }
        if (familyChildRepository.findByFamilyIdAndChildId(family.getId(), request.childId()).isPresent()) {
            throw new BadRequestException("Người này đã được ghi là con của hôn nhân này");
        }
        Set<Long> descendants = descendantsOf(request.childId(), childrenByParent);
        if (descendants.contains(family.getPartner1Id()) || descendants.contains(family.getPartner2Id())) {
            throw new BadRequestException("Liên kết này sẽ biến một người thành tổ tiên của chính mình");
        }
    }

    /**
     * Records a newly accepted child link in an in-memory parent map.
     *
     * @param childrenByParent each parent mapped to their children, updated in place
     * @param family the union the child joined
     * @param childId the child
     */
    private static void addEdge(Map<Long, List<Long>> childrenByParent, Family family, Long childId) {
        Stream.of(family.getPartner1Id(), family.getPartner2Id())
                .filter(Objects::nonNull)
                .forEach(parent -> childrenByParent.computeIfAbsent(parent, key -> new ArrayList<>()).add(childId));
    }

    /**
     * Builds the parentage row for one child of a union.
     *
     * @param familyId the union
     * @param request the child to link
     * @return the entity, not yet saved
     */
    private static FamilyChild linkOf(Long familyId, FamilyChildRequest request) {
        FamilyChild link = new FamilyChild();
        link.setFamilyId(familyId);
        link.setChildId(request.childId());
        link.setRelationToP1(request.relationToP1() == null ? RelationType.BIRTH : request.relationToP1());
        link.setRelationToP2(request.relationToP2() == null ? RelationType.BIRTH : request.relationToP2());
        link.setBirthOrder(request.birthOrder());
        return link;
    }

    /**
     * Rejects new partners for a union when one of them already descends from a child of that union.
     *
     * @param familyId the union being edited
     * @param request the new partners
     */
    private void requirePartnersOutsideDescendants(Long familyId, FamilyRequest request) {
        List<FamilyChild> children = familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(familyId);
        if (children.isEmpty()) {
            return;
        }
        // Swapping a partner is a new parent edge for every child at once, so the add-child guard must run here too.
        Map<Long, List<Long>> childrenByParent = childrenByParent();
        for (FamilyChild child : children) {
            Set<Long> descendants = descendantsOf(child.getChildId(), childrenByParent);
            if (descendants.contains(request.partner1Id()) || descendants.contains(request.partner2Id())) {
                throw new BadRequestException("Thay đổi này sẽ biến một người thành tổ tiên của chính mình");
            }
        }
    }

    /**
     * Collects every person reachable downward from one person over an already-loaded parent map.
     *
     * @param personId the person to start from
     * @param childrenByParent each parent mapped to their children
     * @return the person and all of their descendants
     */
    private static Set<Long> descendantsOf(Long personId, Map<Long, List<Long>> childrenByParent) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.add(personId);
        seen.add(personId);

        while (!pending.isEmpty()) {
            for (Long child : childrenByParent.getOrDefault(pending.removeFirst(), List.of())) {
                if (seen.add(child)) {
                    pending.add(child);
                }
            }
        }
        return seen;
    }

    /**
     * Maps every recorded parent to their children, for a whole-graph walk.
     *
     * @return each partner id mapped to the children of the unions they belong to
     */
    private Map<Long, List<Long>> childrenByParent() {
        Map<Long, List<Long>> byParent = new HashMap<>();
        for (GenerationCalculator.Union union : loadUnions()) {
            Stream.of(union.partner1Id(), union.partner2Id())
                    .filter(Objects::nonNull)
                    .forEach(parent -> byParent.computeIfAbsent(parent, key -> new ArrayList<>())
                            .addAll(union.allChildIds()));
        }
        return byParent;
    }

    /**
     * Rejects a union that names no partner, the same person twice, or a person that does not exist.
     *
     * @param request the inbound payload
     */
    private void validatePartners(FamilyRequest request) {
        if (request.partner1Id() == null && request.partner2Id() == null) {
            throw new BadRequestException("Một hôn nhân cần ít nhất một người");
        }
        if (request.partner1Id() != null && request.partner1Id().equals(request.partner2Id())) {
            throw new BadRequestException("Một người không thể là vợ/chồng của chính mình");
        }
        for (Long partnerId : new Long[] {request.partner1Id(), request.partner2Id()}) {
            if (partnerId != null && !personService.exists(partnerId)) {
                throw new BadRequestException("Không có người với id " + partnerId);
            }
        }
    }

    /**
     * Loads a union or fails.
     *
     * @param id family id
     * @return the entity
     */
    private Family require(Long id) {
        return familyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có hôn nhân với id " + id));
    }

    /**
     * Builds the response for a union, attaching its child links.
     *
     * @param family the entity
     * @return the response DTO
     */
    private FamilyResponse toResponse(Family family) {
        return familyMapper.toResponse(
                family, familyChildRepository.findByFamilyIdOrderByBirthOrderAsc(family.getId()));
    }
}
