package com.genealogy.branch.service;

import com.genealogy.branch.dto.request.BranchRequest;
import com.genealogy.branch.dto.response.BranchResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Clan-branch operations. */
public interface BranchService {

    /**
     * Lists every branch, by name.
     *
     * @return all branches, each carrying its parent id
     */
    List<BranchResponse> findAll();

    /**
     * Returns one branch.
     *
     * @param id branch id
     * @return the branch
     */
    BranchResponse getById(Long id);

    /**
     * Creates a branch and records it in the audit trail.
     *
     * @param request the branch to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created branch
     */
    BranchResponse create(BranchRequest request, Long actorId);

    /**
     * Updates a branch and records it in the audit trail, refusing a stale form, a sibling's name or a cycle.
     *
     * @param id branch id
     * @param request the new values
     * @param actorId the member making the change
     * @return the updated branch
     */
    BranchResponse update(Long id, BranchRequest request, Long actorId);

    /**
     * Deletes a branch that has no sub-branches and records it in the audit trail.
     *
     * @param id branch id
     * @param actorId the member making the change
     * @param changeNote why the branch is being deleted, or null
     */
    // Members are checked by the caller: `person` depends on `branch`, so this feature cannot ask it.
    void delete(Long id, Long actorId, String changeNote);

    /**
     * Returns the branch with one name under one parent, creating it when there is none.
     *
     * @param name the branch name
     * @param parentId the parent branch id, or null for a root branch
     * @param actorId the member making the change
     * @return the branch's id
     */
    Long findOrCreate(String name, Long parentId, Long actorId);

    /**
     * Returns the id of the branch at the end of a root-first path, creating any level of it that is missing.
     *
     * @param pathTopDown each level's name, from the root chi down to the leaf; blank levels are skipped
     * @param actorId the member making the change
     * @return the leaf branch's id, or empty when the path has no non-blank level
     */
    Optional<Long> findOrCreatePath(List<String> pathTopDown, Long actorId);

    /**
     * Returns a branch's id together with the ids of every branch beneath it.
     *
     * @param branchId the branch at the top of the subtree
     * @return the ids of that branch and all its descendants, empty when there is no such branch
     */
    Set<Long> findWithDescendants(Long branchId);

    /**
     * Reports whether a branch exists.
     *
     * @param id branch id
     * @return true if it exists
     */
    boolean exists(Long id);
}
