package com.genealogy.purge.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.branch.dto.response.BranchResponse;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.MediaTargetType;
import com.genealogy.common.model.PlaceType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.SharedUnion;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.media.service.MediaService;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.dto.response.PlaceResponse;
import com.genealogy.place.service.PlaceService;
import com.genealogy.purge.dto.response.PurgeResponse;
import com.genealogy.source.service.SourceService;
import com.genealogy.suggestion.service.SuggestionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for deleting a record without leaving rows that name it behind (CLAUDE.md §8.1, §8.8). */
@ExtendWith(MockitoExtension.class)
class PurgeServiceImplTest {

    private static final Long PERSON = 42L;
    private static final Long ACTOR = 1L;
    private static final Long UNION = 7L;
    private static final Long BRANCH = 3L;
    private static final Long PLACE = 11L;
    private static final Long GRAVE = 99L;
    private static final String NOTE = "Nhập trùng từ file GEDCOM";

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    @Mock
    private EventService eventService;

    @Mock
    private SourceService sourceService;

    @Mock
    private SuggestionService suggestionService;

    @Mock
    private MediaService mediaService;

    @Mock
    private BranchService branchService;

    @Mock
    private GraveService graveService;

    @Mock
    private PlaceService placeService;

    @Mock
    private AdvisoryLock advisoryLock;

    private PurgeServiceImpl purges;

    @BeforeEach
    void setUp() {
        purges = new PurgeServiceImpl(
                personService, familyService, eventService, sourceService, suggestionService, mediaService,
                branchService, graveService, placeService, advisoryLock);
    }

    @Test
    @DisplayName("the parentage lock is taken before the orphaned-children check, not after it")
    void locksBeforeCheckingPersonParentage() {
        givenADeletablePerson();

        purges.purgePerson(PERSON, ACTOR, NOTE);

        // Checked outside the lock, a child linked meanwhile lost their only recorded parent (§8.10 #4).
        InOrder order = inOrder(advisoryLock, familyService);
        order.verify(advisoryLock).lock(AdvisoryLock.PARENTAGE);
        order.verify(familyService).soleParentUnionsWithChildren(PERSON);
    }

    @Test
    @DisplayName("the parentage lock is taken before a union's children are counted")
    void locksBeforeCountingUnionChildren() {
        when(familyService.exists(UNION)).thenReturn(true);
        when(familyService.childCount(UNION)).thenReturn(0);

        purges.purgeUnion(UNION, ACTOR, NOTE);

        InOrder order = inOrder(advisoryLock, familyService);
        order.verify(advisoryLock).lock(AdvisoryLock.PARENTAGE);
        order.verify(familyService).childCount(UNION);
    }

