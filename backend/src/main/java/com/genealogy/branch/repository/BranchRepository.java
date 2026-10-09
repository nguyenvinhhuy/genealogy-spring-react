package com.genealogy.branch.repository;

import com.genealogy.branch.domain.Branch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for clan branches. */
public interface BranchRepository extends JpaRepository<Branch, Long> {

    /**
     * Lists every branch by name, lowest id first among equal names.
     *
     * @return all branches
     */
    List<Branch> findAllByOrderByNameAscIdAsc();

    /**
     * Finds the branch with one name under one parent.
     *
     * @param parentId the parent branch id, or null for the roots
     * @param name the exact name
     * @return the branch, or empty when there is none
     */
    // Written out: a derived query binds a null parent as `parent_id = NULL`, which matches no root at all.
    @Query("""
            SELECT b FROM Branch b
            WHERE b.name = :name
              AND ((:parentId IS NULL AND b.parentId IS NULL) OR b.parentId = :parentId)
            """)
    Optional<Branch> findSibling(@Param("parentId") Long parentId, @Param("name") String name);

    /**
     * Counts the branches directly under one branch.
     *
     * @param parentId the parent branch id
     * @return how many sub-branches it has
     */
    long countByParentId(Long parentId);

    /**
     * Lists the ids of a branch and every branch beneath it, however deep.
     *
     * @param branchId the branch at the top of the subtree
     * @return the ids of that branch and all its descendants
     */
    // UNION, not UNION ALL: the service refuses a cycle, but a query that loops for ever on one is not worth the risk.
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM branches WHERE id = :branchId
                UNION
                SELECT b.id FROM branches b JOIN subtree s ON b.parent_id = s.id
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<Long> findIdsWithDescendants(@Param("branchId") Long branchId);
}
