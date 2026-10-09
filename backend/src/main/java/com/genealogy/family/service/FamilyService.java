package com.genealogy.family.service;

import com.genealogy.family.dto.request.AddRelationRequest;
import com.genealogy.family.dto.request.FamilyChildRequest;
import com.genealogy.family.dto.request.FamilyChildUpdateRequest;
import com.genealogy.family.dto.request.FamilyRequest;
import com.genealogy.family.dto.request.LinkRelationRequest;
import com.genealogy.family.dto.response.AddRelationResponse;
import com.genealogy.family.dto.response.FamilyResponse;
import com.genealogy.family.dto.response.ImportedUnion;
import com.genealogy.family.dto.response.ReassignResult;
import com.genealogy.family.dto.response.SharedUnion;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Union and parentage operations. */
public interface FamilyService {

    /**
     * Lists the unions one person belongs to.
     *
     * @param personId the person id
     * @return that person's unions
     */
    List<FamilyResponse> findByPerson(Long personId);

    /**
     * Returns one union.
     *
     * @param id family id
     * @return the union
     */
    FamilyResponse getById(Long id);

    /**
     * Creates a union.
     *
     * @param request the union to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created union
     */
    FamilyResponse create(FamilyRequest request, Long actorId);

    /**
     * Updates a union.
     *
     * @param id family id
     * @param request the new values
     * @param actorId the member making the change, may be null for a system change
     * @return the updated union
     */
    FamilyResponse update(Long id, FamilyRequest request, Long actorId);

    /**
     * Deletes a union and its child links.
     *
     * @param id family id
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the union was deleted, or null
     */
    void delete(Long id, Long actorId, String changeNote);

    /**
     * Adds a new spouse or child to a person, creating the new person and the link in one transaction.
     *
     * @param personId the person being added to
     * @param request the new person and the kind of relation
     * @param actorId the member making the change
     * @return the new person and the union they joined
     */
    // One transaction: as three requests from the browser, a failure after the first left an orphan behind.
    AddRelationResponse addRelation(Long personId, AddRelationRequest request, Long actorId);

    /**
     * Links two people who are both already recorded as spouses, or as parent and child.
     *
     * @param personId the person the link is made from
     * @param request what the other person is to them, and which union
     * @param actorId the member making the change
     * @return the other person and the union that now links them
     */
    // One transaction, so a union created for the link is not left childless when the link itself is refused.
    AddRelationResponse linkExisting(Long personId, LinkRelationRequest request, Long actorId);

    /**
     * Corrects how a linked child relates to each partner and where they fall in the birth order.
     *
     * @param familyId family id
     * @param childId the linked child
     * @param request the corrected relations and birth order
     * @param actorId the member making the change
     * @return the updated union
     */
    FamilyResponse updateChild(Long familyId, Long childId, FamilyChildUpdateRequest request, Long actorId);

    /**
     * Links a child into a union, rejecting anything that would make a person their own ancestor.
     *
     * @param familyId family id
     * @param request the child to link
     * @param actorId the member making the change, may be null for a system change
     * @return the updated union
     */
    FamilyResponse addChild(Long familyId, FamilyChildRequest request, Long actorId);

    /**
     * Creates a union and links its children for an import, leaving đời for the caller to recompute once.
     *
     * @param request the union to create
     * @param children the children to link, in the order they should be tried
     * @param actorId the member running the import
     * @return the union as created, and the child links refused
     */
    // No recompute here: each is a whole-graph reload and rewrite, and an import calls this once per FAM (§8.6).
    ImportedUnion importUnion(FamilyRequest request, List<FamilyChildRequest> children, Long actorId);

    /**
     * Unlinks a child from a union.
     *
     * @param familyId family id
     * @param childId the person to unlink
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the child was unlinked, or null
     * @return the updated union
     */
    FamilyResponse removeChild(Long familyId, Long childId, Long actorId, String changeNote);

    /** Recomputes every person's generation from the current parentage graph. */
    void recomputeGenerations();

    /**
     * Finds the thuỷ tổ of the clan: the founder đời is counted from, in the largest connected family.
     *
     * @return the founder's person id, or empty when no union is recorded
     */
    Optional<Long> findFounderId();

    /**
     * Loads every union these people are a partner in, with their children, in one pass (CLAUDE.md §4).
     *
     * @param personIds the people to expand downward from
     * @return their unions
     */
    List<UnionEdgeResponse> findUnionsByPartners(Collection<Long> personIds);

    /**
     * Loads every union these people appear in as a child, with their children, in one pass.
     *
     * @param childIds the people to expand upward from
     * @return the unions they were born into
     */
    List<UnionEdgeResponse> findUnionsByChildren(Collection<Long> childIds);

    /**
     * Loads every union in the clan with its children.
     *
     * @return every union
     */
    List<UnionEdgeResponse> findAllUnions();

    /**
     * Reports whether either of two people descends from the other.
     *
     * @param personA one person
     * @param personB the other
     * @return true when a parent chain runs from one down to the other, in either direction
     */
    boolean inOneLineOfDescent(Long personA, Long personB);

    /**
     * Reports whether a union involves anyone still treated as living, failing closed for an unknown union.
     *
     * @param familyId the union id
     * @return true when either partner is living, and true as well when there is no such union
     */
    // The one guard for a union's private facts: its wedding date, photos and citations each had a copy (§3.6).
    boolean involvesLiving(Long familyId);

    /**
     * Lists the people recorded as partners of one union.
     *
     * @param familyId the union id
     * @return the partner ids that are recorded, empty when there is no such union
     */
    List<Long> partnerIds(Long familyId);

    /**
     * Reports whether a union exists.
     *
     * @param familyId the union id
     * @return true if it exists
     */
    boolean exists(Long familyId);

    /**
     * Counts the children linked into one union.
     *
     * @param familyId the union id
     * @return how many children it has
     */
    int childCount(Long familyId);

    /**
     * Finds the unions that name both of two people as partners.
     *
     * @param personA one of the two people
     * @param personB the other one
     * @return each such union with its child count, empty when the two share none
     */
    List<SharedUnion> findSharedUnions(Long personA, Long personB);

    /**
     * Finds the unions where one person is the only recorded partner and children are linked in.
     *
     * @param personId the person about to be deleted
     * @return each such union with its child count, empty when there is none
     */
    List<SharedUnion> soleParentUnionsWithChildren(Long personId);

    /**
     * Deletes the unions that would be left with no partner at all once one person is gone.
     *
     * @param personId the person about to be deleted
     * @param actorId the member making the change, may be null for a system change
     * @param changeNote why the person is being deleted, recorded on each union dropped with them
     * @return the ids of the unions deleted
     */
    List<Long> dropUnionsWithOnlyThisPartner(Long personId, Long actorId, String changeNote);

    /**
     * Moves every union and parentage link off one person onto another, for a merge (F11).
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every union it changes
     * @return what was moved, folded or dropped
     */
    ReassignResult reassignPerson(Long fromId, Long toId, Long actorId, String changeNote);
}
