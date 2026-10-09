package com.genealogy.audit.repository;

import com.genealogy.audit.domain.Revision;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for the audit trail. */
public interface RevisionRepository extends JpaRepository<Revision, Long> {

    /**
     * Lists the changes to one record, in the order the pageable gives.
     *
     * @param entityType which kind of record
     * @param entityId the record id
     * @param pageable paging and order
     * @return the matching page
     */
    Page<Revision> findByEntityTypeAndEntityId(AuditEntityType entityType, Long entityId, Pageable pageable);

    /**
     * Lists the changes across the whole gia phả that match every filter given, in the order the pageable gives.
     *
     * @param entityType which kind of record, or null for every kind
     * @param action what was done, or null for every action
     * @param hidden a kind the caller may not see, or null when they may see every kind
     * @param pageable paging and order
     * @return the matching page
     */
    @Query("""
            SELECT r FROM Revision r
            WHERE (:entityType IS NULL OR r.entityType = :entityType)
              AND (:action IS NULL OR r.action = :action)
              AND (:hidden IS NULL OR r.entityType <> :hidden)
            """)
    Page<Revision> search(
            @Param("entityType") AuditEntityType entityType,
            @Param("action") AuditAction action,
            @Param("hidden") AuditEntityType hidden,
            Pageable pageable);
}
