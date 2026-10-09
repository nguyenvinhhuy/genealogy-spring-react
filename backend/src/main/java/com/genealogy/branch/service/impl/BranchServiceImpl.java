package com.genealogy.branch.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.branch.domain.Branch;
import com.genealogy.branch.dto.request.BranchRequest;
import com.genealogy.branch.dto.response.BranchResponse;
import com.genealogy.branch.mapper.BranchMapper;
import com.genealogy.branch.repository.BranchRepository;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.common.util.Blank;
import com.genealogy.common.util.StaleEdit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link BranchService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.BRANCH;

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final AuditService auditService;
    private final AdvisoryLock advisoryLock;

    /**
     * Lists every branch, by name.
     *
     * @return all branches, each carrying its parent id
     */
    @Override
    public List<BranchResponse> findAll() {
        return branchRepository.findAllByOrderByNameAscIdAsc().stream().map(branchMapper::toResponse).toList();
    }

    /**
     * Returns one branch.
     *
     * @param id branch id
     * @return the branch
     */
    @Override
    public BranchResponse getById(Long id) {
        return branchMapper.toResponse(require(id));
    }

    /**
     * Creates a branch and records it in the audit trail.
     *
     * @param request the branch to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created branch
     */
    @Override
    @Transactional
    public BranchResponse create(BranchRequest request, Long actorId) {
        advisoryLock.lock(AdvisoryLock.BRANCH_TREE);
        BranchRequest cleaned = cleaned(request);
        requireParentExists(cleaned.parentId());
        requireFreeName(null, cleaned);
        Branch saved = branchRepository.saveAndFlush(branchMapper.toEntity(cleaned));
        BranchResponse created = branchMapper.toResponse(saved);
        auditService.record(
                ENTITY_TYPE, created.id(), AuditAction.CREATE, null, created, actorId, cleaned.changeNote());
        return created;
    }

    /**
     * Updates a branch and records it in the audit trail, refusing a stale form, a sibling's name or a cycle.
     *
     * @param id branch id
     * @param request the new values
     * @param actorId the member making the change
     * @return the updated branch
     */
    @Override
    @Transactional
    public BranchResponse update(Long id, BranchRequest request, Long actorId) {
        // Two moves checked side by side could each pass and together close a loop, so they queue instead.
        advisoryLock.lock(AdvisoryLock.BRANCH_TREE);
        Branch branch = require(id);
        StaleEdit.refuseIfStale(request.version(), branch.getVersion(), AuditEntityType.BRANCH);
        BranchRequest cleaned = cleaned(request);
        requireParentExists(cleaned.parentId());
        requireNoCycle(id, cleaned.parentId());
        requireFreeName(id, cleaned);

        BranchResponse before = branchMapper.toResponse(branch);
        branchMapper.update(cleaned, branch);
        branchRepository.flush();
        BranchResponse after = branchMapper.toResponse(branch);
        auditService.record(ENTITY_TYPE, id, AuditAction.UPDATE, before, after, actorId, cleaned.changeNote());
        return after;
    }

    /**
     * Deletes a branch that has no sub-branches and records it in the audit trail.
     *
     * @param id branch id
     * @param actorId the member making the change
     * @param changeNote why the branch is being deleted, or null
     */
    @Override
    @Transactional
    public void delete(Long id, Long actorId, String changeNote) {
        advisoryLock.lock(AdvisoryLock.BRANCH_TREE);
        Branch branch = require(id);
        long children = branchRepository.countByParentId(id);
        if (children > 0) {
            throw new BadRequestException("Chi \"" + branch.getName() + "\" còn " + children
                    + " chi con. Hãy chuyển hoặc xoá các chi con trước.");
        }
        BranchResponse before = branchMapper.toResponse(branch);
        branchRepository.delete(branch);
        branchRepository.flush();
        // Recorded after the delete, and the trail outlives its subject (§3.8): this is the branch's last state.
        auditService.record(ENTITY_TYPE, id, AuditAction.DELETE, before, null, actorId, changeNote);
    }

    /**
     * Returns the branch with one name under one parent, creating it when there is none.
     *
     * @param name the branch name
     * @param parentId the parent branch id, or null for a root branch
     * @param actorId the member making the change
     * @return the branch's id
     */
    @Override
    @Transactional
    public Long findOrCreate(String name, Long parentId, Long actorId) {
        advisoryLock.lock(AdvisoryLock.BRANCH_TREE);
        String trimmed = name.strip();
        return branchRepository.findSibling(parentId, trimmed)
                .map(Branch::getId)
                .orElseGet(() -> create(new BranchRequest(trimmed, parentId, null, null, null), actorId).id());
    }

    /**
     * Returns the id of the branch at the end of a root-first path, creating any level of it that is missing.
     *
     * @param pathTopDown each level's name, from the root chi down to the leaf; blank levels are skipped
     * @param actorId the member making the change
     * @return the leaf branch's id, or empty when the path has no non-blank level
     */
    @Override
    @Transactional
    public Optional<Long> findOrCreatePath(List<String> pathTopDown, Long actorId) {
        List<String> parts = pathTopDown.stream()
                .map(part -> part == null ? "" : part.strip())
                .filter(part -> !part.isEmpty())
                .toList();
        Long parentId = null;
        for (String name : parts) {
            parentId = findOrCreate(name, parentId, actorId);
        }
        return Optional.ofNullable(parentId);
    }

    /**
     * Returns a branch's id together with the ids of every branch beneath it.
     *
     * @param branchId the branch at the top of the subtree
     * @return the ids of that branch and all its descendants, empty when there is no such branch
     */
    @Override
    public Set<Long> findWithDescendants(Long branchId) {
        // A chi must find the people recorded against a phái inside it, or the most precise records go missing.
        return Set.copyOf(branchRepository.findIdsWithDescendants(branchId));
    }

    /**
     * Reports whether a branch exists.
     *
     * @param id branch id
     * @return true if it exists
     */
    @Override
    public boolean exists(Long id) {
        return branchRepository.existsById(id);
    }

    /**
     * Loads a branch or fails.
     *
     * @param id branch id
     * @return the entity
     */
    private Branch require(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có chi với id " + id));
    }

    /**
     * Trims the name and description a form sent, dropping a blank description.
     *
     * @param request the inbound payload
     * @return the same request with its text tidied
     */
    private static BranchRequest cleaned(BranchRequest request) {
        String description = Blank.toNull(request.description());
        return new BranchRequest(
                request.name().strip(), request.parentId(), description, request.changeNote(), request.version());
    }

    /**
     * Fails when a parent id is given but names no branch.
     *
     * @param parentId the parent id, may be null
     */
    private void requireParentExists(Long parentId) {
        if (parentId != null && !branchRepository.existsById(parentId)) {
            throw new BadRequestException("Chi cha được chọn không còn tồn tại (id " + parentId + ")");
        }
    }

    /**
     * Fails when another branch under the same parent already has this name.
     *
     * @param id the branch being saved, or null for a new one
     * @param request the cleaned payload
     */
    private void requireFreeName(Long id, BranchRequest request) {
        // Two sibling chi with one name cannot be told apart in any picker (V10's uq_branches_sibling_name).
        branchRepository.findSibling(request.parentId(), request.name())
                .filter(sibling -> !sibling.getId().equals(id))
                .ifPresent(sibling -> {
                    throw new ConflictException("Đã có chi \"" + request.name() + "\" ở cùng cấp này");
                });
    }

    /**
     * Fails when reparenting a branch under itself or one of its own descendants.
     *
     * @param id the branch being moved
     * @param parentId the proposed parent, may be null
     */
    private void requireNoCycle(Long id, Long parentId) {
        if (parentId != null && branchRepository.findIdsWithDescendants(id).contains(parentId)) {
            throw new BadRequestException("Không thể đặt một chi vào bên dưới chính nó hoặc chi con của nó");
        }
    }
}
