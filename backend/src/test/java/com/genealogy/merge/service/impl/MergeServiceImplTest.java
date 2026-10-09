package com.genealogy.merge.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.MediaTargetType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.ReassignResult;
import com.genealogy.family.dto.response.SharedUnion;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.dto.response.DroppedGrave;
import com.genealogy.grave.service.GraveService;
import com.genealogy.media.service.MediaService;
import com.genealogy.merge.dto.request.MergeRequest;
import com.genealogy.merge.dto.response.MergeResponse;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.purge.dto.response.RowsForgotten;
import com.genealogy.purge.service.PurgeService;
import com.genealogy.source.dto.request.SourceMergeRequest;
import com.genealogy.source.dto.response.CitationsMoved;
import com.genealogy.source.dto.response.SourceMergeResponse;
import com.genealogy.source.service.SourceService;
import com.genealogy.suggestion.dto.response.SuggestionsMoved;
import com.genealogy.suggestion.service.SuggestionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the merge orchestration (docs/analysis.md F11). */
@ExtendWith(MockitoExtension.class)
class MergeServiceImplTest {

    private static final Long KEEP = 1L;
    private static final Long DUPLICATE = 2L;
    private static final Long ACTOR = 9L;
    private static final String REASON = "Cùng một cụ, đối chiếu gia phả chữ Hán";

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    @Mock
    private EventService eventService;

    @Mock
    private GraveService graveService;

    @Mock
    private SourceService sourceService;

    @Mock
    private SuggestionService suggestionService;

    @Mock
    private MediaService mediaService;

    @Mock
    private PurgeService purgeService;

    @Mock
    private AdvisoryLock advisoryLock;

    private MergeServiceImpl merges;

    @BeforeEach
    void setUp() {
        merges = new MergeServiceImpl(
                personService, familyService, eventService, graveService, sourceService, suggestionService,
                mediaService, purgeService, advisoryLock);
    }

    @Test
    @DisplayName("the parentage lock is taken before the cycle and shared-union checks, not after them")
    void locksBeforeChecking() {
        givenTwoMergeablePeople();

        merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        // The lock used to be first taken inside reassignPerson, after both checks had already passed (§8.10 #4).
        InOrder order = inOrder(advisoryLock, familyService);
        order.verify(advisoryLock).lock(AdvisoryLock.PARENTAGE);
        order.verify(familyService).findSharedUnions(KEEP, DUPLICATE);
        order.verify(familyService).inOneLineOfDescent(anyLong(), anyLong());
    }

    /**
     * Builds the request under test.
     *
     * @param targetId the person to keep
     * @param duplicateId the person to absorb
     * @return the request
     */
    private static MergeRequest request(Long targetId, Long duplicateId) {
        return new MergeRequest(targetId, duplicateId, REASON);
    }