    /** Stubs a person nobody's parentage depends on, and no ids of their own to look up first. */
    private void givenADeletablePerson() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(familyService.soleParentUnionsWithChildren(PERSON)).thenReturn(List.of());
        lenient().when(eventService.findIdsBySubject(any(), any())).thenReturn(List.of());
        lenient().when(graveService.idOf(PERSON)).thenReturn(Optional.empty());
    }

    /**
     * Builds a place the purge can name in its refusal.
     *
     * @return the place
     */
    private static PlaceResponse place() {
        return new PlaceResponse(PLACE, "Xã Hoằng Lộc", PlaceType.WARD, null, null, null, "Xã Hoằng Lộc", 0);
    }

    @Test
    @DisplayName("deleting a person takes the rows of all four FK-less tables with them")
    void deletesEveryRowThatNamesThem() {
        givenADeletablePerson();
        when(mediaService.forgetTarget(MediaTargetType.PERSON, PERSON, ACTOR, NOTE)).thenReturn(8);
        when(eventService.forgetSubject(EventSubjectType.PERSON, PERSON, ACTOR, NOTE)).thenReturn(3);
        when(sourceService.forgetTarget(CitationTargetType.PERSON, PERSON, ACTOR, NOTE)).thenReturn(2);
        when(suggestionService.forgetTarget(PERSON, ACTOR, NOTE)).thenReturn(1);
        when(familyService.dropUnionsWithOnlyThisPartner(PERSON, ACTOR, NOTE)).thenReturn(List.of(UNION));

        PurgeResponse response = purges.purgePerson(PERSON, ACTOR, NOTE);

        assertThat(response.mediaDeleted()).isEqualTo(8);
        assertThat(response.eventsDeleted()).isEqualTo(3);
        assertThat(response.citationsDeleted()).isEqualTo(2);
        assertThat(response.suggestionsDeleted()).isEqualTo(1);
        assertThat(response.unionsDeleted()).isEqualTo(1);
        verify(personService).delete(PERSON, ACTOR, NOTE);
    }

    @Test
    @DisplayName("a union dropped with the person takes its own events, citations and photos along")
    void droppedUnionsTakeTheirRowsAlong() {
        givenADeletablePerson();
        when(familyService.dropUnionsWithOnlyThisPartner(PERSON, ACTOR, NOTE)).thenReturn(List.of(UNION));
        // Lenient: the same methods are called first for the person, with arguments these stubs do not match.
        lenient().when(eventService.forgetSubject(EventSubjectType.FAMILY, UNION, ACTOR, NOTE)).thenReturn(1);
        lenient().when(mediaService.forgetTarget(MediaTargetType.FAMILY, UNION, ACTOR, NOTE)).thenReturn(2);

        PurgeResponse response = purges.purgePerson(PERSON, ACTOR, NOTE);

        // Named by id with no FK, so until 2026-09-24 a dropped union's ngày cưới and wedding photo stayed behind.
        verify(sourceService).forgetTarget(CitationTargetType.FAMILY, UNION, ACTOR, NOTE);
        assertThat(response.eventsDeleted()).isEqualTo(1);
        assertThat(response.mediaDeleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("deleting a union clears its events, citations and media before the union row goes")
    void purgeUnionClearsItsRowsFirst() {
        when(familyService.exists(UNION)).thenReturn(true);
        when(familyService.childCount(UNION)).thenReturn(0);

        purges.purgeUnion(UNION, ACTOR, NOTE);

        InOrder order = inOrder(mediaService, eventService, sourceService, familyService);
        order.verify(eventService).forgetSubject(EventSubjectType.FAMILY, UNION, ACTOR, NOTE);
        order.verify(sourceService).forgetTarget(CitationTargetType.FAMILY, UNION, ACTOR, NOTE);
        order.verify(mediaService).forgetTarget(MediaTargetType.FAMILY, UNION, ACTOR, NOTE);
        // The reason reaches the union's DELETE revision, not only the log line (§3.8).
        order.verify(familyService).delete(UNION, ACTOR, NOTE);
    }

    @Test
    @DisplayName("deleting a union that does not exist is a 404 and forgets nothing")
    void purgeUnionRefusesAnUnknownUnion() {
        when(familyService.exists(UNION)).thenReturn(false);

        assertThatThrownBy(() -> purges.purgeUnion(UNION, ACTOR, NOTE)).isInstanceOf(NotFoundException.class);
        verify(mediaService, never()).forgetTarget(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("deleting a union that still has children is refused, like purging its sole parent")
    void purgeUnionRefusesWhenChildrenRemain() {
        when(familyService.exists(UNION)).thenReturn(true);
        when(familyService.childCount(UNION)).thenReturn(2);

        assertThatThrownBy(() -> purges.purgeUnion(UNION, ACTOR, NOTE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2 người con");
        verify(familyService, never()).delete(anyLong(), any(), any());
    }

    @Test
    @DisplayName("a person's grave is deleted with its photos and citations before the person, not left to cascade")
    void purgePersonDeletesTheGraveFirst() {
        givenADeletablePerson();
        when(graveService.idOf(PERSON)).thenReturn(Optional.of(GRAVE));
        // Lenient: PERSON media is called with a different key in the same test and is not itself under test here.
        lenient().when(mediaService.forgetTarget(MediaTargetType.GRAVE, GRAVE, ACTOR, NOTE)).thenReturn(4);
        lenient().when(sourceService.forgetTarget(CitationTargetType.GRAVE, GRAVE, ACTOR, NOTE)).thenReturn(1);

        PurgeResponse response = purges.purgePerson(PERSON, ACTOR, NOTE);

        assertThat(response.mediaDeleted()).isEqualTo(4);
        assertThat(response.citationsDeleted()).isEqualTo(1);
        // Through the grave feature, so the grave's DELETE lands in the trail instead of vanishing in a cascade.
        InOrder order = inOrder(graveService, personService);
        order.verify(graveService).delete(PERSON, ACTOR, NOTE);
        order.verify(personService).delete(PERSON, ACTOR, NOTE);
    }

    @Test
    @DisplayName("a citation naming one of the person's own events is forgotten with the event, in one call")
    void purgePersonForgetsItsEventsCitations() {
        givenADeletablePerson();
        when(eventService.findIdsBySubject(EventSubjectType.PERSON, PERSON)).thenReturn(List.of(5L, 6L));
        when(eventService.forgetSubject(EventSubjectType.PERSON, PERSON, ACTOR, NOTE)).thenReturn(2);
        when(sourceService.forgetTargets(CitationTargetType.EVENT, List.of(5L, 6L), ACTOR, NOTE)).thenReturn(2);

        PurgeResponse response = purges.purgePerson(PERSON, ACTOR, NOTE);

        // Event citations have no other cleanup path: a citation keys on an event id with no foreign key.
        assertThat(response.citationsDeleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("deleting an event forgets its own citations first")
    void purgeEventForgetsItsCitationsFirst() {
        when(eventService.exists(5L)).thenReturn(true);

        purges.purgeEvent(5L, ACTOR, NOTE);

        InOrder order = inOrder(sourceService, eventService);
        order.verify(sourceService).forgetTarget(CitationTargetType.EVENT, 5L, ACTOR, NOTE);
        order.verify(eventService).delete(5L, ACTOR, NOTE);
    }

    @Test
    @DisplayName("deleting an event that does not exist is a 404 and forgets nothing")
    void purgeEventRefusesAnUnknownEvent() {
        when(eventService.exists(5L)).thenReturn(false);

        assertThatThrownBy(() -> purges.purgeEvent(5L, ACTOR, NOTE)).isInstanceOf(NotFoundException.class);
        verify(sourceService, never()).forgetTarget(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("deleting a chi that still has members is refused before anything is touched")
    void purgeBranchRefusesWhenMembersRemain() {
        when(branchService.getById(BRANCH)).thenReturn(new BranchResponse(BRANCH, "Chi Hai", null, null, 0L));
        when(personService.countInBranch(BRANCH)).thenReturn(5L);

        assertThatThrownBy(() -> purges.purgeBranch(BRANCH, ACTOR, NOTE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Chi Hai")
                .hasMessageContaining("5 người");
        verify(branchService, never()).delete(anyLong(), any(), any());
    }

    @Test
    @DisplayName("deleting an empty chi goes through to the branch feature")
    void purgeBranchDeletesAnEmptyBranch() {
        when(branchService.getById(BRANCH)).thenReturn(new BranchResponse(BRANCH, "Chi Hai", null, null, 0L));
        when(personService.countInBranch(BRANCH)).thenReturn(0L);

        purges.purgeBranch(BRANCH, ACTOR, NOTE);

        verify(branchService).delete(BRANCH, ACTOR, NOTE);
    }

    @Test
    @DisplayName("deleting a place that an event or a grave still names is refused, naming both counts")
    void purgePlaceRefusesWhileNamed() {
        when(placeService.getById(PLACE)).thenReturn(place());
        when(eventService.countByPlace(PLACE)).thenReturn(3L);
        when(graveService.countByPlace(PLACE)).thenReturn(1L);

        // Both FKs were SET NULL, so this delete used to erase where cụ was born and buried (§8.8 #6).
        assertThatThrownBy(() -> purges.purgePlace(PLACE, ACTOR, NOTE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Xã Hoằng Lộc")
                .hasMessageContaining("3 sự kiện")
                .hasMessageContaining("1 mộ phần");
        verify(placeService, never()).delete(anyLong(), any(), any());
    }

    @Test
    @DisplayName("deleting a place nothing names goes through to the place feature")
    void purgePlaceDeletesAnUnusedPlace() {
        when(placeService.getById(PLACE)).thenReturn(place());
        when(eventService.countByPlace(PLACE)).thenReturn(0L);
        when(graveService.countByPlace(PLACE)).thenReturn(0L);

        purges.purgePlace(PLACE, ACTOR, NOTE);

        verify(placeService).delete(PLACE, ACTOR, NOTE);
    }

    @Test
    @DisplayName("deleting a grave forgets its photos and citations before the grave row goes")
    void purgeGraveClearsItsRowsFirst() {
        when(graveService.idOf(PERSON)).thenReturn(Optional.of(GRAVE));

        purges.purgeGrave(PERSON, ACTOR, NOTE);

        // Until 2026-09-28 the grave's photos stayed in storage and in `media` after the grave was removed (§8.8 #3).
        InOrder order = inOrder(mediaService, sourceService, graveService);
        order.verify(mediaService).forgetTarget(MediaTargetType.GRAVE, GRAVE, ACTOR, NOTE);
        order.verify(sourceService).forgetTarget(CitationTargetType.GRAVE, GRAVE, ACTOR, NOTE);
        order.verify(graveService).delete(PERSON, ACTOR, NOTE);
    }

    @Test
    @DisplayName("deleting a source that still holds scans is refused, so the pages are never lost with it")
    void purgeSourceRefusesWhileItHasScans() {
        when(mediaService.countByTarget(MediaTargetType.SOURCE, 4L)).thenReturn(80);

        assertThatThrownBy(() -> purges.purgeSource(4L, ACTOR, NOTE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("80 tệp");
        verify(sourceService, never()).delete(anyLong(), any(), any());
    }

    @Test
    @DisplayName("deleting a source with no scans goes through to the source feature")
    void purgeSourceDeletesAnEmptySource() {
        when(mediaService.countByTarget(MediaTargetType.SOURCE, 4L)).thenReturn(0);

        purges.purgeSource(4L, ACTOR, NOTE);

        verify(sourceService).delete(4L, ACTOR, NOTE);
    }

    @Test
    @DisplayName("deleting a grave nobody recorded is a 404 and forgets nothing")
    void purgeGraveRefusesWhenNoneRecorded() {
        when(graveService.idOf(PERSON)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purges.purgeGrave(PERSON, ACTOR, NOTE)).isInstanceOf(NotFoundException.class);
        verify(mediaService, never()).forgetTarget(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("the rows go before the person, because nothing would find them afterwards")
    void clearsTheRowsBeforeDeletingThePerson() {
        givenADeletablePerson();

        purges.purgePerson(PERSON, ACTOR, NOTE);

        // Media first of all: its rows are the only record of which storage objects to delete.
        InOrder order = inOrder(mediaService, eventService, sourceService, suggestionService, personService);
        order.verify(mediaService).forgetTarget(MediaTargetType.PERSON, PERSON, ACTOR, NOTE);
        order.verify(eventService).forgetSubject(EventSubjectType.PERSON, PERSON, ACTOR, NOTE);
        order.verify(sourceService).forgetTarget(CitationTargetType.PERSON, PERSON, ACTOR, NOTE);
        order.verify(suggestionService).forgetTarget(PERSON, ACTOR, NOTE);
        order.verify(personService).delete(PERSON, ACTOR, NOTE);
    }

    @Test
    @DisplayName("đời is recomputed, because removing a parent changes the graph's shape")
    void recomputesGenerations() {
        givenADeletablePerson();

        purges.purgePerson(PERSON, ACTOR, NOTE);

        verify(familyService).recomputeGenerations();
    }

    @Test
    @DisplayName("deleting the only recorded parent of a union with children is refused")
    void refusesWhenChildrenWouldLoseTheirOnlyParent() {
        when(personService.exists(PERSON)).thenReturn(true);
        when(familyService.soleParentUnionsWithChildren(PERSON))
                .thenReturn(List.of(new SharedUnion(7L, 3)));

        assertThatThrownBy(() -> purges.purgePerson(PERSON, ACTOR, NOTE))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("#7")
                .hasMessageContaining("3 người con");
        // family_children cascades off the union, so the refusal has to come before anything is touched.
        verify(personService, never()).delete(anyLong(), anyLong(), any());
        verify(mediaService, never()).forgetTarget(any(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("deleting someone who does not exist is refused rather than half-run")
    void refusesAnUnknownPerson() {
        when(personService.exists(PERSON)).thenReturn(false);

        assertThatThrownBy(() -> purges.purgePerson(PERSON, ACTOR, NOTE))
                .isInstanceOf(NotFoundException.class);
        verify(mediaService, never()).forgetTarget(any(), anyLong(), any(), any());
    }
}
