package com.genealogy.suggestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.CalendarType;
import com.genealogy.common.model.DateModifier;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.GenealogyDateText;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.model.Role;
import com.genealogy.event.dto.request.EventRequest;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.mapper.EventMapper;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.request.AddRelationRequest;
import com.genealogy.family.dto.response.AddRelationResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.member.service.MemberNameService;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.service.PersonService;
import com.genealogy.suggestion.domain.Suggestion;
import com.genealogy.suggestion.domain.SuggestionKind;
import com.genealogy.suggestion.domain.SuggestionStatus;
import com.genealogy.suggestion.domain.SuggestionTargetType;
import com.genealogy.suggestion.dto.request.SuggestedAnchor;
import com.genealogy.suggestion.dto.request.SuggestedPerson;
import com.genealogy.suggestion.dto.request.SuggestionRequest;
import com.genealogy.suggestion.dto.request.SuggestionReviewRequest;
import com.genealogy.suggestion.dto.response.SuggestionPreview;
import com.genealogy.suggestion.dto.response.SuggestionResponse;
import com.genealogy.suggestion.dto.response.SuggestionsMoved;
import com.genealogy.suggestion.mapper.SuggestionMapper;
import com.genealogy.suggestion.repository.SuggestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for the edit-suggestion queue (docs/analysis.md F17). */
@ExtendWith(MockitoExtension.class)
class SuggestionServiceImplTest {

    private static final Long SUGGESTER = 7L;
    private static final Long REVIEWER = 1L;
    private static final Long PERSON = 3L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private SuggestionRepository suggestionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private PersonService personService;

    @Mock
    private MemberNameService memberService;

    @Mock
    private FamilyService familyService;

    @Mock
    private EventService eventService;

    @Captor
    private ArgumentCaptor<PersonRequest> personCaptor;

    private SuggestionServiceImpl suggestions;

