package com.genealogy.branch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.branch.domain.Branch;
import com.genealogy.branch.dto.request.BranchRequest;
import com.genealogy.branch.dto.response.BranchResponse;
import com.genealogy.branch.mapper.BranchMapper;
import com.genealogy.branch.repository.BranchRepository;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.util.AdvisoryLock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for clan-branch operations (CLAUDE.md §8.7). */
@ExtendWith(MockitoExtension.class)
class BranchServiceImplTest {

    private static final Long ACTOR = 9L;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private AdvisoryLock advisoryLock;

    private BranchServiceImpl branches;

    @BeforeEach
    void setUp() {
        BranchMapper mapper = Mappers.getMapper(BranchMapper.class);
        branches = new BranchServiceImpl(branchRepository, mapper, auditService, advisoryLock);
    }

    /** Builds a saved branch entity. */
    private static Branch entity(Long id, String name, Long parentId, long version) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setName(name);
        branch.setParentId(parentId);
        branch.setVersion(version);
        return branch;
    }

    /** Builds a create/update request with no change note or version. */
    private static BranchRequest request(String name, Long parentId) {
        return new BranchRequest(name, parentId, null, "vì sao", null);
    }

    @Test
    @DisplayName("creating a root branch records it in the audit trail")
    void createsARootBranch() {
        when(branchRepository.findSibling(null, "Chi Ba")).thenReturn(Optional.empty());
        when(branchRepository.saveAndFlush(any(Branch.class))).thenAnswer(call -> {
            Branch saved = call.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        BranchResponse created = branches.create(request("Chi Ba", null), ACTOR);

        assertThat(created.id()).isEqualTo(5L);
        verify(auditService).record(
                AuditEntityType.BRANCH, 5L, AuditAction.CREATE, null, created, ACTOR, "vì sao");
    }

    @Test
    @DisplayName("creating a branch under an unknown parent is refused")
    void refusesAnUnknownParent() {
        when(branchRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> branches.create(request("Chi Ba", 99L), ACTOR))
                .isInstanceOf(BadRequestException.class);
        verify(branchRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("two siblings cannot share a name, but the same branch may keep its own")
    void refusesADuplicateSiblingName() {
        Branch existing = entity(3L, "Chi Ba", null, 0);
        when(branchRepository.findSibling(null, "Chi Ba")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> branches.create(request("Chi Ba", null), ACTOR))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("a branch cannot be reparented under itself or one of its own descendants")
    void refusesACycle() {
        Branch branch = entity(1L, "Chi Hai", null, 0);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(branchRepository.existsById(2L)).thenReturn(true);
        when(branchRepository.findIdsWithDescendants(1L)).thenReturn(List.of(1L, 2L));

        assertThatThrownBy(() -> branches.update(1L, request("Chi Hai", 2L), ACTOR))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("an edit made from a stale version is refused before anything else is checked")
    void refusesAStaleEdit() {
        Branch branch = entity(1L, "Chi Hai", null, 3);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> branches.update(
                1L, new BranchRequest("Chi Hai", null, null, null, 1L), ACTOR))
                .isInstanceOf(ConflictException.class);
        verify(branchRepository, never()).flush();
    }

    @Test
    @DisplayName("deleting a branch with sub-branches is refused, naming the count")
    void refusesDeletingABranchWithChildren() {
        Branch branch = entity(1L, "Chi Hai", null, 0);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(branchRepository.countByParentId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> branches.delete(1L, ACTOR, "dọn dẹp"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 chi con");
        verify(branchRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleting an empty branch records its last state in the audit trail")
    void deletesAnEmptyBranch() {
        Branch branch = entity(1L, "Chi Hai", null, 0);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(branchRepository.countByParentId(1L)).thenReturn(0L);

        branches.delete(1L, ACTOR, "dọn dẹp");

        verify(branchRepository).delete(branch);
        verify(auditService).record(
                eq(AuditEntityType.BRANCH), eq(1L), eq(AuditAction.DELETE), any(), isNull(), eq(ACTOR),
                eq("dọn dẹp"));
    }

    @Test
    @DisplayName("findOrCreate reuses a sibling of the same name instead of making a duplicate")
    void findOrCreateReusesAnExistingSibling() {
        Branch existing = entity(7L, "Chi Ba", null, 0);
        when(branchRepository.findSibling(null, "Chi Ba")).thenReturn(Optional.of(existing));

        Long id = branches.findOrCreate("Chi Ba", null, ACTOR);

        assertThat(id).isEqualTo(7L);
        verify(branchRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("findOrCreate creates a branch when no sibling of that name exists")
    void findOrCreateMakesANewBranch() {
        when(branchRepository.findSibling(null, "Chi Ba")).thenReturn(Optional.empty());
        when(branchRepository.saveAndFlush(any(Branch.class))).thenAnswer(call -> {
            Branch saved = call.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        Long id = branches.findOrCreate("Chi Ba", null, ACTOR);

        assertThat(id).isEqualTo(8L);
    }

    @Test
    @DisplayName("findOrCreatePath walks a root-first path level by level, one findOrCreate per level")
    void findOrCreatePathWalksEachLevel() {
        Branch root = entity(1L, "Chi Hai", null, 0);
        when(branchRepository.findSibling(null, "Chi Hai")).thenReturn(Optional.of(root));
        Branch leaf = entity(2L, "Chi Hai - Phái Hai", 1L, 0);
        when(branchRepository.findSibling(1L, "Chi Hai - Phái Hai")).thenReturn(Optional.of(leaf));

        Optional<Long> found = branches.findOrCreatePath(List.of("Chi Hai", "Chi Hai - Phái Hai"), ACTOR);

        assertThat(found).contains(2L);
    }

    @Test
    @DisplayName("findOrCreatePath on an all-blank path creates nothing and returns empty")
    void findOrCreatePathOnABlankPathReturnsEmpty() {
        Optional<Long> found = branches.findOrCreatePath(List.of("", "  "), ACTOR);

        assertThat(found).isEmpty();
        verify(branchRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("getById fails for an unknown branch rather than returning something empty")
    void getByIdFailsForAnUnknownBranch() {
        when(branchRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> branches.getById(404L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("findWithDescendants delegates to the recursive query")
    void findWithDescendantsDelegates() {
        when(branchRepository.findIdsWithDescendants(1L)).thenReturn(List.of(1L, 2L, 3L));

        assertThat(branches.findWithDescendants(1L)).isEqualTo(Set.of(1L, 2L, 3L));
    }
}