    /** Stubs the happy path so a test can assert one thing about it. */
    private void givenTwoMergeablePeople() {
        when(personService.exists(anyLong())).thenReturn(true);
        when(familyService.findSharedUnions(KEEP, DUPLICATE)).thenReturn(List.of());
        when(familyService.inOneLineOfDescent(anyLong(), anyLong())).thenReturn(false);
        when(familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new ReassignResult(
                        1, 1, 2, Map.of(), List.of(), List.of("đã gộp union")));
        when(eventService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON)).thenReturn(3);
        when(graveService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON)).thenReturn(Optional.empty());
        when(sourceService.reassignTarget(CitationTargetType.PERSON, DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new CitationsMoved(4, List.of()));
        when(suggestionService.reassignTarget(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new SuggestionsMoved(0, 0));
        when(personService.absorb(eq(KEEP), eq(DUPLICATE), eq(ACTOR), any())).thenReturn(2);
        when(personService.getById(eq(KEEP), any()))
                .thenReturn(new PersonDetailResponse(
                        KEEP, "Nguyễn Văn Ánh", Gender.MALE, 1, null, false, null, List.of(), Instant.now(), 0));
    }

    @Test
    @DisplayName("a merge reports what each feature moved")
    void reportsWhatMoved() {
        givenTwoMergeablePeople();

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        assertThat(response.targetId()).isEqualTo(KEEP);
        assertThat(response.targetName()).isEqualTo("Nguyễn Văn Ánh");
        assertThat(response.namesMoved()).isEqualTo(2);
        assertThat(response.unionsMoved()).isEqualTo(1);
        assertThat(response.unionsCollapsed()).isEqualTo(1);
        assertThat(response.childLinksMoved()).isEqualTo(2);
        assertThat(response.eventsMoved()).isEqualTo(3);
        assertThat(response.citationsMoved()).isEqualTo(4);
        assertThat(response.notes()).containsExactly("đã gộp union");
    }

    @Test
    @DisplayName("everything is repointed before the person row is deleted")
    void repointsBeforeDeleting() {
        givenTwoMergeablePeople();

        merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        // family_children and graves cascade on person delete, so absorbing first loses them silently.
        InOrder order = inOrder(
                familyService, eventService, graveService, sourceService, suggestionService,
                mediaService, personService);
        order.verify(familyService).reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);
        order.verify(eventService).reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);
        order.verify(graveService).reassignPerson(DUPLICATE, KEEP, ACTOR, REASON);
        order.verify(sourceService).reassignTarget(CitationTargetType.PERSON, DUPLICATE, KEEP, ACTOR, REASON);
        // Every feature that owns rows naming the duplicate is listed, or a forgotten one goes unnoticed:
        order.verify(suggestionService).reassignTarget(DUPLICATE, KEEP, ACTOR, REASON);
        order.verify(mediaService).reassignTarget(MediaTargetType.PERSON, DUPLICATE, KEEP, ACTOR, REASON);
        order.verify(personService).absorb(eq(KEEP), eq(DUPLICATE), eq(ACTOR), any());
    }

    @Test
    @DisplayName("đời is recomputed, because the parentage graph just changed shape")
    void recomputesGenerations() {
        givenTwoMergeablePeople();

        merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        verify(familyService).recomputeGenerations();
    }

    @Test
    @DisplayName("the reason reaches the audit trail as the basis for the change")
    void passesTheReasonToTheAuditTrail() {
        givenTwoMergeablePeople();

        merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        verify(personService).absorb(KEEP, DUPLICATE, ACTOR, "Cùng một cụ, đối chiếu gia phả chữ Hán");
    }

    @Test
    @DisplayName("a grave that had to be dropped is reported, not swallowed")
    void reportsADroppedGrave() {
        givenTwoMergeablePeople();
        when(graveService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(Optional.of(new DroppedGrave(77L, "Bỏ mộ phần của bản trùng: khu B")));
        when(purgeService.forgetGraveRows(77L, ACTOR, REASON)).thenReturn(new RowsForgotten(0, 0, 0));

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        assertThat(response.notes()).contains("Bỏ mộ phần của bản trùng: khu B");
    }

    @Test
    @DisplayName("a dropped grave's own photos and citations are forgotten too, or nothing would ever reclaim them")
    void forgetsTheDroppedGravesRows() {
        givenTwoMergeablePeople();
        when(graveService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(Optional.of(new DroppedGrave(77L, "Bỏ mộ phần của bản trùng: khu B")));
        when(purgeService.forgetGraveRows(77L, ACTOR, REASON)).thenReturn(new RowsForgotten(0, 0, 2));

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        // The grave row is gone, but nothing FKs media or citations to it, so both would orphan silently.
        verify(purgeService).forgetGraveRows(77L, ACTOR, REASON);
        assertThat(response.notes()).anyMatch(note -> note.contains("2 tệp"));
    }

    @Test
    @DisplayName("a citation folded into the survivor's copy is named in the notes, not dropped in silence")
    void reportsAFoldedCitation() {
        givenTwoMergeablePeople();
        when(sourceService.reassignTarget(CitationTargetType.PERSON, DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new CitationsMoved(3, List.of("Dẫn chứng #8 trùng với #5, đã gộp vào đó")));

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        assertThat(response.citationsMoved()).isEqualTo(3);
        assertThat(response.notes()).contains("Dẫn chứng #8 trùng với #5, đã gộp vào đó");
    }

    @Test
    @DisplayName("a grave that moved rather than dropped keeps its media, so nothing is forgotten")
    void keepsAMovedGravesMedia() {
        givenTwoMergeablePeople();

        merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        verify(purgeService, never()).forgetGraveRows(anyLong(), any(), any());
    }

    @Test
    @DisplayName("merging a person into themselves is refused")
    void refusesSelfMerge() {
        assertThatThrownBy(() -> merges.mergePersons(request(KEEP, KEEP), ACTOR))
                .isInstanceOf(BadRequestException.class);
        verify(familyService, never()).reassignPerson(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("merging an ancestor with a descendant is refused before anything moves")
    void refusesAncestorMerge() {
        when(personService.exists(anyLong())).thenReturn(true);
        when(familyService.findSharedUnions(KEEP, DUPLICATE)).thenReturn(List.of());
        when(familyService.inOneLineOfDescent(KEEP, DUPLICATE)).thenReturn(true);

        assertThatThrownBy(() -> merges.mergePersons(request(KEEP, DUPLICATE), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vòng lặp");
        // Nothing may have moved: the check exists so the transaction never has to roll damage back.
        verify(familyService, never()).reassignPerson(anyLong(), anyLong(), any(), any());
        verify(personService, never()).absorb(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("a folded union's events, citations and photos follow it to the union that survived")
    void rowsKeyedOnAFoldedUnionFollowIt() {
        givenTwoMergeablePeople();
        when(familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new ReassignResult(1, 1, 2, Map.of(6L, 5L), List.of(), List.of()));
        when(sourceService.reassignTarget(CitationTargetType.FAMILY, 6L, 5L, ACTOR, REASON))
                .thenReturn(new CitationsMoved(1, List.of()));

        merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        // None of the three tables has an FK to `families`, so nothing else would ever catch this.
        verify(eventService).reassignFamily(6L, 5L, ACTOR, REASON);
        verify(sourceService).reassignTarget(CitationTargetType.FAMILY, 6L, 5L, ACTOR, REASON);
        verify(mediaService).reassignTarget(MediaTargetType.FAMILY, 6L, 5L, ACTOR, REASON);
    }

    @Test
    @DisplayName("a dropped union's rows are deleted and the count is reported, not swallowed")
    void rowsKeyedOnADroppedUnionAreReported() {
        givenTwoMergeablePeople();
        when(familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new ReassignResult(1, 0, 2, Map.of(), List.of(9L), List.of()));
        when(purgeService.forgetUnionRows(9L, ACTOR, REASON)).thenReturn(new RowsForgotten(1, 2, 3));

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        // The event's own citation has no other cleanup path once the union it hung off of is dropped.
        assertThat(response.notes())
                .anyMatch(note -> note.contains("#9") && note.contains("1 sự kiện")
                        && note.contains("2 trích dẫn") && note.contains("3 tệp"));
    }

    @Test
    @DisplayName("the duplicate's photos come across, because media has no FK to hold them")
    void mediaFollowsThePerson() {
        givenTwoMergeablePeople();
        when(mediaService.reassignTarget(MediaTargetType.PERSON, DUPLICATE, KEEP, ACTOR, REASON)).thenReturn(3);

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        assertThat(response.mediaMoved()).isEqualTo(3);
    }

    @Test
    @DisplayName("a folded union's photos count toward the files moved, not only the person's own")
    void countsAFoldedUnionsMedia() {
        givenTwoMergeablePeople();
        when(familyService.reassignPerson(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new ReassignResult(1, 1, 2, Map.of(6L, 5L), List.of(), List.of()));
        when(sourceService.reassignTarget(CitationTargetType.FAMILY, 6L, 5L, ACTOR, REASON))
                .thenReturn(new CitationsMoved(0, List.of()));
        when(mediaService.reassignTarget(MediaTargetType.FAMILY, 6L, 5L, ACTOR, REASON)).thenReturn(4);
        when(mediaService.reassignTarget(MediaTargetType.PERSON, DUPLICATE, KEEP, ACTOR, REASON)).thenReturn(1);

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        assertThat(response.mediaMoved()).isEqualTo(5);
    }

    @Test
    @DisplayName("pending edits written for the duplicate are reported as turned into notes")
    void reportsSuggestionsTurnedIntoNotes() {
        givenTwoMergeablePeople();
        when(suggestionService.reassignTarget(DUPLICATE, KEEP, ACTOR, REASON))
                .thenReturn(new SuggestionsMoved(3, 2));

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        assertThat(response.notes())
                .anyMatch(note -> note.contains("3 đề xuất") && note.contains("2 đề xuất sửa"));
    }

    @Test
    @DisplayName("merging two sources carries the duplicate's scans onto the kept one")
    void sourceMergeMovesTheScans() {
        SourceMergeRequest request = new SourceMergeRequest(6L, 5L, REASON);
        when(sourceService.merge(request, ACTOR)).thenReturn(new SourceMergeResponse(null, 2, 0, List.of()));
        when(mediaService.reassignTarget(MediaTargetType.SOURCE, 6L, 5L, ACTOR, REASON)).thenReturn(80);

        SourceMergeResponse response = merges.mergeSources(request, ACTOR);

        assertThat(response.citationsMoved()).isEqualTo(2);
        assertThat(response.mediaMoved()).isEqualTo(80);
    }

    @Test
    @DisplayName("a merge that would orphan the children of a wrongly recorded marriage is refused")
    void refusesWhenTheSharedUnionHasChildren() {
        when(personService.exists(anyLong())).thenReturn(true);
        when(familyService.findSharedUnions(KEEP, DUPLICATE)).thenReturn(List.of(new SharedUnion(7L, 3)));

        assertThatThrownBy(() -> merges.mergePersons(request(KEEP, DUPLICATE), ACTOR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("#7")
                .hasMessageContaining("3 người con");
        // family_children cascades on delete, so dropping that union would take real parentage with it.
        verify(familyService, never()).reassignPerson(anyLong(), anyLong(), any(), any());
        verify(personService, never()).absorb(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("a wrongly recorded marriage with no children is still merged, as before")
    void mergesWhenTheSharedUnionIsChildless() {
        givenTwoMergeablePeople();
        when(familyService.findSharedUnions(KEEP, DUPLICATE)).thenReturn(List.of(new SharedUnion(7L, 0)));

        MergeResponse response = merges.mergePersons(request(KEEP, DUPLICATE), ACTOR);

        assertThat(response.duplicateId()).isEqualTo(DUPLICATE);
        verify(personService).absorb(eq(KEEP), eq(DUPLICATE), eq(ACTOR), any());
    }

    @Test
    @DisplayName("a merge naming someone who does not exist is refused")
    void refusesUnknownPerson() {
        when(personService.exists(KEEP)).thenReturn(false);

        assertThatThrownBy(() -> merges.mergePersons(request(KEEP, DUPLICATE), ACTOR))
                .isInstanceOf(BadRequestException.class);
        verify(familyService, never()).reassignPerson(anyLong(), anyLong(), any(), any());
    }
}