    @BeforeEach
    void setUp() {
        suggestions = new SuggestionServiceImpl(
                suggestionRepository, Mappers.getMapper(SuggestionMapper.class), auditService, JSON,
                personService, memberService, familyService, eventService);
        // The real rendering, so a proposal still reads as the stored date will; `event` owns it (§8.12 #44).
        EventMapper dates = Mappers.getMapper(EventMapper.class);
        lenient().when(eventService.renderDate(any()))
                .thenAnswer(call -> GenealogyDateText.of(dates.toDate(call.getArgument(0))));
        lenient().when(memberService.findNames(any())).thenReturn(Map.of(REVIEWER, "Trưởng tộc"));
        lenient().when(personService.findNodes(any())).thenReturn(List.of());
        lenient().when(suggestionRepository.saveAndFlush(any(Suggestion.class))).thenAnswer(call -> {
            Suggestion saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(5L);
            }
            return saved;
        });
        lenient().when(suggestionRepository.claim(anyLong(), any(), any(), any(), any())).thenReturn(1);
    }

    /**
     * Builds one proposed name.
     *
     * @param givenName the tên
     * @param primary whether it is flagged primary
     * @return the name
     */
    private static PersonNameRequest name(String givenName, boolean primary) {
        return new PersonNameRequest(PersonNameType.BIRTH, "Nguyễn", "Văn", givenName, primary);
    }

    /**
     * Builds a proposed edit carrying one name.
     *
     * @param givenName the tên to propose
     * @return the proposal
     */
    private static SuggestedPerson proposed(String givenName) {
        return new SuggestedPerson(List.of(name(givenName, true)), Gender.MALE, null, null, null);
    }

    /**
     * Builds a year-only solar date.
     *
     * @param year the year
     * @return the date
     */
    private static GenealogyDateRequest year(int year) {
        return new GenealogyDateRequest(
                DateModifier.EXACT, CalendarType.SOLAR, year, null, null, null, null, null, null, null, null);
    }

    /**
     * Builds a pending suggestion already stored.
     *
     * @param kind what is being asked for
     * @param targetId the person it points at, or null
     * @param payload the proposal to store as JSON, or null
     * @return the suggestion
     */
    private static Suggestion stored(SuggestionKind kind, Long targetId, SuggestedPerson payload) {
        Suggestion suggestion = new Suggestion();
        suggestion.setId(5L);
        suggestion.setTargetType(SuggestionTargetType.PERSON);
        suggestion.setTargetId(targetId);
        suggestion.setKind(kind);
        suggestion.setPayload(payload == null ? null : JSON.writeValueAsString(payload));
        suggestion.setMessage("Bà nội kể cụ tên là Ánh");
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setCreatedBy(SUGGESTER);
        suggestion.setCreatedAt(Instant.now());
        return suggestion;
    }

    /**
     * Stubs the repository to hand back one stored suggestion.
     *
     * @param suggestion the suggestion
     */
    private void given(Suggestion suggestion) {
        when(suggestionRepository.findById(5L)).thenReturn(Optional.of(suggestion));
        // The claim is a conditional UPDATE; the reload after it sees the row it wrote, so the mock writes it too.
        lenient().when(suggestionRepository.claim(eq(5L), any(), any(), any(), any())).thenAnswer(call -> {
            suggestion.setStatus(call.getArgument(1));
            suggestion.setReviewedBy(call.getArgument(2));
            suggestion.setReviewedAt(call.getArgument(3));
            suggestion.setReviewNote(call.getArgument(4));
            return 1;
        });
    }

    /**
     * Returns the person as they are recorded now: a primary name, a tên húy, a chi and notes.
     *
     * @return the current state
     */
    private static PersonRequest currentPerson() {
        return new PersonRequest(
                Gender.MALE, 7L, "ĐT 090xxxx, con cụ Đảm",
                List.of(name("Anh", true), new PersonNameRequest(PersonNameType.HUY, null, null, "Đảm", false)),
                null, null);
    }

    @Test
    @DisplayName("a suggested new person is stored with the parent or spouse it hangs off")
    void storesANewPersonWithItsAnchor() {
        ArgumentCaptor<Suggestion> saved = ArgumentCaptor.forClass(Suggestion.class);

        SuggestionResponse response = suggestions.create(
                new SuggestionRequest(SuggestionTargetType.PERSON, null, SuggestionKind.CREATE,
                        new SuggestedPerson(List.of(name("Bình", false)), null, year(1950), null,
                                new SuggestedAnchor(PERSON, AddRelationRequest.Kind.CHILD, null)),
                        "bà nội kể còn một cụ nữa"),
                SUGGESTER);

        verify(suggestionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPayload()).contains("anchor").doesNotContain("death");
        assertThat(response.status()).isEqualTo(SuggestionStatus.PENDING);
        // A CREATE has no target until approval, and Map.of() throws on a null key (§9).
        assertThat(response.targetName()).isNull();
        assertThat(response.anchor().personId()).isEqualTo(PERSON);
        assertThat(response.proposal().birth()).isEqualTo("1950");
        verify(auditService).record(eq(AuditEntityType.SUGGESTION), eq(5L), eq(AuditAction.CREATE), isNull(),
                any(), eq(SUGGESTER), isNull());
    }

    @Test
    @DisplayName("a new person with no parent or spouse is refused, because approval would place them nowhere")
    void refusesANewPersonWithNoAnchor() {
        assertThatThrownBy(() -> suggestions.create(
                        new SuggestionRequest(SuggestionTargetType.PERSON, null, SuggestionKind.CREATE,
                                proposed("Bình"), "vì sao"),
                        SUGGESTER))
                .isInstanceOf(BadRequestException.class);
        verify(suggestionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the queue is read through one scoped query: a MEMBER's own, newest first, never an open sort")
    void memberSeesOnlyTheirOwnSuggestions() {
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        when(suggestionRepository.search(isNull(), eq(SUGGESTER), paging.capture()))
                .thenReturn(new PageImpl<>(List.of(stored(SuggestionKind.UPDATE, PERSON, proposed("Ánh")))));

        // A client sort reached a derived query and a size of 10,000 was honoured (§8.9 #11).
        assertThat(suggestions.search(null, Role.MEMBER, SUGGESTER,
                PageRequest.of(0, 10_000, Sort.by("payload")))).hasSize(1);
        assertThat(paging.getValue().getPageSize()).isLessThanOrEqualTo(100);
        assertThat(paging.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    @DisplayName("an EDITOR reading the queue sees everyone's, because reviewing is their job")
    void editorSeesTheWholeQueue() {
        when(suggestionRepository.search(isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(stored(SuggestionKind.UPDATE, PERSON, null))));

        assertThat(suggestions.search(null, Role.EDITOR, REVIEWER, Pageable.unpaged())).hasSize(1);
    }

    @Test
    @DisplayName("one unreadable payload does not break the page; it is flagged so it can still be rejected")
    void toleratesAnUnreadablePayload() {
        Suggestion broken = stored(SuggestionKind.UPDATE, PERSON, null);
        broken.setPayload("{not json");
        when(suggestionRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of(broken)));

        SuggestionResponse row = suggestions.search(null, Role.EDITOR, REVIEWER, Pageable.unpaged()).getContent()
                .getFirst();

        assertThat(row.payloadReadable()).isFalse();
        assertThat(row.proposal()).isNull();
    }

    @Test
    @DisplayName("a payload stored before proposals lost chi and notes still reads")
    void readsAnOldPayload() {
        Suggestion old = stored(SuggestionKind.UPDATE, PERSON, null);
        old.setPayload("""
                {"gender":"MALE","branchId":4,"notes":"x","names":[
                  {"type":"BIRTH","surname":"Nguyễn","middleName":"Văn","givenName":"Ánh","primary":true}]}""");
        when(suggestionRepository.search(any(), any(), any())).thenReturn(new PageImpl<>(List.of(old)));

        SuggestionResponse row = suggestions.search(null, Role.EDITOR, REVIEWER, Pageable.unpaged()).getContent()
                .getFirst();

        assertThat(row.payloadReadable()).isTrue();
        assertThat(row.proposal().names().getFirst().display()).isEqualTo("Nguyễn Văn Ánh");
    }

    @Test
    @DisplayName("a MEMBER asking for someone else's suggestion by id is told there is no such one")
    void memberCannotFetchAnotherMembersSuggestion() {
        given(stored(SuggestionKind.UPDATE, PERSON, null));

        // Not a refusal: a 403 would confirm that someone proposed something about that person.
        assertThatThrownBy(() -> suggestions.getById(5L, Role.MEMBER, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a MEMBER's pending count is their own, not the clan's backlog")
    void memberPendingCountIsTheirOwn() {
        when(suggestionRepository.countInState(SuggestionStatus.PENDING, SUGGESTER)).thenReturn(2L);

        assertThat(suggestions.countPending(Role.MEMBER, SUGGESTER)).isEqualTo(2L);
    }

    @Test
    @DisplayName("an EDITOR's pending count is the whole queue")
    void editorPendingCountIsTheQueue() {
        when(suggestionRepository.countInState(SuggestionStatus.PENDING, null)).thenReturn(9L);

        assertThat(suggestions.countPending(Role.EDITOR, REVIEWER)).isEqualTo(9L);
    }

    @Test
    @DisplayName("a suggested edit is stored as pending, trimmed, and changes nothing yet")
    void createsPendingWithoutTouchingThePerson() {
        SuggestionResponse response = suggestions.create(
                new SuggestionRequest(
                        SuggestionTargetType.PERSON, PERSON, SuggestionKind.UPDATE, proposed("Ánh"),
                        "  vì bà nội kể  "),
                SUGGESTER);

        assertThat(response.status()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(response.message()).isEqualTo("vì bà nội kể");
        // The whole point of F17: offering a suggestion must not write to the gia phả.
        verify(personService, never()).update(anyLong(), any(), anyLong());
        verify(familyService, never()).addRelation(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("an edit about someone who does not exist is refused")
    void refusesAnUnknownTarget() {
        doThrow(new BadRequestException("Không có người với id 3")).when(personService).requireExists(PERSON);

        assertThatThrownBy(() -> suggestions.create(
                        new SuggestionRequest(SuggestionTargetType.PERSON, PERSON, SuggestionKind.NOTE, null, "x"),
                        SUGGESTER))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("a MEMBER cannot review, even by calling the service directly")
    void refusesAReviewBelowEditor() {
        assertThatThrownBy(() -> suggestions.review(
                        5L, new SuggestionReviewRequest(true, null), Role.MEMBER, SUGGESTER))
                .isInstanceOf(ForbiddenException.class);
        verify(suggestionRepository, never()).claim(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a MEMBER cannot preview, because the current side is the living person's real record")
    void refusesAPreviewBelowEditor() {
        assertThatThrownBy(() -> suggestions.preview(5L, Role.MEMBER)).isInstanceOf(ForbiddenException.class);
        verify(personService, never()).currentState(anyLong());
    }

    @Test
    @DisplayName("approving an edit writes the names, keeps chi, notes and tên húy, and keeps the old primary")
    void approvingKeepsWhatTheSuggesterCouldNotSee() {
        given(stored(SuggestionKind.UPDATE, PERSON,
                new SuggestedPerson(List.of(name("Ánh", false)), null, null, null, null)));
        when(personService.currentState(PERSON)).thenReturn(currentPerson());

        suggestions.review(5L, new SuggestionReviewRequest(true, null), Role.EDITOR, REVIEWER);

        verify(personService).update(eq(PERSON), personCaptor.capture(), eq(REVIEWER));
        PersonRequest written = personCaptor.getValue();
        // A MEMBER cannot read a living person's notes, so a proposal must never be able to overwrite them (#1).
        assertThat(written.notes()).isEqualTo("ĐT 090xxxx, con cụ Đảm");
        assertThat(written.branchId()).isEqualTo(7L);
        assertThat(written.gender()).isEqualTo(Gender.MALE);
        assertThat(written.names()).extracting(PersonNameRequest::givenName).containsExactly("Ánh", "Đảm", "Anh");
        // The proposed name carried no flag; it is still the one made primary, not added as an alternate (#7).
        assertThat(written.names().getFirst().primary()).isTrue();
        assertThat(written.names().stream().filter(PersonNameRequest::primary)).hasSize(1);
        assertThat(written.changeNote()).contains("Duyệt đề xuất #5");
    }

    @Test
    @DisplayName("the preview shows exactly what approval would write")
    void previewMatchesWhatApprovalWrites() {
        given(stored(SuggestionKind.UPDATE, PERSON,
                new SuggestedPerson(List.of(name("Ánh", true)), null, year(1920), null, null)));
        when(personService.currentState(PERSON)).thenReturn(currentPerson());
        when(eventService.findBySubject(EventSubjectType.PERSON, PERSON, Role.EDITOR)).thenReturn(List.of());

        SuggestionPreview preview = suggestions.preview(5L, Role.EDITOR);

        assertThat(preview.current().names()).extracting(n -> n.display()).containsExactly("Nguyễn Văn Anh", "Đảm");
        assertThat(preview.proposed().names()).extracting(n -> n.display())
                .containsExactly("Nguyễn Văn Ánh", "Đảm", "Nguyễn Văn Anh");
        assertThat(preview.current().birth()).isNull();
        assertThat(preview.proposed().birth()).isEqualTo("1920");
        assertThat(preview.proposed().gender()).isEqualTo(Gender.MALE);
    }

    @Test
    @DisplayName("approving a proposed birth date records a birth when none exists")
    void approvingADateCreatesTheEvent() {
        given(stored(SuggestionKind.UPDATE, PERSON, new SuggestedPerson(null, null, year(1920), null, null)));
        when(eventService.findBySubject(EventSubjectType.PERSON, PERSON, Role.EDITOR)).thenReturn(List.of());
        ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);

        suggestions.review(5L, new SuggestionReviewRequest(true, null), Role.EDITOR, REVIEWER);

        verify(eventService).create(event.capture(), eq(REVIEWER));
        assertThat(event.getValue().type()).isEqualTo(EventType.BIRTH);
        assertThat(event.getValue().date().year()).isEqualTo(1920);
        // Nothing but the date was proposed, so the person record itself is left alone.
        verify(personService, never()).update(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("approving a proposed date changes the one recorded birth, keeping its place and note")
    void approvingADateUpdatesTheOneEvent() {
        given(stored(SuggestionKind.UPDATE, PERSON, new SuggestedPerson(null, null, year(1921), null, null)));
        when(eventService.findBySubject(EventSubjectType.PERSON, PERSON, Role.EDITOR)).thenReturn(List.of(
                new EventResponse(40L, EventSubjectType.PERSON, PERSON, EventType.BIRTH, null, 11L, "ở quê", 2)));
        ArgumentCaptor<EventRequest> event = ArgumentCaptor.forClass(EventRequest.class);

        suggestions.review(5L, new SuggestionReviewRequest(true, null), Role.EDITOR, REVIEWER);

        verify(eventService).update(eq(40L), event.capture(), eq(REVIEWER));
        assertThat(event.getValue().placeId()).isEqualTo(11L);
        assertThat(event.getValue().description()).isEqualTo("ở quê");
    }

    @Test
    @DisplayName("a proposed date is refused when two births are recorded, rather than picking one")
    void refusesADateOnAConflict() {
        given(stored(SuggestionKind.UPDATE, PERSON, new SuggestedPerson(null, null, year(1921), null, null)));
        when(eventService.findBySubject(EventSubjectType.PERSON, PERSON, Role.EDITOR)).thenReturn(List.of(
                new EventResponse(40L, EventSubjectType.PERSON, PERSON, EventType.BIRTH, null, null, null, 0),
                new EventResponse(41L, EventSubjectType.PERSON, PERSON, EventType.BIRTH, null, null, null, 0)));

        assertThatThrownBy(() -> suggestions.review(
                        5L, new SuggestionReviewRequest(true, null), Role.EDITOR, REVIEWER))
                .isInstanceOf(BadRequestException.class);
        verify(eventService, never()).update(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("approving a suggested new person adds them to the anchor's family and records which one")
    void approvingCreateGoesThroughAddRelation() {
        given(stored(SuggestionKind.CREATE, null, new SuggestedPerson(List.of(name("Bình", false)), Gender.MALE,
                null, null, new SuggestedAnchor(PERSON, AddRelationRequest.Kind.CHILD, 12L))));
        ArgumentCaptor<AddRelationRequest> relation = ArgumentCaptor.forClass(AddRelationRequest.class);
        when(familyService.addRelation(eq(PERSON), relation.capture(), eq(REVIEWER)))
                .thenReturn(new AddRelationResponse(42L, null));

        SuggestionResponse response =
                suggestions.review(5L, new SuggestionReviewRequest(true, null), Role.EDITOR, REVIEWER);

        // Through addRelation, so the new person gets a union and a đời, not a row in no family (#15).
        assertThat(relation.getValue().kind()).isEqualTo(AddRelationRequest.Kind.CHILD);
        assertThat(relation.getValue().familyId()).isEqualTo(12L);
        assertThat(relation.getValue().names().getFirst().primary()).isTrue();
        assertThat(response.targetId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo(SuggestionStatus.APPROVED);
    }

    @Test
    @DisplayName("a second review loses the claim and is refused, so a proposal is never applied twice")
    void refusesASecondReview() {
        given(stored(SuggestionKind.UPDATE, PERSON, proposed("Ánh")));
        when(suggestionRepository.claim(anyLong(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> suggestions.review(
                        5L, new SuggestionReviewRequest(true, null), Role.EDITOR, REVIEWER))
                .isInstanceOf(ConflictException.class);
        verify(personService, never()).update(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("rejecting changes nothing and must say why")
    void rejectingChangesNothing() {
        // A rejection with no reason leaves the suggester guessing, so it is refused outright.
        assertThatThrownBy(() -> suggestions.review(
                        5L, new SuggestionReviewRequest(false, "  "), Role.EDITOR, REVIEWER))
                .isInstanceOf(BadRequestException.class);
        verify(suggestionRepository, never()).claim(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a rejection is claimed with its trimmed reason and recorded in the trail")
    void rejectingRecordsTheReason() {
        given(stored(SuggestionKind.UPDATE, PERSON, proposed("Ánh")));

        suggestions.review(5L, new SuggestionReviewRequest(false, "  không có căn cứ "), Role.EDITOR, REVIEWER);

        verify(suggestionRepository).claim(eq(5L), eq(SuggestionStatus.REJECTED), eq(REVIEWER), any(),
                eq("không có căn cứ"));
        verify(personService, never()).update(anyLong(), any(), anyLong());
        verify(auditService).record(eq(AuditEntityType.SUGGESTION), eq(5L), eq(AuditAction.UPDATE), any(), any(),
                eq(REVIEWER), eq("không có căn cứ"));
    }

    @Test
    @DisplayName("approving a plain note records the decision without touching anyone")
    void approvingANoteWritesNothing() {
        given(stored(SuggestionKind.NOTE, PERSON, null));

        suggestions.review(5L, new SuggestionReviewRequest(true, "đã ghi nhận"), Role.EDITOR, REVIEWER);

        verify(personService, never()).update(anyLong(), any(), anyLong());
        verify(eventService, never()).create(any(), anyLong());
    }

    @Test
    @DisplayName("a suggestion whose parts contradict each other is refused before anything is stored")
    void refusesContradictoryShapes() {
        // An edit with nothing to apply would approve into silence.
        assertThatThrownBy(() -> suggestions.create(
                        new SuggestionRequest(SuggestionTargetType.PERSON, PERSON, SuggestionKind.UPDATE,
                                new SuggestedPerson(List.of(), null, null, null, null), "vì sao"),
                        SUGGESTER))
                .isInstanceOf(BadRequestException.class);

        // A new person must not claim to be an edit of an existing one.
        assertThatThrownBy(() -> suggestions.create(
                        new SuggestionRequest(SuggestionTargetType.PERSON, PERSON, SuggestionKind.CREATE,
                                proposed("Ánh"), "vì sao"),
                        SUGGESTER))
                .isInstanceOf(BadRequestException.class);

        // A note carrying a payload would quietly apply on approval.
        assertThatThrownBy(() -> suggestions.create(
                        new SuggestionRequest(SuggestionTargetType.PERSON, PERSON, SuggestionKind.NOTE,
                                proposed("Ánh"), "vì sao"),
                        SUGGESTER))
                .isInstanceOf(BadRequestException.class);

        verify(suggestionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a merge turns a pending edit of the duplicate into a note, and moves an approved CREATE's person")
    void reassignTurnsPendingEditsIntoNotes() {
        Suggestion pending = stored(SuggestionKind.UPDATE, 2L, proposed("Ánh"));
        Suggestion created = stored(SuggestionKind.CREATE, 2L, proposed("Ánh"));
        created.setId(6L);
        created.setStatus(SuggestionStatus.APPROVED);
        when(suggestionRepository.findByTargetTypeAndTargetId(SuggestionTargetType.PERSON, 2L))
                .thenReturn(List.of(pending, created));
        when(suggestionRepository.findByKindAndStatus(SuggestionKind.CREATE, SuggestionStatus.PENDING))
                .thenReturn(List.of());

        SuggestionsMoved moved = suggestions.reassignTarget(2L, PERSON, REVIEWER, "gộp");

        // Applied to the survivor, an edit written for the duplicate would replace their primary name and sex (#6).
        assertThat(pending.getKind()).isEqualTo(SuggestionKind.NOTE);
        assertThat(pending.getPayload()).isNull();
        assertThat(pending.getMessage()).contains("bản trùng #2").contains("Nguyễn Văn Ánh");
        assertThat(pending.getTargetId()).isEqualTo(PERSON);
        // The person it created is now the survivor; left on the duplicate, its link and its name point at a ghost.
        assertThat(created.getTargetId()).isEqualTo(PERSON);
        assertThat(moved).isEqualTo(new SuggestionsMoved(2, 1));
    }

    @Test
    @DisplayName("a folded union's heir replaces it in a pending new person, and a deleted union leaves none")
    void reanchorUnionsFollowsFoldAndDelete() {
        Suggestion folded = stored(SuggestionKind.CREATE, null, new SuggestedPerson(List.of(name("Bình", true)),
                null, null, null, new SuggestedAnchor(PERSON, AddRelationRequest.Kind.CHILD, 11L)));
        Suggestion gone = stored(SuggestionKind.CREATE, null, new SuggestedPerson(List.of(name("Cúc", true)),
                null, null, null, new SuggestedAnchor(PERSON, AddRelationRequest.Kind.CHILD, 12L)));
        Suggestion untouched = stored(SuggestionKind.CREATE, null, new SuggestedPerson(List.of(name("Dần", true)),
                null, null, null, new SuggestedAnchor(PERSON, AddRelationRequest.Kind.CHILD, 13L)));
        when(suggestionRepository.findByKindAndStatus(SuggestionKind.CREATE, SuggestionStatus.PENDING))
                .thenReturn(List.of(folded, gone, untouched));

        int moved = suggestions.reanchorUnions(Map.of(11L, 10L), List.of(12L), REVIEWER, "gộp");

        assertThat(moved).isEqualTo(2);
        assertThat(folded.getPayload()).contains("\"familyId\":10");
        assertThat(gone.getPayload()).doesNotContain("\"familyId\":12");
        assertThat(untouched.getPayload()).contains("\"familyId\":13");
    }

    @Test
    @DisplayName("a merge re-anchors a pending new person onto the survivor")
    void reassignReanchorsPendingCreates() {
        Suggestion create = stored(SuggestionKind.CREATE, null, new SuggestedPerson(List.of(name("Bình", true)),
                null, null, null, new SuggestedAnchor(2L, AddRelationRequest.Kind.CHILD, null)));
        when(suggestionRepository.findByTargetTypeAndTargetId(SuggestionTargetType.PERSON, 2L)).thenReturn(List.of());
        when(suggestionRepository.findByKindAndStatus(SuggestionKind.CREATE, SuggestionStatus.PENDING))
                .thenReturn(List.of(create));

        suggestions.reassignTarget(2L, PERSON, REVIEWER, "gộp");

        assertThat(create.getPayload()).contains("\"personId\":" + PERSON);
    }

    @Test
    @DisplayName("deleting a person deletes the suggestions about them and those anchored to them, recording each")
    void forgetTargetTakesAnchoredCreatesToo() {
        Suggestion about = stored(SuggestionKind.NOTE, PERSON, null);
        Suggestion anchored = stored(SuggestionKind.CREATE, null, new SuggestedPerson(List.of(name("Bình", true)),
                null, null, null, new SuggestedAnchor(PERSON, AddRelationRequest.Kind.SPOUSE, null)));
        anchored.setId(6L);
        when(suggestionRepository.findByTargetTypeAndTargetId(SuggestionTargetType.PERSON, PERSON))
                .thenReturn(List.of(about));
        when(suggestionRepository.findByKindAndStatus(SuggestionKind.CREATE, SuggestionStatus.PENDING))
                .thenReturn(List.of(anchored));

        assertThat(suggestions.forgetTarget(PERSON, REVIEWER, "xoá")).isEqualTo(2);
        verify(auditService).record(eq(AuditEntityType.SUGGESTION), eq(6L), eq(AuditAction.DELETE), any(),
                isNull(), eq(REVIEWER), eq("xoá"));
    }
}
