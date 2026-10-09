package com.genealogy.audit.service;

import com.genealogy.audit.dto.response.RevisionResponse;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Records and reads the audit trail (CLAUDE.md §3.8). */
public interface AuditService {

    /**
     * Records one change, serialising whatever the caller hands over as the payloads.
     *
     * @param entityType which kind of record changed
     * @param entityId the record that changed
     * @param action what was done to it
     * @param before the record as it was, or null on CREATE
     * @param after the record as it became, or null on DELETE
     * @param changedBy the member making the change, may be null for a system change
     * @param note why the change was made, may be null
     */
    void record(
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            Object before,
            Object after,
            Long changedBy,
            String note);

    /**
     * Lists the changes to one record, newest first, refusing callers below EDITOR.
     *
     * @param entityType which kind of record
     * @param entityId the record id
     * @param role the calling member's access level
     * @param pageable which page; any requested order is replaced by newest first
     * @return the matching page
     */
    Page<RevisionResponse> findForEntity(AuditEntityType entityType, Long entityId, Role role, Pageable pageable);

    /**
     * Lists changes across the whole gia phả matching every filter given, newest first, refusing callers below EDITOR.
     *
     * @param entityType which kind of record, or null for every kind
     * @param action what was done, or null for every action
     * @param role the calling member's access level
     * @param pageable which page; any requested order is replaced by newest first
     * @return the matching page
     */
    Page<RevisionResponse> search(AuditEntityType entityType, AuditAction action, Role role, Pageable pageable);
}
