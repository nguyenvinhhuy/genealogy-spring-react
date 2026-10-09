package com.genealogy.audit.dto.response;

import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import java.time.Instant;

/**
 * One recorded change, as returned to clients.
 *
 * @param id revision id
 * @param entityType which kind of record changed
 * @param entityId the record that changed
 * @param action what was done to it
 * @param beforeData the record as it was, as JSON; null on CREATE
 * @param afterData the record as it became, as JSON; null on DELETE
 * @param changedBy the member who made the change, or null when the account is gone
 * @param changedByName that member's name, or null
 * @param note why the change was made
 * @param changedAt when it happened
 */
public record RevisionResponse(
        Long id,
        AuditEntityType entityType,
        Long entityId,
        AuditAction action,
        String beforeData,
        String afterData,
        Long changedBy,
        String changedByName,
        String note,
        Instant changedAt) {
}
