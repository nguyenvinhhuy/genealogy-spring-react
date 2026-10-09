package com.genealogy.suggestion.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Role;
import com.genealogy.common.util.Blank;
import com.genealogy.common.util.NameDisplay;
import com.genealogy.common.util.NameKey;
import com.genealogy.common.web.PageRequests;
import com.genealogy.event.dto.request.EventRequest;
import com.genealogy.event.dto.request.GenealogyDateRequest;
import com.genealogy.event.dto.response.EventResponse;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.request.AddRelationRequest;
import com.genealogy.family.service.FamilyService;
import com.genealogy.member.service.MemberNameService;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.suggestion.domain.Suggestion;
import com.genealogy.suggestion.domain.SuggestionKind;
import com.genealogy.suggestion.domain.SuggestionStatus;
import com.genealogy.suggestion.domain.SuggestionTargetType;
import com.genealogy.suggestion.dto.request.SuggestedAnchor;
import com.genealogy.suggestion.dto.request.SuggestedPerson;
import com.genealogy.suggestion.dto.request.SuggestionRequest;
import com.genealogy.suggestion.dto.request.SuggestionReviewRequest;
import com.genealogy.suggestion.dto.response.AnchorView;
import com.genealogy.suggestion.dto.response.PersonSide;
import com.genealogy.suggestion.dto.response.ProposedName;
import com.genealogy.suggestion.dto.response.SuggestionPreview;
import com.genealogy.suggestion.dto.response.SuggestionResponse;
import com.genealogy.suggestion.dto.response.SuggestionsMoved;
import com.genealogy.suggestion.mapper.SuggestionMapper;
import com.genealogy.suggestion.repository.SuggestionRepository;
import com.genealogy.suggestion.service.SuggestionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Default {@link SuggestionService}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SuggestionServiceImpl implements SuggestionService {

    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.SUGGESTION;
    private static final String UNREADABLE = "Không đọc được nội dung đề xuất";

    private final SuggestionRepository suggestionRepository;
    private final SuggestionMapper suggestionMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    // Cross-feature by interface only (CLAUDE.md §4): no person, member, family or event entity is imported here.
    private final PersonService personService;
    private final MemberNameService memberNameService;
    private final FamilyService familyService;
    private final EventService eventService;

    /**
     * Lists suggestions newest first, optionally by state, only the caller's own below EDITOR.
     *
     * @param status the state to filter by, or null for all of them
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @param pageable which page; its size is capped and its sort ignored
     * @return the matching page
     */
    @Override
    public Page<SuggestionResponse> search(
            SuggestionStatus status, Role role, Long memberId, Pageable pageable) {
        Page<Suggestion> page =
                suggestionRepository.search(status, ownerScope(role, memberId), PageRequests.capped(pageable));
        return toResponses(page.getContent(), page);
    }

    /**
     * Returns one suggestion, answering "no such suggestion" below EDITOR when it is someone else's.
     *
     * @param id suggestion id
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @return the suggestion
     */
    @Override
    public SuggestionResponse getById(Long id, Role role, Long memberId) {
        return toResponse(requireVisible(id, role, memberId));
    }

    /**
     * Shows what approving a suggestion would change, field by field (EDITOR+).
     *
     * @param id suggestion id
     * @param role the calling member's access level
     * @return the person as they are and as approval would leave them
     */
    @Override
    public SuggestionPreview preview(Long id, Role role) {
        // The current side is the real record, living details included, so only a reviewer may see it (§3.6).
        requireReviewer(role);
        Suggestion suggestion = require(id);
        if (suggestion.getKind() == SuggestionKind.NOTE) {
            return new SuggestionPreview(null, null, null);
        }
        SuggestedPerson proposed = readStrictly(suggestion);
        if (suggestion.getKind() == SuggestionKind.CREATE) {
            Map<Long, Optional<SuggestedPerson>> read = Map.of(suggestion.getId(), Optional.of(proposed));
            AnchorView anchor = anchorOf(proposed.anchor(), anchorNames(List.of(suggestion), read));
            return new SuggestionPreview(null, sideOf(proposed), anchor);
        }
        Long personId = suggestion.getTargetId();
        PersonRequest current = personService.currentState(personId);
        List<EventResponse> events = lifeEvents(personId, role);
        PersonSide before = new PersonSide(
                namesOf(current.names()), current.gender(),
                recorded(events, EventType.BIRTH), recorded(events, EventType.DEATH));
        PersonSide after = new PersonSide(
                proposed.names() == null ? before.names() : namesOf(merged(proposed.names(), current.names())),
                proposed.gender() == null ? before.gender() : proposed.gender(),
                proposed.birth() == null ? before.birth() : dateText(proposed.birth()),
                proposed.death() == null ? before.death() : dateText(proposed.death()));
        return new SuggestionPreview(before, after, null);
    }

    /**
     * Records a suggestion offered by a member.
     *
     * @param request what is being suggested
     * @param createdBy id of the member offering it
     * @return the recorded suggestion
     */
    @Override
    @Transactional
    public SuggestionResponse create(SuggestionRequest request, Long createdBy) {
        validateShape(request);

        Suggestion suggestion = new Suggestion();
        suggestion.setTargetType(request.targetType());
        suggestion.setTargetId(request.targetId());
        suggestion.setKind(request.kind());
        suggestion.setPayload(request.kind() == SuggestionKind.NOTE ? null : serialise(request.person()));
        suggestion.setMessage(request.message().strip());
        suggestion.setCreatedBy(createdBy);

        SuggestionResponse created = toResponse(suggestionRepository.saveAndFlush(suggestion));
        auditService.record(ENTITY_TYPE, created.id(), AuditAction.CREATE, null, created, createdBy, null);
        return created;
    }

    /**
     * Approves or rejects a suggestion, applying its proposal when approved (EDITOR+).
     *
     * @param id suggestion id
     * @param request the decision and its reason
     * @param role the calling member's access level
     * @param reviewedBy id of the member deciding
     * @return the reviewed suggestion
     */
    @Override
    @Transactional
    public SuggestionResponse review(Long id, SuggestionReviewRequest request, Role role, Long reviewedBy) {
        // The service is the guard, not the matcher: approving writes to the gia phả as the reviewer (§8.9 #40).
        requireReviewer(role);
        boolean approve = Boolean.TRUE.equals(request.approve());
        String note = Blank.toNull(request.reviewNote());
        if (!approve && note == null) {
            throw new BadRequestException("Từ chối đề xuất thì phải ghi lý do");
        }

        SuggestionResponse before = toResponse(require(id));
        SuggestionStatus decision = approve ? SuggestionStatus.APPROVED : SuggestionStatus.REJECTED;
        // Claimed first, in one conditional UPDATE: two reviewers can no longer both apply one proposal (#2).
        if (suggestionRepository.claim(id, decision, reviewedBy, Instant.now(), note) == 0) {
            throw new ConflictException("Đề xuất này đã được xử lý");
        }
        Suggestion suggestion = require(id);
        if (approve) {
            apply(suggestion, role, reviewedBy);
        }
        SuggestionResponse after = toResponse(suggestionRepository.saveAndFlush(suggestion));
        auditService.record(ENTITY_TYPE, id, AuditAction.UPDATE, before, after, reviewedBy, note);
        return after;
    }

    /**
     * Counts the suggestions still waiting for a reviewer, only the caller's own below EDITOR.
     *
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @return how many are pending
     */
    @Override
    public long countPending(Role role, Long memberId) {
        return suggestionRepository.countInState(SuggestionStatus.PENDING, ownerScope(role, memberId));
    }

    /**
     * Moves every suggestion off one person onto another, for a merge, recording each change.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason
     * @return how many moved, and how many pending edits became notes
     */
    @Override
    @Transactional
    public SuggestionsMoved reassignTarget(Long fromId, Long toId, Long actorId, String changeNote) {
        int moved = 0;
        int turned = 0;
        for (Suggestion suggestion : suggestionRepository.findByTargetTypeAndTargetId(
                SuggestionTargetType.PERSON, fromId)) {
            // An approved CREATE follows its person to the survivor, or its link and its name would point at a ghost.
            SuggestionResponse before = toResponse(suggestion);
            // An edit proposed against the duplicate would replace the survivor's primary name and sex (#6).
            if (suggestion.getKind() == SuggestionKind.UPDATE && suggestion.getStatus() == SuggestionStatus.PENDING) {
                suggestion.setMessage("[Đề xuất về bản trùng #" + fromId + " đã gộp — xem lại trước khi áp dụng: "
                        + proposalText(read(suggestion)) + "] " + suggestion.getMessage());
                suggestion.setKind(SuggestionKind.NOTE);
                suggestion.setPayload(null);
                turned++;
            }
            suggestion.setTargetId(toId);
            record(before, suggestion, actorId, changeNote);
            moved++;
        }
        // A pending new person anchored to the duplicate joins the survivor's family instead.
        for (Suggestion suggestion : pendingCreatesAnchoredTo(fromId)) {
            SuggestionResponse before = toResponse(suggestion);
            SuggestedPerson proposed = read(suggestion).orElseThrow();
            SuggestedAnchor anchor = proposed.anchor();
            suggestion.setPayload(serialise(new SuggestedPerson(proposed.names(), proposed.gender(),
                    proposed.birth(), proposed.death(),
                    new SuggestedAnchor(toId, anchor.kind(), anchor.familyId()))));
            record(before, suggestion, actorId, changeNote);
            moved++;
        }
        suggestionRepository.flush();
        return new SuggestionsMoved(moved, turned);
    }

    /**
     * Points each pending new person's union at the one that replaced it, or at a new union when it is gone.
     *
     * @param foldedInto each folded union's id mapped to the union that survived it
     * @param goneUnions the ids of unions deleted outright
     * @param actorId the member whose merge or delete did it
     * @param changeNote why
     * @return how many suggestions were re-anchored
     */
    @Override
    @Transactional
    public int reanchorUnions(Map<Long, Long> foldedInto, Collection<Long> goneUnions, Long actorId,
            String changeNote) {
        int moved = 0;
        for (Suggestion suggestion : suggestionRepository.findByKindAndStatus(
                SuggestionKind.CREATE, SuggestionStatus.PENDING)) {
            SuggestedPerson proposed = read(suggestion).orElse(null);
            SuggestedAnchor anchor = proposed == null ? null : proposed.anchor();
            if (anchor == null || anchor.familyId() == null) {
                continue;
            }
            boolean folded = foldedInto.containsKey(anchor.familyId());
            if (!folded && !goneUnions.contains(anchor.familyId())) {
                continue;
            }
            SuggestionResponse before = toResponse(suggestion);
            // A union that is gone has no heir: the child is proposed into a new union of the anchor, for the reviewer.
            Long familyId = folded ? foldedInto.get(anchor.familyId()) : null;
            suggestion.setPayload(serialise(new SuggestedPerson(proposed.names(), proposed.gender(),
                    proposed.birth(), proposed.death(),
                    new SuggestedAnchor(anchor.personId(), anchor.kind(), familyId))));
            record(before, suggestion, actorId, changeNote);
            moved++;
        }
        suggestionRepository.flush();
        return moved;
    }

    /**
     * Deletes the suggestions about a person who is themselves being deleted, recording each one.
     *
     * @param personId the person being deleted
     * @param actorId the member deleting them
     * @param changeNote why they are being deleted
     * @return how many suggestions were deleted
     */
    @Override
    @Transactional
    public int forgetTarget(Long personId, Long actorId, String changeNote) {
        // The audit trail outlives the person (§3.8); a proposal about them, or anchored to them, does not.
        List<Suggestion> doomed = new ArrayList<>(
                suggestionRepository.findByTargetTypeAndTargetId(SuggestionTargetType.PERSON, personId));
        doomed.addAll(pendingCreatesAnchoredTo(personId));
        List<SuggestionResponse> before = doomed.stream().map(this::toResponse).toList();
        suggestionRepository.deleteAll(doomed);
        suggestionRepository.flush();
        before.forEach(suggestion -> auditService.record(
                ENTITY_TYPE, suggestion.id(), AuditAction.DELETE, suggestion, null, actorId, changeNote));
        return doomed.size();
    }

    /**
     * Writes an approved suggestion into the gia phả as the reviewer's own edit.
     *
     * @param suggestion the suggestion being approved
     * @param role the reviewer's access level
     * @param reviewedBy id of the member approving it
     */
    private void apply(Suggestion suggestion, Role role, Long reviewedBy) {
        if (suggestion.getKind() == SuggestionKind.NOTE) {
            return;
        }
        SuggestedPerson proposed = readStrictly(suggestion);
        // The reviewer authors the resulting edit: they vouched for it, and the note points back (§3.8).
        String changeNote = "Duyệt đề xuất #" + suggestion.getId() + ": " + suggestion.getMessage();

        if (suggestion.getKind() == SuggestionKind.CREATE) {
            SuggestedAnchor anchor = proposed.anchor();
            // A proposal from before anchors existed would create a person in no union, with no đời (§8.9 #15).
            if (anchor == null) {
                throw new BadRequestException("Đề xuất này không nói người mới là con hay vợ/chồng của ai. "
                        + "Hãy từ chối rồi thêm người trực tiếp từ trang của cha/mẹ hoặc vợ/chồng.");
            }
            Long created = familyService.addRelation(anchor.personId(), new AddRelationRequest(
                    anchor.kind(), proposed.gender(), withPrimary(proposed.names()), anchor.familyId(), changeNote),
                    reviewedBy).personId();
            suggestion.setTargetId(created);
            applyDate(created, EventType.BIRTH, proposed.birth(), role, reviewedBy, changeNote);
            applyDate(created, EventType.DEATH, proposed.death(), role, reviewedBy, changeNote);
            return;
        }
        Long personId = suggestion.getTargetId();
        if (proposed.names() != null || proposed.gender() != null) {
            PersonRequest current = personService.currentState(personId);
            personService.update(personId, new PersonRequest(
                    proposed.gender() == null ? current.gender() : proposed.gender(),
                    // Chi and notes are not a MEMBER's to propose, so approval always keeps them (§8.9 D1).
                    current.branchId(),
                    current.notes(),
                    proposed.names() == null ? current.names() : merged(proposed.names(), current.names()),
                    changeNote,
                    // No version: the proposal is applied onto the person as they are now, not as they were then.
                    null), reviewedBy);
        }
        applyDate(personId, EventType.BIRTH, proposed.birth(), role, reviewedBy, changeNote);
        applyDate(personId, EventType.DEATH, proposed.death(), role, reviewedBy, changeNote);
    }

    /**
     * Records a proposed birth or death date: a new event when none is recorded, the one event's date otherwise.
     *
     * @param personId whose date it is
     * @param type BIRTH or DEATH
     * @param date the proposed date, or null to leave it alone
     * @param role the reviewer's access level
     * @param actorId the reviewer
     * @param changeNote the audit note the edit carries
     */
    private void applyDate(
            Long personId, EventType type, GenealogyDateRequest date, Role role, Long actorId, String changeNote) {
        if (date == null) {
            return;
        }
        List<EventResponse> recorded = lifeEvents(personId, role).stream()
                .filter(event -> event.type() == type)
                .toList();
        // Two recorded births is a contradiction for the reviewer to settle, not for approval to pick one (§5.1).
        if (recorded.size() > 1) {
            throw new BadRequestException("Người này đang có nhiều ngày " + (type == EventType.BIRTH ? "sinh" : "mất")
                    + " được ghi. Hãy sửa trực tiếp trên trang người rồi mới duyệt đề xuất.");
        }
        if (recorded.isEmpty()) {
            eventService.create(new EventRequest(personId, type, date, null, null, changeNote, null), actorId);
            return;
        }
        EventResponse existing = recorded.getFirst();
        // The place and the note stay: a proposal about the date says nothing about where it happened.
        eventService.update(existing.id(), new EventRequest(
                personId, type, date, existing.placeId(), existing.description(), changeNote, null), actorId);
    }

    /**
     * Lists a person's birth and death events.
     *
     * @param personId the person
     * @param role the reviewer's access level, always EDITOR+ here
     * @return their life events
     */
    private List<EventResponse> lifeEvents(Long personId, Role role) {
        return eventService.findBySubject(EventSubjectType.PERSON, personId, role).stream()
                .filter(event -> event.type() == EventType.BIRTH || event.type() == EventType.DEATH)
                .toList();
    }

    /**
     * Renders the recorded dates of one type, joined when there is more than one.
     *
     * @param events the person's life events
     * @param type BIRTH or DEATH
     * @return the rendered dates, or null when none is recorded
     */
    private static String recorded(List<EventResponse> events, EventType type) {
        String text = events.stream()
                .filter(event -> event.type() == type && event.date() != null && event.date().display() != null)
                .map(event -> event.date().display())
                .collect(Collectors.joining(" / "));
        return text.isEmpty() ? null : text;
    }

    /**
     * Builds the names an approved edit writes, keeping the alternates the proposal does not mention.
     *
     * @param proposed the names the suggestion carries
     * @param current the names the person has now
     * @return the proposed names, primary first, followed by the current ones they neither name nor replace
     */
    private static List<PersonNameRequest> merged(List<PersonNameRequest> proposed, List<PersonNameRequest> current) {
        // Removing a name stays an EDITOR's own edit: a suggestion has no way to mean "delete this one".
        List<PersonNameRequest> names = new ArrayList<>(withPrimary(proposed));
        current.stream()
                .filter(name -> !name.primary())
                .filter(name -> names.stream().noneMatch(other -> sameName(other, name)))
                .forEach(names::add);
        // The old primary, when replaced, is kept as an alternate rather than lost with the proposal.
        current.stream()
                .filter(PersonNameRequest::primary)
                .filter(name -> names.stream().noneMatch(other -> sameName(other, name)))
                .map(name -> new PersonNameRequest(
                        name.type(), name.surname(), name.middleName(), name.givenName(), false))
                .forEach(names::add);
        return names;
    }

    /**
     * Makes exactly one name primary: the one flagged, or the first when none is.
     *
     * @param names the proposed names
     * @return the same names, with one primary
     */
    private static List<PersonNameRequest> withPrimary(List<PersonNameRequest> names) {
        // PersonRequest takes the first name as primary when none is flagged; a suggestion now does the same (#7).
        int primary = 0;
        for (int at = 0; at < names.size(); at++) {
            if (names.get(at).primary()) {
                primary = at;
                break;
            }
        }
        List<PersonNameRequest> result = new ArrayList<>();
        for (int at = 0; at < names.size(); at++) {
            PersonNameRequest name = names.get(at);
            result.add(new PersonNameRequest(
                    name.type(), name.surname(), name.middleName(), name.givenName(), at == primary));
        }
        result.sort((one, other) -> Boolean.compare(other.primary(), one.primary()));
        return result;
    }

    /**
     * Reports whether two names record the same thing.
     *
     * @param one one name
     * @param other the other name
     * @return true when the type and all three name parts match, by the rule a merge uses too
     */
    private static boolean sameName(PersonNameRequest one, PersonNameRequest other) {
        return NameKey.of(one.type(), one.surname(), one.middleName(), one.givenName())
                .equals(NameKey.of(other.type(), other.surname(), other.middleName(), other.givenName()));
    }

    /**
     * Rejects a suggestion whose parts contradict each other before anything is stored.
     *
     * @param request the inbound payload
     */
    private void validateShape(SuggestionRequest request) {
        SuggestedPerson person = request.person();
        boolean needsPerson = request.kind() != SuggestionKind.NOTE;
        if (needsPerson && person == null) {
            throw new BadRequestException("Đề xuất sửa hoặc thêm người thì phải kèm thông tin người");
        }
        if (!needsPerson && person != null) {
            throw new BadRequestException("Góp ý thì không kèm thông tin người");
        }
        if (request.kind() == SuggestionKind.CREATE) {
            if (request.targetId() != null) {
                throw new BadRequestException("Đề xuất thêm người mới thì không chỉ vào ai cả");
            }
            if (person.anchor() == null) {
                throw new BadRequestException("Đề xuất thêm người mới phải nói đó là con hay vợ/chồng của ai");
            }
            if (person.names() == null || person.names().isEmpty()) {
                throw new BadRequestException("Đề xuất thêm người mới phải có ít nhất một tên");
            }
            personService.requireExists(person.anchor().personId());
            return;
        }
        if (request.targetId() == null) {
            throw new BadRequestException("Đề xuất này phải chỉ rõ người nào");
        }
        personService.requireExists(request.targetId());
        if (request.kind() == SuggestionKind.UPDATE) {
            if (person.anchor() != null) {
                throw new BadRequestException("Đề xuất sửa một người thì không kèm cha/mẹ hay vợ/chồng");
            }
            boolean empty = (person.names() == null || person.names().isEmpty()) && person.gender() == null
                    && person.birth() == null && person.death() == null;
            if (empty) {
                throw new BadRequestException(
                        "Đề xuất sửa phải đổi ít nhất một điều: tên, giới tính, ngày sinh hoặc mất");
            }
        }
    }

    /**
     * Returns the member a list is narrowed to: nobody for a reviewer, the caller below EDITOR.
     *
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @return the owner to filter by, or null for everyone's
     */
    private static Long ownerScope(Role role, Long memberId) {
        // The one place the §8 P5 rule lives: below EDITOR a caller sees only what they offered (§8.9 #37).
        return Role.isEditorOrAbove(role) ? null : memberId;
    }

    /**
     * Refuses a caller below EDITOR.
     *
     * @param role the calling member's access level
     */
    private static void requireReviewer(Role role) {
        if (!Role.isEditorOrAbove(role)) {
            throw new ForbiddenException("Chỉ biên tập viên và trưởng tộc được duyệt đề xuất");
        }
    }

    /**
     * Loads a suggestion the caller may see, answering "no such suggestion" when it is someone else's.
     *
     * @param id suggestion id
     * @param role the calling member's access level
     * @param memberId the calling member's id
     * @return the suggestion
     */
    private Suggestion requireVisible(Long id, Role role, Long memberId) {
        Suggestion suggestion = require(id);
        Long owner = ownerScope(role, memberId);
        // "No such suggestion" rather than a refusal, which would confirm what was proposed about whom.
        if (owner != null && !owner.equals(suggestion.getCreatedBy())) {
            throw new NotFoundException("Không có đề xuất với id " + id);
        }
        return suggestion;
    }

    /**
     * Loads a suggestion or fails.
     *
     * @param id suggestion id
     * @return the suggestion
     */
    private Suggestion require(Long id) {
        return suggestionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có đề xuất với id " + id));
    }

    /**
     * Lists the pending new people anchored to one person.
     *
     * @param personId the anchor
     * @return the matching suggestions
     */
    private List<Suggestion> pendingCreatesAnchoredTo(Long personId) {
        // The anchor lives inside the payload, and a clan's pending new people are few enough to read in full.
        return suggestionRepository.findByKindAndStatus(SuggestionKind.CREATE, SuggestionStatus.PENDING).stream()
                .filter(suggestion -> read(suggestion)
                        .map(SuggestedPerson::anchor)
                        .map(anchor -> personId.equals(anchor.personId()))
                        .orElse(false))
                .toList();
    }

    /**
     * Records one change made to a suggestion.
     *
     * @param before the suggestion as it was
     * @param suggestion the entity as it is now
     * @param actorId the member making the change
     * @param changeNote why
     */
    private void record(SuggestionResponse before, Suggestion suggestion, Long actorId, String changeNote) {
        suggestionRepository.flush();
        auditService.record(
                ENTITY_TYPE, before.id(), AuditAction.UPDATE, before, toResponse(suggestion), actorId, changeNote);
    }

    /**
     * Describes a proposal in one line, for a note that has to carry it after its payload is dropped.
     *
     * @param proposed the proposal, or empty when it could not be read
     * @return the description
     */
    private String proposalText(Optional<SuggestedPerson> proposed) {
        if (proposed.isEmpty()) {
            return "(không đọc được nội dung)";
        }
        SuggestedPerson person = proposed.get();
        List<String> parts = new ArrayList<>();
        if (person.names() != null && !person.names().isEmpty()) {
            parts.add("tên " + person.names().stream()
                    .map(name -> NameDisplay.of(name.surname(), name.middleName(), name.givenName()))
                    .collect(Collectors.joining(", ")));
        }
        if (person.gender() != null) {
            parts.add("giới tính " + person.gender());
        }
        if (person.birth() != null) {
            parts.add("sinh " + dateText(person.birth()));
        }
        if (person.death() != null) {
            parts.add("mất " + dateText(person.death()));
        }
        return String.join("; ", parts);
    }

    /**
     * Renders what a proposal carries, with no current state, so its author may see it.
     *
     * @param proposed the proposal
     * @return the rendered proposal
     */
    private PersonSide sideOf(SuggestedPerson proposed) {
        return new PersonSide(
                proposed.names() == null ? List.of() : namesOf(withPrimary(proposed.names())),
                proposed.gender(),
                proposed.birth() == null ? null : dateText(proposed.birth()),
                proposed.death() == null ? null : dateText(proposed.death()));
    }

    /**
     * Renders names for the screen, surname first.
     *
     * @param names the names
     * @return the rendered names, in the order given
     */
    private static List<ProposedName> namesOf(List<PersonNameRequest> names) {
        return names.stream()
                .map(name -> new ProposedName(
                        name.type(),
                        NameDisplay.of(name.surname(), name.middleName(), name.givenName()),
                        name.primary()))
                .toList();
    }

    /**
     * Renders a proposed date the way every stored date is rendered.
     *
     * @param date the proposed date
     * @return the Vietnamese text
     */
    private String dateText(GenealogyDateRequest date) {
        // Asked of `event`, which owns the defaults an omitted modifier or calendar takes when the date is stored.
        return eventService.renderDate(date);
    }

    /**
     * Names an anchor for the screen.
     *
     * @param anchor the anchor, or null
     * @param names the display name of every person pointed at
     * @return the anchor view, or null
     */
    private static AnchorView anchorOf(SuggestedAnchor anchor, Map<Long, String> names) {
        return anchor == null
                ? null
                : new AnchorView(anchor.personId(), names.get(anchor.personId()), anchor.kind(), anchor.familyId());
    }

    /**
     * Converts one suggestion to its response representation.
     *
     * @param suggestion the entity
     * @return the response DTO
     */
    private SuggestionResponse toResponse(Suggestion suggestion) {
        return toResponses(List.of(suggestion), null).getContent().getFirst();
    }

    /**
     * Converts a batch of suggestions, loading every member and person name they need in one query each.
     *
     * @param suggestions the entities
     * @param page the page they came from, or null for a single suggestion
     * @return the response DTOs, as a page
     */
    private Page<SuggestionResponse> toResponses(List<Suggestion> suggestions, Page<Suggestion> page) {
        Map<Long, Optional<SuggestedPerson>> proposals = suggestions.stream()
                .collect(Collectors.toMap(Suggestion::getId, this::read, (one, other) -> one));
        Map<Long, String> memberNames = memberNames(suggestions);
        Map<Long, String> personNames = anchorNames(suggestions, proposals);
        List<SuggestionResponse> rows = suggestions.stream()
                .map(suggestion -> {
                    Optional<SuggestedPerson> proposed = proposals.get(suggestion.getId());
                    boolean readable = suggestion.getPayload() == null || proposed.isPresent();
                    return suggestionMapper.toResponse(
                            suggestion,
                            nameOf(personNames, suggestion.getTargetId()),
                            nameOf(memberNames, suggestion.getCreatedBy()),
                            nameOf(memberNames, suggestion.getReviewedBy()),
                            proposed.map(this::sideOf).orElse(null),
                            proposed.map(person -> anchorOf(person.anchor(), personNames)).orElse(null),
                            readable);
                })
                .toList();
        return page == null ? new PageImpl<>(rows) : new PageImpl<>(rows, page.getPageable(), page.getTotalElements());
    }

    /**
     * Loads the display name of every member a batch of suggestions refers to, in one query.
     *
     * @param suggestions the suggestions
     * @return the names by member id
     */
    private Map<Long, String> memberNames(List<Suggestion> suggestions) {
        Set<Long> ids = suggestions.stream()
                .flatMap(suggestion -> Stream.of(suggestion.getCreatedBy(), suggestion.getReviewedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : memberNameService.findNames(ids);
    }

    /**
     * Loads the display name of every person a batch of suggestions points at or anchors to, in one query.
     *
     * @param suggestions the suggestions
     * @param proposals each suggestion's payload, already read, so it is not parsed a second time
     * @return the names by person id
     */
    private Map<Long, String> anchorNames(
            List<Suggestion> suggestions, Map<Long, Optional<SuggestedPerson>> proposals) {
        Set<Long> ids = suggestions.stream()
                .flatMap(suggestion -> Stream.of(
                        suggestion.getTargetId(),
                        proposals.getOrDefault(suggestion.getId(), Optional.empty())
                                .map(SuggestedPerson::anchor).map(SuggestedAnchor::personId).orElse(null)))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty()
                ? Map.of()
                : personService.findNodes(ids).stream()
                        .collect(Collectors.toMap(PersonNodeResponse::id, PersonNodeResponse::displayName));
    }

    /**
     * Looks up a name, tolerating an id that is absent.
     *
     * @param names the names already loaded
     * @param id the person or member to name, or null
     * @return the name, or null when there is none
     */
    private static String nameOf(Map<Long, String> names, Long id) {
        // Map.of() throws on a null key, and a CREATE suggestion has no target until it is approved.
        return id == null ? null : names.get(id);
    }

    /**
     * Serialises a proposal to the JSON stored with the suggestion, dropping the fields left empty.
     *
     * @param person the proposal
     * @return the JSON
     */
    private String serialise(SuggestedPerson person) {
        try {
            ObjectNode node = objectMapper.valueToTree(person);
            // A null field is one the suggester never filled in, so it is dropped rather than stored as "clear".
            Stream.of("names", "gender", "birth", "death", "anchor")
                    .filter(field -> node.path(field).isNull())
                    .forEach(node::remove);
            return objectMapper.writeValueAsString(node);
        } catch (RuntimeException failure) {
            // An unreadable payload could never be approved, so it is refused now rather than stored.
            log.warn("Could not serialise a suggested person", failure);
            throw new BadRequestException("Không lưu được nội dung đề xuất");
        }
    }

    /**
     * Reads a stored proposal back, tolerating one that no longer reads.
     *
     * @param suggestion the suggestion
     * @return the proposal, or empty for a note or a payload that cannot be read
     */
    private Optional<SuggestedPerson> read(Suggestion suggestion) {
        if (suggestion.getPayload() == null) {
            return Optional.empty();
        }
        try {
            // Payloads stored before §8.9 D1 still carry branchId and notes, which a proposal no longer has.
            return Optional.of(objectMapper.readerFor(SuggestedPerson.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(suggestion.getPayload()));
        } catch (RuntimeException failure) {
            // One bad row must not make the whole queue a 400, or it could never even be rejected (§8.9 #12).
            log.warn("Could not read the payload of suggestion {}", suggestion.getId(), failure);
            return Optional.empty();
        }
    }

    /**
     * Reads a stored proposal back for a preview or an approval, which cannot proceed without it.
     *
     * @param suggestion the suggestion
     * @return the proposal
     */
    private SuggestedPerson readStrictly(Suggestion suggestion) {
        return read(suggestion).orElseThrow(() -> new BadRequestException(UNREADABLE));
    }
}
