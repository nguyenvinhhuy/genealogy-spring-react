package com.genealogy.common.util;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Takes transaction-scoped PostgreSQL advisory locks. */
// A @Repository so its flush of pending writes has its clashes translated, instead of reaching the handler as a 500.
@Repository
@RequiredArgsConstructor
public class AdvisoryLock {

    // Every parentage write: a cycle check and the link it allows must not interleave with another (§8.6).
    public static final long PARENTAGE = 1_000_001L;

    // Every chi write: two reparentings checked side by side could each pass and together close a loop.
    public static final long BRANCH_TREE = 1_000_002L;

    // Every place write, for the same reason as the chi tree, and so two imports do not create one path twice.
    public static final long PLACE_TREE = 1_000_003L;

    private final EntityManager entityManager;

    /**
     * Waits for and takes one lock, held until the surrounding transaction ends.
     *
     * @param key which lock, one of the constants above
     */
    public void lock(long key) {
        // Wrapped in a subquery because pg_advisory_xact_lock returns void, which JDBC cannot read as a result.
        entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS locked")
                .setParameter("key", key)
                .getSingleResult();
    }
}
