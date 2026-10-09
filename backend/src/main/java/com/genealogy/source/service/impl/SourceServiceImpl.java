package com.genealogy.source.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.ForbiddenException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.exception.ProblemMessages;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.Role;
import com.genealogy.common.util.Blank;
import com.genealogy.common.util.LikePattern;
import com.genealogy.common.util.StaleEdit;
import com.genealogy.common.web.PageRequests;
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
import com.genealogy.source.dto.response.SourceResponse;
import com.genealogy.source.mapper.SourceMapper;
import com.genealogy.source.repository.CitationRepository;
import com.genealogy.source.repository.SourceRepository;
import com.genealogy.source.service.SourceService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link SourceService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceServiceImpl implements SourceService {

    private static final String CITED_ALREADY = ProblemMessages.CITED_ALREADY;
    private static final String UQ_CITATIONS = "uq_citations";

    private final SourceRepository sourceRepository;
    private final CitationRepository citationRepository;
    private final SourceMapper sourceMapper;
    private final AuditService auditService;
    // Cross-feature by interface only (CLAUDE.md §4): no other feature's entity is imported here.
    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;
    private final GraveService graveService;

    /**
     * Lists sources ordered by title, optionally filtered by an accent-insensitive title search, for EDITOR+ only.
     *
     * @param query the search text, or null for no filter
     * @param pageable paging information; its sort is ignored
     * @param role the calling member's access level
     * @return the matching page
     */
    @Override
    public Page<SourceResponse> search(String query, Pageable pageable, Role role) {
        requireReader(role);
        // A client sort on a native query reaches SQL as a raw column name, and title order is the only useful one.
        Pageable page = PageRequests.capped(pageable);
        String like = LikePattern.orNull(query);
        Page<Source> found = like == null
                ? sourceRepository.findAllByOrderByTitleAscIdAsc(page)
                : sourceRepository.searchByTitle(like, page);
        return new PageImpl<>(toResponses(found.getContent()), page, found.getTotalElements());
    }

    /**
     * Returns one source, for EDITOR+ only.
     *
     * @param id source id
     * @param role the calling member's access level
     * @return the source
     */
    @Override
    public SourceResponse getById(Long id, Role role) {
        requireReader(role);
        return toResponse(require(id));
    }

    /**
     * Reports whether a source exists.
     *
     * @param id source id
     * @return true if it exists
     */
    @Override
    public boolean exists(Long id) {
        return sourceRepository.existsById(id);
    }

    /**
     * Creates a source and records it in the audit trail.
     *
     * @param request the source to create
     * @param actorId id of the member adding it
     * @return the created source
     */
    @Override
    @Transactional
    public SourceResponse create(SourceRequest request, Long actorId) {
        SourceResponse created = sourceMapper.toResponse(insert(request, actorId), 0);
        auditService.record(AuditEntityType.SOURCE, created.id(), AuditAction.CREATE, null, created, actorId,
                request.changeNote());
        return created;
    }

    /**
     * Updates a source and records it in the audit trail.
     *
     * @param id source id
     * @param request the new values
     * @param actorId id of the member making the change
     * @return the updated source
     */
    @Override
    @Transactional
    public SourceResponse update(Long id, SourceRequest request, Long actorId) {
        Source source = require(id);
        StaleEdit.refuseIfStale(request.version(), source.getVersion(), AuditEntityType.SOURCE);
        SourceResponse before = toResponse(source);
        sourceMapper.update(request, source);
        // Flushed so the response carries the bumped version, or the form's next save is refused as stale.
        sourceRepository.flush();
        SourceResponse after = sourceMapper.toResponse(source, before.citationCount());
        auditService.record(AuditEntityType.SOURCE, id, AuditAction.UPDATE, before, after, actorId,
                request.changeNote());
        return after;
    }

    /**
     * Deletes a source, refusing while anything still cites it.
     *
     * @param id source id
     * @param actorId id of the member making the change
     * @param changeNote why the source is being deleted, or null
     */
    @Override
    @Transactional
    public void delete(Long id, Long actorId, String changeNote) {
        Source source = require(id);
        long cited = citationRepository.countBySourceId(id);
        // Citations cascaded away with their source (V6): deleting one silently took every "on what basis" (§3.8).
        if (cited > 0) {
            throw new BadRequestException("Nguồn \"" + source.getTitle() + "\" đang được trích dẫn " + cited
                    + " lần. Hãy gộp nó vào một nguồn khác thay vì xoá.");
        }
        SourceResponse before = sourceMapper.toResponse(source, 0);
        sourceRepository.delete(source);
        sourceRepository.flush();
        auditService.record(AuditEntityType.SOURCE, id, AuditAction.DELETE, before, null, actorId, changeNote);
    }

    /**
     * Folds one source into another: its citations move onto the kept source and it is deleted.
     *
     * @param request which source is folded into which, and why
     * @param actorId id of the member running the merge
     * @return the kept source and what moved
     */
    @Override
    @Transactional
    public SourceMergeResponse merge(SourceMergeRequest request, Long actorId) {
        if (request.duplicateId().equals(request.targetId())) {
            throw new BadRequestException("Không gộp được một nguồn vào chính nó");
        }
        Source duplicate = requireForBody(request.duplicateId());
        Source kept = requireForBody(request.targetId());

        List<String> folded = new ArrayList<>();
        int moved = 0;
        for (Citation citation : citationRepository.findBySourceIdOrderByIdAsc(duplicate.getId())) {
            if (move(citation, kept.getId(), citation.getTargetId(), actorId, request.reason(), folded)) {
                moved++;
            }
        }
        citationRepository.flush();

        SourceResponse before = sourceMapper.toResponse(duplicate, 0);
        sourceRepository.delete(duplicate);
        sourceRepository.flush();
        auditService.record(AuditEntityType.SOURCE, before.id(), AuditAction.DELETE, before, null, actorId,
                request.reason());
        // The scans are moved by `merge`, which may call media; this feature may not (§8.9 D2).
        return new SourceMergeResponse(toResponse(kept), moved, 0, folded);
    }

    /**
     * Lists the citations backing up one record, hiding a living person's from callers below EDITOR.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param role the calling member's access level
     * @return the citations, or nothing when the record involves a living person the caller may not see
     */
    @Override
    public List<CitationResponse> findCitations(CitationTargetType targetType, Long targetId, Role role) {
        // A quote is verbatim source text and routinely carries the very birth date §3.6 withholds.
        if (!Role.maySeeLivingDetails(role) && targetIsLiving(targetType, targetId)) {
            return List.of();
        }
        return toCitationResponses(citationRepository.findByTargetTypeAndTargetIdOrderByIdAsc(targetType, targetId));
    }

    /**
     * Cites a source against one recorded fact, creating the source first when the request describes a new one.
     *
     * @param request the citation to add
     * @param actorId id of the member making the change
     * @return the created citation
     */
    @Override
    @Transactional
    public CitationResponse addCitation(CitationRequest request, Long actorId) {
        requireTargetExists(request.targetType(), request.targetId());
        // One transaction for both: two requests left an orphan source behind every retry (§8.8 #16).
        Source source = resolveSource(request, actorId);
        String locator = locatorOf(request.locator());
        if (!citationRepository.findColliding(
                source.getId(), request.targetType().name(), request.targetId(), locator).isEmpty()) {
            throw new ConflictException(CITED_ALREADY);
        }

        Citation citation = new Citation();
        sourceMapper.update(request, citation);
        citation.setSourceId(source.getId());
        citation.setTargetType(request.targetType());
        citation.setTargetId(request.targetId());
        citation.setLocator(locator);
        Citation saved = saveCitation(citation);

        CitationResponse created = sourceMapper.toResponse(saved, source);
        auditService.record(AuditEntityType.CITATION, created.id(), AuditAction.CREATE, null, created, actorId,
                request.changeNote());
        return created;
    }

    /**
     * Changes which source a citation names, where in it, and what it quotes.
     *
     * @param id citation id
     * @param request the new values; its target must be the citation's own
     * @param actorId id of the member making the change
     * @return the updated citation
     */
    @Override
    @Transactional
    public CitationResponse updateCitation(Long id, CitationRequest request, Long actorId) {
        Citation citation = requireCitation(id);
        StaleEdit.refuseIfStale(request.version(), citation.getVersion(), AuditEntityType.CITATION);
        // A citation backs up one fact; pointing it at another is a new citation, not a correction of this one.
        if (citation.getTargetType() != request.targetType() || !citation.getTargetId().equals(request.targetId())) {
            throw new BadRequestException("Không chuyển được dẫn chứng sang bản ghi khác; hãy thêm dẫn chứng mới");
        }
        Source source = resolveSource(request, actorId);
        String locator = locatorOf(request.locator());
        boolean collides = citationRepository.findColliding(
                        source.getId(), citation.getTargetType().name(), citation.getTargetId(), locator).stream()
                .anyMatch(other -> !other.equals(id));
        if (collides) {
            throw new ConflictException(CITED_ALREADY);
        }

        CitationResponse before = toCitationResponse(citation);
        sourceMapper.update(request, citation);
        citation.setSourceId(source.getId());
        citation.setLocator(locator);
        Citation saved = saveCitation(citation);
        CitationResponse after = sourceMapper.toResponse(saved, source);
        auditService.record(AuditEntityType.CITATION, id, AuditAction.UPDATE, before, after, actorId,
                request.changeNote());
        return after;
    }

    /**
     * Removes one citation, leaving its source alone.
     *
     * @param id citation id
     * @param actorId id of the member making the change
     * @param changeNote why the citation is being removed, or null
     */
    @Override
    @Transactional
    public void removeCitation(Long id, Long actorId, String changeNote) {
        Citation citation = requireCitation(id);
        CitationResponse before = toCitationResponse(citation);
        citationRepository.delete(citation);
        citationRepository.flush();
        auditService.record(AuditEntityType.CITATION, id, AuditAction.DELETE, before, null, actorId, changeNote);
    }

    /**
     * Lists every source in the clan.
     *
     * @return every source, ordered by id so an export is reproducible
     */
    @Override
    public List<SourceResponse> findAllSources() {
        Map<Long, Long> counts = countsOf(citationRepository.countGroupedByAllSources());
        return sourceRepository.findAll(Sort.by("id")).stream()
                .map(source -> sourceMapper.toResponse(source, counts.getOrDefault(source.getId(), 0L)))
                .toList();
    }

    /**
     * Lists every citation in the clan.
     *
     * @return every citation, ordered by id
     */
    @Override
    public List<CitationResponse> findAllCitations() {
        return toCitationResponses(citationRepository.findAll(Sort.by("id")));
    }

    /**
     * Moves every citation off one target onto another, for a merge, folding any that would collide.
     *
     * @param targetType what kind of record is being merged
     * @param fromId the record being absorbed
     * @param toId the record being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason, recorded on every citation it moves
     * @return how many moved, and a line for each that was folded into one already on the kept record
     */
    @Override
    @Transactional
    public CitationsMoved reassignTarget(
            CitationTargetType targetType, Long fromId, Long toId, Long actorId, String changeNote) {
        List<String> folded = new ArrayList<>();
        int moved = 0;
        for (Citation citation : citationRepository.findByTargetTypeAndTargetIdOrderByIdAsc(targetType, fromId)) {
            if (move(citation, citation.getSourceId(), toId, actorId, changeNote, folded)) {
                moved++;
            }
        }
        citationRepository.flush();
        return new CitationsMoved(moved, folded);
    }

    /**
     * Deletes the citations of a record that is itself being deleted, recording each one.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     * @param actorId the member deleting the record
     * @param changeNote why the record is being deleted
     * @return how many citations were deleted
     */
    @Override
    @Transactional
    public int forgetTarget(CitationTargetType targetType, Long targetId, Long actorId, String changeNote) {
        return forgetTargets(targetType, List.of(targetId), actorId, changeNote);
    }

    /**
     * Deletes the citations of several records of one kind that are being deleted, in one read.
     *
     * @param targetType what kind of record
     * @param targetIds the record ids
     * @param actorId the member deleting the records
     * @param changeNote why the records are being deleted
     * @return how many citations were deleted
     */
    @Override
    @Transactional
    public int forgetTargets(
            CitationTargetType targetType, Collection<Long> targetIds, Long actorId, String changeNote) {
        if (targetIds.isEmpty()) {
            return 0;
        }
        // `citations.target_id` has no FK, so nothing else would ever reclaim these rows.
        List<Citation> citations = citationRepository.findByTargetTypeAndTargetIdInOrderByIdAsc(targetType, targetIds);
        List<CitationResponse> before = toCitationResponses(citations);
        citationRepository.deleteAll(citations);
        citationRepository.flush();
        // The trail outlives its subject (§3.8): a purged cụ's quotes stay readable in the history.
        before.forEach(citation -> auditService.record(AuditEntityType.CITATION, citation.id(), AuditAction.DELETE,
                citation, null, actorId, changeNote));
        return citations.size();
    }

    /**
     * Moves one citation onto another source or target, or folds it into the one it would collide with there.
     *
     * @param citation the citation to move
     * @param sourceId the source it should name
     * @param targetId the record it should back up
     * @param actorId the member running the merge
     * @param changeNote the merge's reason
     * @param folded collects a line for each citation folded rather than moved
     * @return true when it moved, false when it was folded
     */
    private boolean move(
            Citation citation, Long sourceId, Long targetId, Long actorId, String changeNote, List<String> folded) {
        CitationResponse before = toCitationResponse(citation);
        List<Long> colliding = citationRepository.findColliding(
                sourceId, citation.getTargetType().name(), targetId, citation.getLocator());
        if (colliding.isEmpty()) {
            citation.setSourceId(sourceId);
            citation.setTargetId(targetId);
            citationRepository.flush();
            auditService.record(AuditEntityType.CITATION, citation.getId(), AuditAction.UPDATE, before,
                    toCitationResponse(citation), actorId, changeNote);
            return true;
        }

        // uq_citations forbids both rows; the quote is what the source says, so it is kept, never dropped (§8.8 #8).
        Citation kept = requireCitation(colliding.getFirst());
        String quote = foldQuote(kept.getQuote(), citation.getQuote());
        if (!Objects.equals(quote, kept.getQuote())) {
            CitationResponse keptBefore = toCitationResponse(kept);
            kept.setQuote(quote);
            citationRepository.flush();
            auditService.record(AuditEntityType.CITATION, kept.getId(), AuditAction.UPDATE, keptBefore,
                    toCitationResponse(kept), actorId, changeNote);
        }
        citationRepository.delete(citation);
        citationRepository.flush();
        auditService.record(AuditEntityType.CITATION, citation.getId(), AuditAction.DELETE, before, null, actorId,
                changeNote);
        folded.add("Dẫn chứng #" + citation.getId() + " trùng với #" + kept.getId()
                + (citation.getLocator() == null ? "" : " (" + citation.getLocator() + ")")
                + ", đã gộp vào đó");
        return false;
    }

    /**
     * Combines two quotes from the same source at the same place, keeping every distinct word of both.
     *
     * @param kept the quote on the citation being kept, or null
     * @param moving the quote on the citation being folded away, or null
     * @return the combined quote
     */
    private static String foldQuote(String kept, String moving) {
        return Blank.join(kept, moving);
    }

    /**
     * Saves a citation, reading only a {@code uq_citations} refusal as "already cited".
     *
     * @param citation the citation to save
     * @return the saved citation
     */
    private Citation saveCitation(Citation citation) {
        try {
            return citationRepository.saveAndFlush(citation);
        } catch (DataIntegrityViolationException ex) {
            // Checked first; this is a concurrent write, and any other constraint is not "already cited" (§8.8 #5).
            String cause = ex.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains(UQ_CITATIONS)) {
                throw new ConflictException(CITED_ALREADY);
            }
            throw ex;
        }
    }

    /**
     * Finds the source a citation request names, or creates the one it describes.
     *
     * @param request the citation request
     * @param actorId the member making the change
     * @return the source
     */
    private Source resolveSource(CitationRequest request, Long actorId) {
        if ((request.sourceId() == null) == (request.newSource() == null)) {
            throw new BadRequestException("Hãy chọn một nguồn có sẵn hoặc nhập một nguồn mới, không phải cả hai");
        }
        if (request.sourceId() != null) {
            return requireForBody(request.sourceId());
        }
        Source created = insert(request.newSource(), actorId);
        auditService.record(AuditEntityType.SOURCE, created.getId(), AuditAction.CREATE, null,
                sourceMapper.toResponse(created, 0), actorId, request.changeNote());
        return created;
    }

    /**
     * Inserts a source built from a request.
     *
     * @param request the source to create
     * @param actorId the member adding it
     * @return the saved entity
     */
    private Source insert(SourceRequest request, Long actorId) {
        Source source = sourceMapper.toEntity(request);
        source.setCreatedBy(actorId);
        return sourceRepository.saveAndFlush(source);
    }

    /**
     * Reads a locator as the database compares it: blank is no locator at all.
     *
     * @param raw the locator as typed
     * @return the trimmed locator, or null
     */
    private static String locatorOf(String raw) {
        // "" and null are different to uq_citations, so "" would let a second no-locator citation in.
        return Blank.toNull(raw);
    }

    /**
     * Refuses a caller below EDITOR, who sees a source only through a citation on a record they may see.
     *
     * @param role the calling member's access level
     */
    private static void requireReader(Role role) {
        // The route matcher already stops a MEMBER; this holds when a new endpoint forgets it (§3.6, §8.8 D3).
        if (!Role.isEditorOrAbove(role)) {
            throw new ForbiddenException("Chỉ biên tập viên và trưởng tộc được xem danh sách nguồn");
        }
    }

    /**
     * Rejects a citation of a record that does not exist.
     *
     * @param targetType what kind of record
     * @param targetId the record id
     */
    private void requireTargetExists(CitationTargetType targetType, Long targetId) {
        boolean exists = switch (targetType) {
            case PERSON -> personService.exists(targetId);
            case FAMILY -> familyService.exists(targetId);
            case EVENT -> eventService.exists(targetId);
            case GRAVE -> graveService.exists(targetId);
        };
        if (!exists) {
            throw new BadRequestException("Không có " + AuditEntityType.nounOf(targetType) + " với id " + targetId);
        }
    }

    /**
     * Reports whether a cited record involves anyone still treated as living.
     *
     * @param targetType what kind of record the citation backs up
     * @param targetId the record id
     * @return true when a living person is involved, and true as well when the record is unknown
     */
    private boolean targetIsLiving(CitationTargetType targetType, Long targetId) {
        // Every arm, not just PERSON, and each feature's own guard rather than a copy of it (§3.6, §9).
        return switch (targetType) {
            case PERSON -> personService.isLiving(targetId);
            case FAMILY -> familyService.involvesLiving(targetId);
            case EVENT -> eventService.involvesLiving(targetId);
            case GRAVE -> graveService.involvesLiving(targetId);
        };
    }

    /**
     * Loads a source named by a path id, or fails with a 404.
     *
     * @param id source id
     * @return the entity
     */
    private Source require(Long id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có nguồn với id " + id));
    }

    /**
     * Loads a source named inside a request body, or fails with a 400.
     *
     * @param id source id
     * @return the entity
     */
    private Source requireForBody(Long id) {
        return sourceRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không có nguồn với id " + id));
    }

    /**
     * Loads a citation or fails.
     *
     * @param id citation id
     * @return the entity
     */
    private Citation requireCitation(Long id) {
        return citationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có dẫn chứng với id " + id));
    }

    /**
     * Builds the response for a source, counting how often it has been cited.
     *
     * @param source the entity
     * @return the response DTO
     */
    private SourceResponse toResponse(Source source) {
        return sourceMapper.toResponse(source, citationRepository.countBySourceId(source.getId()));
    }

    /**
     * Builds the responses for a page of sources, counting only their citations, in one query.
     *
     * @param sources the entities
     * @return the response DTOs, in the order given
     */
    private List<SourceResponse> toResponses(List<Source> sources) {
        if (sources.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = countsOf(
                citationRepository.countGroupedBySource(sources.stream().map(Source::getId).toList()));
        return sources.stream()
                .map(source -> sourceMapper.toResponse(source, counts.getOrDefault(source.getId(), 0L)))
                .toList();
    }

    /**
     * Reads a GROUP BY of citation counts into a map.
     *
     * @param rows each source id paired with its count
     * @return the counts by source id
     */
    private static Map<Long, Long> countsOf(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /**
     * Builds the response for one citation, loading its source.
     *
     * @param citation the entity
     * @return the response DTO
     */
    private CitationResponse toCitationResponse(Citation citation) {
        return sourceMapper.toResponse(citation, sourceRepository.findById(citation.getSourceId()).orElse(null));
    }

    /**
     * Builds the responses for a batch of citations, loading their sources in one query.
     *
     * @param citations the entities
     * @return the response DTOs, in the order given
     */
    private List<CitationResponse> toCitationResponses(List<Citation> citations) {
        Set<Long> ids = citations.stream().map(Citation::getSourceId).collect(Collectors.toSet());
        Map<Long, Source> sources = ids.isEmpty()
                ? Map.of()
                : sourceRepository.findAllById(ids).stream().collect(Collectors.toMap(Source::getId, s -> s));
        return citations.stream()
                .map(citation -> sourceMapper.toResponse(citation, sources.get(citation.getSourceId())))
                .toList();
    }
}
