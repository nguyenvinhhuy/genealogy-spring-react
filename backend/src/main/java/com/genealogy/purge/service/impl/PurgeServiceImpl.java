package com.genealogy.purge.service.impl;

import com.genealogy.branch.dto.response.BranchResponse;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.MediaTargetType;
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
import com.genealogy.purge.dto.response.RowsForgotten;
import com.genealogy.purge.service.PurgeService;
import com.genealogy.source.service.SourceService;
import com.genealogy.suggestion.service.SuggestionService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link PurgeService}. */
// Its own feature like `merge`: the others depend on `person`, so it cannot call them back without a cycle.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurgeServiceImpl implements PurgeService {

    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;
    private final SourceService sourceService;
    private final SuggestionService suggestionService;
    private final MediaService mediaService;
    private final BranchService branchService;
    private final GraveService graveService;
    private final PlaceService placeService;
    private final AdvisoryLock advisoryLock;

    /**
     * Deletes a person and every row that names them, refusing when the delete would orphan children.
     *
     * @param personId the person to delete
     * @param actorId id of the member making the change
     * @param changeNote why the person is being deleted, or null
     * @return what was deleted along the way
     */
    @Override
    @Transactional
    public PurgeResponse purgePerson(Long personId, Long actorId, String changeNote) {
        if (!personService.exists(personId)) {
            throw new NotFoundException("Không có người với id " + personId);
        }
        // Locked before the check: a child linked meanwhile would otherwise lose their only recorded parent.
        advisoryLock.lock(AdvisoryLock.PARENTAGE);
        requireNoOrphanedChildren(personId);

        // Four tables name a person by id with no foreign key, so nothing else would ever reclaim their rows.
        int media = mediaService.forgetTarget(MediaTargetType.PERSON, personId, actorId, changeNote);
        // Deleted here, not left to the V4 cascade: its photos and citations name it by id, and the delete is audited.
        RowsForgotten grave = graveService.idOf(personId)
                .map(graveId -> deleteGrave(personId, graveId, actorId, changeNote))
                .orElse(new RowsForgotten(0, 0, 0));
        media += grave.files();
        ForgetSummary personEvents = forgetEventsOf(EventSubjectType.PERSON, personId, actorId, changeNote);
        int events = personEvents.events();
        int citations = grave.citations() + personEvents.citations()
                + sourceService.forgetTarget(CitationTargetType.PERSON, personId, actorId, changeNote);
        int suggestions = suggestionService.forgetTarget(personId, actorId, changeNote);
        List<Long> unions = familyService.dropUnionsWithOnlyThisPartner(personId, actorId, changeNote);
        // A dropped union's wedding, citations and photos name it by id too, so they go with it.
        for (Long familyId : unions) {
            RowsForgotten forgotten = forgetUnionRows(familyId, actorId, changeNote);
            events += forgotten.events();
            citations += forgotten.citations();
            media += forgotten.files();
        }
        suggestionService.reanchorUnions(Map.of(), unions, actorId, changeNote);

        // The revision trail is deliberately left behind: it outlives its subject (CLAUDE.md §3.8).
        personService.delete(personId, actorId, changeNote);
        familyService.recomputeGenerations();

        log.info("Purged person {} by member {}: {} events, {} citations, {} files, {} suggestions, {} unions",
                personId, actorId, events, citations, media, suggestions, unions.size());
        return new PurgeResponse(personId, events, citations, media, suggestions, unions.size());
    }

    /**
     * Deletes a union together with its events, citations and media, refusing when it still has children.
     *
     * @param familyId the union to delete
     * @param actorId id of the member making the change
     * @param changeNote why the union is being deleted, or null
     */
    @Override
    @Transactional
    public void purgeUnion(Long familyId, Long actorId, String changeNote) {
        // Checked first so an unknown id is a 404 before anything is forgotten under it.
        if (!familyService.exists(familyId)) {
            throw new NotFoundException("Không có hôn nhân với id " + familyId);
        }
        // Locked before the count, which a child linked meanwhile would make stale (§8.10 #4).
        advisoryLock.lock(AdvisoryLock.PARENTAGE);
        int children = familyService.childCount(familyId);
        if (children > 0) {
            // Consistent with purgePerson and merge: a child does not silently lose their recorded parents.
            throw new BadRequestException("Hôn nhân #" + familyId + " còn " + children
                    + " người con. Hãy gỡ các con khỏi hôn nhân này trước rồi mới xoá.");
        }
        RowsForgotten forgotten = forgetUnionRows(familyId, actorId, changeNote);
        familyService.delete(familyId, actorId, changeNote);
        // A proposed child anchored to this union would 404 on approval for ever (§8.12 #6).
        suggestionService.reanchorUnions(Map.of(), List.of(familyId), actorId, changeNote);
        log.info("Purged union {} by member {}: {} events, {} citations, {} files",
                familyId, actorId, forgotten.events(), forgotten.citations(), forgotten.files());
    }

    /**
     * Deletes every row that names a union by id, for a union that is about to go or has just been folded away.
     *
     * @param familyId the union
     * @param actorId id of the member making the change
     * @param changeNote why, recorded on every row deleted
     * @return what was deleted
     */
    @Override
    @Transactional
    public RowsForgotten forgetUnionRows(Long familyId, Long actorId, String changeNote) {
        ForgetSummary unionEvents = forgetEventsOf(EventSubjectType.FAMILY, familyId, actorId, changeNote);
        int citations = unionEvents.citations()
                + sourceService.forgetTarget(CitationTargetType.FAMILY, familyId, actorId, changeNote);
        int files = mediaService.forgetTarget(MediaTargetType.FAMILY, familyId, actorId, changeNote);
        return new RowsForgotten(unionEvents.events(), citations, files);
    }

    /**
     * Deletes every row that names a grave by id, for a grave that is about to go or has just been dropped.
     *
     * @param graveId the grave
     * @param actorId id of the member making the change
     * @param changeNote why, recorded on every row deleted
     * @return what was deleted
     */
    @Override
    @Transactional
    public RowsForgotten forgetGraveRows(Long graveId, Long actorId, String changeNote) {
        // `media` and `citations` name a grave by id with no FK, so they would orphan once the row is gone.
        int files = mediaService.forgetTarget(MediaTargetType.GRAVE, graveId, actorId, changeNote);
        int citations = sourceService.forgetTarget(CitationTargetType.GRAVE, graveId, actorId, changeNote);
        return new RowsForgotten(0, citations, files);
    }

    /**
     * Deletes an event and the citations that name it, since a citation keys on an event id with no foreign key.
     *
     * @param eventId the event to delete
     * @param actorId id of the member making the change
     * @param changeNote why the event is being deleted, or null
     */
    @Override
    @Transactional
    public void purgeEvent(Long eventId, Long actorId, String changeNote) {
        // Checked first so an unknown id is a 404 before anything is forgotten under it.
        if (!eventService.exists(eventId)) {
            throw new NotFoundException("Không có sự kiện với id " + eventId);
        }
        sourceService.forgetTarget(CitationTargetType.EVENT, eventId, actorId, changeNote);
        eventService.delete(eventId, actorId, changeNote);
    }

    /**
     * Deletes a place, refusing while an event, a grave or a smaller place still names it.
     *
     * @param placeId the place to delete
     * @param actorId id of the member making the change
     * @param changeNote why the place is being deleted, or null
     */
    @Override
    @Transactional
    public void purgePlace(Long placeId, Long actorId, String changeNote) {
        PlaceResponse place = placeService.getById(placeId);
        long events = eventService.countByPlace(placeId);
        long graves = graveService.countByPlace(placeId);
        // Both FKs were ON DELETE SET NULL, so a delete silently erased where cụ was born and buried (§8.8 #6).
        if (events > 0 || graves > 0) {
            throw new BadRequestException("Nơi chốn \"" + place.name() + "\" còn được ghi ở " + events
                    + " sự kiện và " + graves + " mộ phần. Hãy chuyển chúng sang nơi khác trước rồi mới xoá.");
        }
        // Smaller places beneath it are checked inside placeService.delete, which owns the tree.
        placeService.delete(placeId, actorId, changeNote);
    }

    /**
     * Deletes a source, refusing while anything cites it or a scan still hangs off it.
     *
     * @param sourceId the source to delete
     * @param actorId id of the member making the change
     * @param changeNote why the source is being deleted, or null
     */
    @Override
    @Transactional
    public void purgeSource(Long sourceId, Long actorId, String changeNote) {
        // Checked here, not in `source`: media depends on source, so source cannot ask media back (§8.9 D2).
        int scans = mediaService.countByTarget(MediaTargetType.SOURCE, sourceId);
        if (scans > 0) {
            throw new BadRequestException("Nguồn này còn " + scans
                    + " tệp scan. Hãy gộp nó vào một nguồn khác, hoặc xoá các tệp trước.");
        }
        // The citations are checked inside sourceService.delete, which owns them.
        sourceService.delete(sourceId, actorId, changeNote);
    }

    /**
     * Deletes a person's grave record together with the photos and citations that name it.
     *
     * @param personId whose grave it is
     * @param actorId id of the member making the change
     * @param changeNote why the grave is being removed, or null
     */
    @Override
    @Transactional
    public void purgeGrave(Long personId, Long actorId, String changeNote) {
        Long graveId = graveService.idOf(personId)
                .orElseThrow(() -> new NotFoundException("Chưa ghi mộ phần cho người này"));
        RowsForgotten deleted = deleteGrave(personId, graveId, actorId, changeNote);
        log.info("Purged the grave of person {} by member {}: {} files, {} citations",
                personId, actorId, deleted.files(), deleted.citations());
    }

    /**
     * Deletes one grave record after the photos and citations that name it by id.
     *
     * @param personId whose grave it is
     * @param graveId the grave id
     * @param actorId id of the member making the change
     * @param changeNote why the grave is being removed
     * @return how many photos and citations went with it
     */
    private RowsForgotten deleteGrave(Long personId, Long graveId, Long actorId, String changeNote) {
        RowsForgotten forgotten = forgetGraveRows(graveId, actorId, changeNote);
        graveService.delete(personId, actorId, changeNote);
        return forgotten;
    }

    /**
     * Deletes a chi, refusing when anyone still belongs to it or a sub-branch still sits beneath it.
     *
     * @param branchId the chi to delete
     * @param actorId id of the member making the change
     * @param changeNote why the chi is being deleted, or null
     */
    @Override
    @Transactional
    public void purgeBranch(Long branchId, Long actorId, String changeNote) {
        BranchResponse branch = branchService.getById(branchId);
        long members = personService.countInBranch(branchId);
        if (members > 0) {
            throw new BadRequestException("Chi \"" + branch.name() + "\" còn " + members
                    + " người. Hãy chuyển họ sang chi khác trước rồi mới xoá.");
        }
        // The sub-branch count is checked again inside branchService.delete; this message covers both at once.
        branchService.delete(branchId, actorId, changeNote);
    }

    /**
     * Refuses a delete that would leave children with no recorded parent at all.
     *
     * @param personId the person being deleted
     */
    private void requireNoOrphanedChildren(Long personId) {
        // `family_children.family_id` cascades on delete (V3), so dropping the union takes its children too.
        List<SharedUnion> blocking = familyService.soleParentUnionsWithChildren(personId);
        if (blocking.isEmpty()) {
            return;
        }

        SharedUnion union = blocking.getFirst();
        throw new BadRequestException(
                "Người này là cha/mẹ duy nhất được ghi ở hôn nhân #" + union.familyId()
                        + ", mà hôn nhân đó có " + union.childCount() + " người con. Xoá sẽ mất"
                        + " liên kết cha/mẹ của họ, nên hãy ghi cha/mẹ còn lại hoặc chuyển những"
                        + " người con sang hôn nhân khác trước.");
    }

    /**
     * Deletes a subject's events together with the citations that name each one by id.
     *
     * @param subjectType whether the subject is a person or a union
     * @param subjectId the subject id
     * @param actorId id of the member making the change
     * @param changeNote why the events are being deleted, recorded on each one
     * @return how many events and how many event citations were deleted
     */
    private ForgetSummary forgetEventsOf(
            EventSubjectType subjectType, Long subjectId, Long actorId, String changeNote) {
        // Ids are read before the delete, or there is nothing left naming which citations belonged to them.
        List<Long> eventIds = eventService.findIdsBySubject(subjectType, subjectId);
        int events = eventService.forgetSubject(subjectType, subjectId, actorId, changeNote);
        // One read for every event's citations, not one query per event (§8.8 #33).
        int citations = sourceService.forgetTargets(CitationTargetType.EVENT, eventIds, actorId, changeNote);
        return new ForgetSummary(events, citations);
    }

    /** How many events and how many of their citations one {@link #forgetEventsOf} call deleted. */
    private record ForgetSummary(int events, int citations) {
    }
}
