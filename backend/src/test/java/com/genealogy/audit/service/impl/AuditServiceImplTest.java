package com.genealogy.audit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.domain.Revision;
import com.genealogy.audit.dto.response.RevisionResponse;
import com.genealogy.audit.mapper.RevisionMapper;
import com.genealogy.audit.repository.RevisionRepository;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.Role;
import com.genealogy.member.service.MemberNameService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for the audit trail (CLAUDE.md §3.8). */
@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private RevisionRepository revisionRepository;

    @Mock
    private MemberNameService memberService;

    @Captor
    private ArgumentCaptor<Revision> revisionCaptor;

    private AuditServiceImpl audit;

    @BeforeEach
    void setUp() {
        RevisionMapper revisionMapper = Mappers.getMapper(RevisionMapper.class);
        audit = new AuditServiceImpl(revisionRepository, revisionMapper, memberService, new ObjectMapper());
    }

    /** A payload Jackson cannot serialise, to exercise the fallback. */
    private record Unserialisable(Object self) {
    }

    @Test
    @DisplayName("record() must join the caller's transaction, never start its own")
    void recordJoinsTheCallersTransaction() throws NoSuchMethodException {
        Method method = AuditServiceImpl.class.getMethod(
                "record", AuditEntityType.class, Long.class, AuditAction.class,
                Object.class, Object.class, Long.class, String.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        // REQUIRES_NEW left a revision behind on a rolled-back edit, so the propagation is pinned here.
        assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    @Test
    @DisplayName("a create records only the after payload")
    void createRecordsOnlyAfter() {
        audit.record(AuditEntityType.PERSON, 1L, AuditAction.CREATE, null, new Point(1, 2), 9L, "vì sao");

        verify(revisionRepository).save(revisionCaptor.capture());
        Revision saved = revisionCaptor.getValue();

        assertThat(saved.getBeforeData()).isNull();
        assertThat(saved.getAfterData()).contains("\"x\":1");
        assertThat(saved.getChangedBy()).isEqualTo(9L);
        assertThat(saved.getNote()).isEqualTo("vì sao");
    }

    @Test
    @DisplayName("a delete records only the before payload")
    void deleteRecordsOnlyBefore() {
        audit.record(AuditEntityType.PERSON, 1L, AuditAction.DELETE, new Point(3, 4), null, 9L, null);

        verify(revisionRepository).save(revisionCaptor.capture());
        Revision saved = revisionCaptor.getValue();

        assertThat(saved.getAfterData()).isNull();
        assertThat(saved.getBeforeData()).contains("\"y\":4");
    }

    @Test
    @DisplayName("Vietnamese text survives serialisation into the trail")
    void diacriticsSurvive() {
        audit.record(AuditEntityType.PERSON, 1L, AuditAction.CREATE, null, new Named("Nguyễn Văn Ánh"), 9L, null);

        verify(revisionRepository).save(revisionCaptor.capture());
        assertThat(revisionCaptor.getValue().getAfterData()).contains("Nguyễn Văn Ánh");
    }

    @Test
    @DisplayName("an unserialisable payload still leaves a row saying who and when")
    void unserialisablePayloadStillRecordsTheChange() {
        Unserialisable cyclic = new Unserialisable(new Object());

        audit.record(AuditEntityType.PERSON, 1L, AuditAction.UPDATE, null, cyclic, 9L, null);

        verify(revisionRepository).save(revisionCaptor.capture());
        Revision saved = revisionCaptor.getValue();

        // Never null: both payloads null trips ck_revisions_has_payload and rolls back a real change.
        assertThat(saved.getAfterData()).isNotNull();
        assertThat(saved.getChangedBy()).isEqualTo(9L);
    }

    @Test
    @DisplayName("a MEMBER may not read the trail, of one record or of the whole gia phả")
    void memberIsRefused() {
        assertThatThrownBy(() -> audit.findForEntity(AuditEntityType.PERSON, 1L, Role.MEMBER, PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> audit.search(null, null, Role.MEMBER, PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("an EDITOR reads the trail, newest first regardless of what the request asked for")
    void editorReadsNewestFirst() {
        Revision revision = new Revision();
        revision.setEntityType(AuditEntityType.PERSON);
        revision.setEntityId(1L);
        revision.setAction(AuditAction.UPDATE);
        revision.setAfterData("{}");
        revision.setChangedBy(9L);
        when(revisionRepository.findByEntityTypeAndEntityId(eq(AuditEntityType.PERSON), eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(revision)));
        when(memberService.findNames(Set.of(9L))).thenReturn(Map.of(9L, "Trưởng tộc"));

        Page<?> page = audit.findForEntity(AuditEntityType.PERSON, 1L, Role.EDITOR, PageRequest.of(0, 200));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(revisionRepository)
                .findByEntityTypeAndEntityId(eq(AuditEntityType.PERSON), eq(1L), pageableCaptor.capture());
        // A client sort on an unknown field was a 500 (§9); the trail has exactly one order that means anything.
        assertThat(pageableCaptor.getValue().getSort().toString()).contains("changedAt: DESC").contains("id: DESC");
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("withNames leaves a revision's author name null when no one is recorded")
    void withNamesLeavesNoAuthorNull() {
        Revision revision = new Revision();
        revision.setEntityType(AuditEntityType.BRANCH);
        revision.setEntityId(2L);
        revision.setAction(AuditAction.CREATE);
        revision.setAfterData("{}");
        revision.setChangedBy(null);
        when(revisionRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(revision)));

        Page<RevisionResponse> page = audit.search(null, null, Role.ADMIN, PageRequest.of(0, 10));

        assertThat(page.getContent().getFirst().changedByName()).isNull();
    }

    @Test
    @DisplayName("an account's history, which carries its email, is the ADMIN's alone")
    void memberHistoryIsAdminOnly() {
        assertThatThrownBy(() -> audit.findForEntity(AuditEntityType.MEMBER, 1L, Role.EDITOR, PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> audit.search(AuditEntityType.MEMBER, null, Role.EDITOR, PageRequest.of(0, 10)))
                .isInstanceOf(ForbiddenException.class);
        when(revisionRepository.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        audit.search(null, null, Role.EDITOR, PageRequest.of(0, 10));
        audit.search(null, null, Role.ADMIN, PageRequest.of(0, 10));

        // The whole-clan feed leaves accounts out for an EDITOR rather than refusing the whole page.
        verify(revisionRepository).search(eq(null), eq(null), eq(AuditEntityType.MEMBER), any());
        verify(revisionRepository).search(eq(null), eq(null), eq(null), any());
    }

    /** A trivially serialisable payload. */
    private record Point(int x, int y) {
    }

    /** A payload carrying Vietnamese text. */
    private record Named(String displayName) {
    }
}
