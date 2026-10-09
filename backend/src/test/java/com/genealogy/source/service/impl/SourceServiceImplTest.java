package com.genealogy.source.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.Role;
import com.genealogy.common.model.SourceType;
import com.genealogy.event.service.EventService;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.service.PersonService;
import com.genealogy.source.domain.Citation;
import com.genealogy.source.domain.Source;
import com.genealogy.source.dto.request.CitationRequest;
import com.genealogy.source.dto.request.SourceMergeRequest;
import com.genealogy.source.dto.request.SourceRequest;
import com.genealogy.source.dto.response.CitationResponse;
import com.genealogy.source.dto.response.CitationsMoved;
import com.genealogy.source.dto.response.SourceMergeResponse;
import com.genealogy.source.mapper.SourceMapper;
import com.genealogy.source.repository.CitationRepository;
import com.genealogy.source.repository.SourceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Unit tests for sources and citations: who may read them, and that no quote is lost on the way (§8.8). */
@ExtendWith(MockitoExtension.class)
class SourceServiceImplTest {

    private static final Long ACTOR = 9L;
    private static final Long PERSON = 42L;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private CitationRepository citationRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    @Mock
    private EventService eventService;

    @Mock
    private GraveService graveService;

    private SourceServiceImpl sources;

    @BeforeEach
    void setUp() {
        sources = new SourceServiceImpl(sourceRepository, citationRepository, Mappers.getMapper(SourceMapper.class),
                auditService, personService, familyService, eventService, graveService);
    }

    /**
     * Builds a saved source entity.
     *
     * @param id source id
     * @param title its title
     * @return the entity
     */
    private static Source source(Long id, String title) {
        Source source = new Source();
        source.setId(id);
        source.setTitle(title);
        source.setType(SourceType.CLAN_BOOK);
        return source;
    }

    /**
     * Builds a saved citation entity.
     *
     * @param id citation id
     * @param sourceId the source it names
     * @param targetId the person it backs up
     * @param locator where in the source, or null
     * @param quote what it quotes, or null
     * @return the entity
     */
    private static Citation citation(Long id, Long sourceId, Long targetId, String locator, String quote) {
        Citation citation = new Citation();
        citation.setId(id);
        citation.setSourceId(sourceId);
        citation.setTargetType(CitationTargetType.PERSON);
        citation.setTargetId(targetId);
        citation.setLocator(locator);
        citation.setQuote(quote);
        return citation;
    }

    /**
     * Builds a citation request against one person.
     *
     * @param sourceId the source to cite, or null
     * @param newSource a source to create, or null
     * @param locator where in the source
     * @return the request
     */
    private static CitationRequest cite(Long sourceId, SourceRequest newSource, String locator) {
        return new CitationRequest(
                sourceId, newSource, CitationTargetType.PERSON, PERSON, locator, "Sinh năm Canh Tý", "vì sao", null);
    }

