package com.genealogy.audit.controller;

import com.genealogy.audit.dto.response.RevisionResponse;
import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the audit trail. */
@RestController
@RequestMapping("/api/v1/revisions")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * Lists recorded changes newest first: to one record when an id is given, otherwise across the gia phả.
     *
     * @param entityType which kind of record, or null for every kind
     * @param entityId one record's id, which needs its type
     * @param action what was done, or null for every action; ignored for one record
     * @param pageable which page
     * @param principal the authenticated caller
     * @return the matching page
     */
    @GetMapping
    @Operation(summary = "List recorded changes")
    public PagedModel<RevisionResponse> find(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) AuditAction action,
            Pageable pageable,
            @AuthenticationPrincipal AuthPrincipal principal) {
        if (entityId == null) {
            return new PagedModel<>(auditService.search(entityType, action, principal.role(), pageable));
        }
        // An id alone used to fall through to the whole clan's feed: nobody asking for one record wants that.
        if (entityType == null) {
            throw new BadRequestException("Cần cho biết loại bản ghi khi hỏi lịch sử của một bản ghi");
        }
        return new PagedModel<>(auditService.findForEntity(entityType, entityId, principal.role(), pageable));
    }
}
