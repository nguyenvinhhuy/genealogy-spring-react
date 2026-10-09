package com.genealogy.audit.service.impl;

import com.genealogy.audit.domain.Revision;
import com.genealogy.audit.dto.response.RevisionResponse;
import com.genealogy.audit.mapper.RevisionMapper;
import com.genealogy.audit.repository.RevisionRepository;
import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.Role;
import com.genealogy.common.web.PageRequests;
import com.genealogy.member.service.MemberNameService;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Default {@link AuditService}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditServiceImpl implements AuditService {

    // The id breaks ties: one merge writes many revisions in the same instant, and pages must not overlap.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("changedAt"), Sort.Order.desc("id"));

    private final RevisionRepository revisionRepository;
    private final RevisionMapper revisionMapper;
    // Cross-feature by interface only (CLAUDE.md §4): the member entity is never imported here.
    private final MemberNameService memberNameService;
    private final ObjectMapper objectMapper;

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
    @Override
    @Transactional
    public void record(
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            Object before,
            Object after,
            Long changedBy,
            String note) {
        // Joins the caller's transaction: with REQUIRES_NEW a rolled-back edit still left a revision behind.
        Revision revision = new Revision();
        revision.setEntityType(entityType);
        revision.setEntityId(entityId);
        revision.setAction(action);
        revision.setBeforeData(serialise(before));
        revision.setAfterData(serialise(after));
        revision.setChangedBy(changedBy);
        revision.setNote(note);

        revisionRepository.save(revision);
    }

    /**
     * Lists the changes to one record, newest first, refusing callers below EDITOR.
     *
     * @param entityType which kind of record
     * @param entityId the record id
     * @param role the calling member's access level
     * @param pageable which page; any requested order is replaced by newest first
     * @return the matching page
     */
    @Override
    public Page<RevisionResponse> findForEntity(
            AuditEntityType entityType, Long entityId, Role role, Pageable pageable) {
        requireReader(role);
        requireMaySee(entityType, role);
        return withNames(revisionRepository.findByEntityTypeAndEntityId(entityType, entityId, newestFirst(pageable)));
    }

    /**
     * Lists changes across the whole gia phả matching every filter given, newest first, refusing callers below EDITOR.
     *
     * @param entityType which kind of record, or null for every kind
     * @param action what was done, or null for every action
     * @param role the calling member's access level
     * @param pageable which page; any requested order is replaced by newest first
     * @return the matching page
     */
    @Override
    public Page<RevisionResponse> search(
            AuditEntityType entityType, AuditAction action, Role role, Pageable pageable) {
        requireReader(role);
        if (entityType != null) {
            requireMaySee(entityType, role);
        }
        AuditEntityType hidden = role == Role.ADMIN ? null : AuditEntityType.MEMBER;
        return withNames(revisionRepository.search(entityType, action, hidden, newestFirst(pageable)));
    }

    /**
     * Refuses a caller who may not read the trail.
     *
     * @param role the calling member's access level
     */
    private static void requireReader(Role role) {
        // The route matcher already stops a MEMBER; this holds when a new endpoint forgets it (§3.6).
        if (!Role.isEditorOrAbove(role)) {
            throw new ForbiddenException("Chỉ biên tập viên và trưởng tộc được xem lịch sử thay đổi");
        }
    }

    /**
     * Refuses a kind of record the caller may not see the history of.
     *
     * @param entityType the kind asked for
     * @param role the calling member's access level
     */
    private static void requireMaySee(AuditEntityType entityType, Role role) {
        // An account's revision carries its email, and only the trưởng tộc may list accounts (§8.11 #13).
        if (entityType == AuditEntityType.MEMBER && role != Role.ADMIN) {
            throw new ForbiddenException("Chỉ trưởng tộc được xem lịch sử tài khoản.");
        }
    }

    /**
     * Keeps the requested page but fixes the order and caps the size.
     *
     * @param pageable the page the client asked for
     * @return the page to query
     */
    private static Pageable newestFirst(Pageable pageable) {
        // A client sort on an unknown field was a 500, and the trail has exactly one order that means anything.
        return PageRequests.capped(pageable, NEWEST_FIRST);
    }

    /**
     * Attaches the author's name to each revision on a page.
     *
     * @param page the revisions
     * @return the response page
     */
    private Page<RevisionResponse> withNames(Page<Revision> page) {
        Set<Long> authorIds = page.getContent().stream()
                .map(Revision::getChangedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = memberNameService.findNames(authorIds);
        return page.map(revision -> revisionMapper.toResponse(
                revision, revision.getChangedBy() == null ? null : names.get(revision.getChangedBy())));
    }

    /**
     * Serialises a payload to JSON, or null when there is nothing to record.
     *
     * @param payload the object to serialise, may be null
     * @return the JSON, or null
     */
    private String serialise(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (RuntimeException ex) {
            // Never null: both payloads null trips ck_revisions_has_payload and rolls the caller's edit back.
            log.warn("Could not serialise an audit payload of {}", payload.getClass(), ex);
            return "{\"error\":\"payload could not be serialised\"}";
        }
    }
}