    @Test
    @DisplayName("a MEMBER cannot list sources; the service refuses even if a route forgets to (§8.8 D3)")
    void refusesAMemberTheSourceList() {
        assertThatThrownBy(() -> sources.search(null, PageRequest.of(0, 20), Role.MEMBER))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> sources.getById(1L, Role.MEMBER)).isInstanceOf(ForbiddenException.class);
        verify(sourceRepository, never()).findAllByOrderByTitleAscIdAsc(any());
    }

    @Test
    @DisplayName("an EDITOR lists sources by title, with a page size capped and counts read only for the page")
    void listsSourcesForAnEditor() {
        when(sourceRepository.findAllByOrderByTitleAscIdAsc(PageRequest.of(0, 100)))
                .thenReturn(new PageImpl<>(List.of(source(1L, "Gia phả cũ")), PageRequest.of(0, 100), 1));
        when(citationRepository.countGroupedBySource(List.of(1L))).thenReturn(List.<Object[]>of(new Object[] {1L, 4L}));

        var page = sources.search(null, PageRequest.of(0, 5000), Role.EDITOR);

        assertThat(page.getContent()).singleElement()
                .satisfies(row -> assertThat(row.citationCount()).isEqualTo(4L));
    }

    @Test
    @DisplayName("a search escapes LIKE wildcards, so `%` finds a percent sign rather than every source")
    void escapesTheSearch() {
        when(sourceRepository.searchByTitle(eq("100\\%"), any())).thenReturn(new PageImpl<>(List.of()));

        sources.search("100%", PageRequest.of(0, 20), Role.ADMIN);

        verify(sourceRepository).searchByTitle(eq("100\\%"), any());
    }

    @Test
    @DisplayName("a MEMBER gets no citations of a sinh phần or a living person's event (§3.6)")
    void hidesLivingCitationsFromAMember() {
        when(graveService.involvesLiving(4L)).thenReturn(true);
        when(eventService.involvesLiving(7L)).thenReturn(true);

        assertThat(sources.findCitations(CitationTargetType.GRAVE, 4L, Role.MEMBER)).isEmpty();
        assertThat(sources.findCitations(CitationTargetType.EVENT, 7L, Role.MEMBER)).isEmpty();
        verify(citationRepository, never()).findByTargetTypeAndTargetIdOrderByIdAsc(any(), any());
    }

    @Test
    @DisplayName("an EDITOR gets a living person's citations, and the guard is never asked")
    void showsLivingCitationsToAnEditor() {
        when(citationRepository.findByTargetTypeAndTargetIdOrderByIdAsc(CitationTargetType.PERSON, PERSON))
                .thenReturn(List.of(citation(1L, 3L, PERSON, "trang 12", null)));
        when(sourceRepository.findAllById(any())).thenReturn(List.of(source(3L, "Gia phả cũ")));

        List<CitationResponse> found = sources.findCitations(CitationTargetType.PERSON, PERSON, Role.EDITOR);

        assertThat(found).singleElement().satisfies(row -> assertThat(row.sourceTitle()).isEqualTo("Gia phả cũ"));
        verify(personService, never()).isLiving(any());
    }

    @Test
    @DisplayName("a citation of a record that does not exist is refused before anything is written")
    void refusesACitationOfNothing() {
        when(personService.exists(PERSON)).thenReturn(false);

        assertThatThrownBy(() -> sources.addCitation(cite(3L, null, null), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không có người");
        verify(citationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a citation names an existing source or a new one, never both and never neither")
    void refusesAnAmbiguousSource() {
        when(personService.exists(PERSON)).thenReturn(true);
        SourceRequest newSource = new SourceRequest("Lời kể", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> sources.addCitation(cite(3L, newSource, null), ACTOR))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> sources.addCitation(cite(null, null, null), ACTOR))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a new source and its citation are created in one call, both audited, and a blank locator is none")
    void createsTheSourceAndTheCitationTogether() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(sourceRepository.saveAndFlush(any(Source.class))).thenAnswer(call -> {
            Source saved = call.getArgument(0);
            saved.setId(3L);
            return saved;
        });
        when(citationRepository.findColliding(3L, "PERSON", PERSON, null)).thenReturn(List.of());
        when(citationRepository.saveAndFlush(any(Citation.class))).thenAnswer(call -> {
            Citation saved = call.getArgument(0);
            saved.setId(8L);
            return saved;
        });
        SourceRequest newSource = new SourceRequest("Lời kể của bà", null, null, null, null, null, null, null);

        CitationResponse created = sources.addCitation(cite(null, newSource, "   "), ACTOR);

        assertThat(created.sourceId()).isEqualTo(3L);
        assertThat(created.sourceType()).isEqualTo(SourceType.OTHER);
        // "" and null differ to uq_citations, so a blank locator would let a second no-locator citation in.
        assertThat(created.locator()).isNull();
        verify(auditService).record(eq(AuditEntityType.SOURCE), eq(3L), eq(AuditAction.CREATE), isNull(), any(),
                eq(ACTOR), eq("vì sao"));
        verify(auditService).record(AuditEntityType.CITATION, 8L, AuditAction.CREATE, null, created, ACTOR, "vì sao");
    }

    @Test
    @DisplayName("citing the same source at the same place twice is a 409 in Vietnamese, not a raw constraint")
    void refusesADuplicateCitation() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(sourceRepository.findById(3L)).thenReturn(Optional.of(source(3L, "Gia phả cũ")));
        when(citationRepository.findColliding(3L, "PERSON", PERSON, "trang 12")).thenReturn(List.of(1L));

        assertThatThrownBy(() -> sources.addCitation(cite(3L, null, "trang 12"), ACTOR))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã được trích dẫn");
    }

    @Test
    @DisplayName("a source still cited is not deleted; the refusal says to merge it instead")
    void refusesToDeleteACitedSource() {
        when(sourceRepository.findById(3L)).thenReturn(Optional.of(source(3L, "Gia phả cũ")));
        when(citationRepository.countBySourceId(3L)).thenReturn(2L);

        // Citations cascaded away with their source, so this delete used to take every "on what basis" (§3.8).
        assertThatThrownBy(() -> sources.delete(3L, ACTOR, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gộp");
        verify(sourceRepository, never()).delete(any());
    }

    @Test
    @DisplayName("merging two sources folds a colliding citation into the kept one, keeping both quotes")
    void mergeKeepsBothQuotes() {
        Citation kept = citation(1L, 3L, PERSON, "trang 12", "Sinh năm Canh Tý");
        Citation moving = citation(2L, 4L, PERSON, "trang 12", "Mất năm Ất Dậu");
        when(sourceRepository.findById(4L)).thenReturn(Optional.of(source(4L, "Gia phả chép lại")));
        when(sourceRepository.findById(3L)).thenReturn(Optional.of(source(3L, "Gia phả cũ")));
        when(citationRepository.findBySourceIdOrderByIdAsc(4L)).thenReturn(List.of(moving));
        when(citationRepository.findColliding(3L, "PERSON", PERSON, "trang 12")).thenReturn(List.of(1L));
        when(citationRepository.findById(1L)).thenReturn(Optional.of(kept));

        SourceMergeResponse response = sources.merge(new SourceMergeRequest(4L, 3L, "Cùng một cuốn"), ACTOR);

        // uq_citations forbids both rows; the quote is what the source says, so it survives the fold (§8.8 #8).
        assertThat(kept.getQuote()).contains("Sinh năm Canh Tý").contains("Mất năm Ất Dậu");
        verify(citationRepository).delete(moving);
        assertThat(response.citationsMoved()).isZero();
        assertThat(response.folded()).singleElement().asString().contains("#2").contains("#1");
        verify(sourceRepository).delete(any(Source.class));
    }

    @Test
    @DisplayName("moving a duplicate's citations repoints the ones that do not collide and reports the rest")
    void reassignRepointsAndReports() {
        Citation fresh = citation(5L, 3L, 1L, "trang 3", null);
        Citation same = citation(6L, 3L, 1L, null, null);
        when(citationRepository.findByTargetTypeAndTargetIdOrderByIdAsc(CitationTargetType.PERSON, 1L))
                .thenReturn(List.of(fresh, same));
        when(citationRepository.findColliding(3L, "PERSON", PERSON, "trang 3")).thenReturn(List.of());
        when(citationRepository.findColliding(3L, "PERSON", PERSON, null)).thenReturn(List.of(9L));
        when(citationRepository.findById(9L)).thenReturn(Optional.of(citation(9L, 3L, PERSON, null, null)));

        CitationsMoved moved = sources.reassignTarget(CitationTargetType.PERSON, 1L, PERSON, ACTOR, "gộp");

        assertThat(fresh.getTargetId()).isEqualTo(PERSON);
        assertThat(moved.moved()).isEqualTo(1);
        assertThat(moved.folded()).hasSize(1);
        verify(citationRepository).delete(same);
    }

    @Test
    @DisplayName("forgetting a record's citations records each one, so a purged cụ's quotes stay in the trail")
    void forgetRecordsEachCitation() {
        Citation gone = citation(1L, 3L, PERSON, "trang 12", "Sinh năm Canh Tý");
        when(citationRepository.findByTargetTypeAndTargetIdInOrderByIdAsc(CitationTargetType.EVENT, List.of(7L, 8L)))
                .thenReturn(List.of(gone));
        when(sourceRepository.findAllById(any())).thenReturn(List.of(source(3L, "Gia phả cũ")));

        int forgotten = sources.forgetTargets(CitationTargetType.EVENT, List.of(7L, 8L), ACTOR, "xoá người");

        assertThat(forgotten).isEqualTo(1);
        ArgumentCaptor<CitationResponse> before = ArgumentCaptor.forClass(CitationResponse.class);
        verify(auditService).record(eq(AuditEntityType.CITATION), eq(1L), eq(AuditAction.DELETE), before.capture(),
                isNull(), eq(ACTOR), eq("xoá người"));
        assertThat(before.getValue().quote()).isEqualTo("Sinh năm Canh Tý");
    }
}
