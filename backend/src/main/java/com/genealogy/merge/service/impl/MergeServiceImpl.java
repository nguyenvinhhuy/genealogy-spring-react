package com.genealogy.merge.service.impl;

import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.model.CitationTargetType;
import com.genealogy.common.model.MediaTargetType;
import com.genealogy.common.model.Role;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.ReassignResult;
import com.genealogy.family.dto.response.SharedUnion;
import com.genealogy.family.service.FamilyService;
import com.genealogy.grave.service.GraveService;
import com.genealogy.media.service.MediaService;
import com.genealogy.merge.dto.request.MergeRequest;
import com.genealogy.merge.dto.response.MergeResponse;
import com.genealogy.merge.service.MergeService;
import com.genealogy.person.dto.response.PersonView;
import com.genealogy.person.service.PersonService;
import com.genealogy.purge.dto.response.RowsForgotten;
import com.genealogy.purge.service.PurgeService;
import com.genealogy.source.dto.request.SourceMergeRequest;
import com.genealogy.source.dto.response.CitationsMoved;
import com.genealogy.source.dto.response.SourceMergeResponse;
import com.genealogy.source.service.SourceService;
import com.genealogy.suggestion.dto.response.SuggestionsMoved;
import com.genealogy.suggestion.service.SuggestionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link MergeService}. */
// Its own feature: `event` and `family` depend on `person`, so orchestrating there closes a bean cycle.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MergeServiceImpl implements MergeService {

    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;
    private final GraveService graveService;
    private final SourceService sourceService;
    private final SuggestionService suggestionService;
    private final MediaService mediaService;
    private final PurgeService purgeService;
    private final AdvisoryLock advisoryLock;

    /**
     * Merges one person into another and deletes the duplicate.
     *
     * @param request which two people, and on what basis
     * @param actorId id of the member running the merge
     * @return what was moved, folded or dropped
     */
    @Override
    @Transactional
    public MergeResponse mergePersons(MergeRequest request, Long actorId) {
        Long targetId = request.targetId();
        Long duplicateId = request.duplicateId();
        // Locked before the checks: outside it, a child linked meanwhile slipped past the cycle guard (§8.10 #4).
        advisoryLock.lock(AdvisoryLock.PARENTAGE);
        requireMergeable(targetId, duplicateId);

        List<String> notes = new ArrayList<>();
        // Repointed before the row is deleted: family links and the grave cascade, so the other order loses.
        ReassignResult family = familyService.reassignPerson(duplicateId, targetId, actorId, request.reason());
        notes.addAll(family.notes());
        int unionMediaMoved = followTheUnions(family, notes, actorId, request.reason());
        // A proposed child pointing at a folded or dropped union would 404 on approval for ever (§8.12 #6).
        int reanchored = suggestionService.reanchorUnions(
                family.unionsFoldedInto(), family.unionsDropped(), actorId, request.reason());
        if (reanchored > 0) {
            notes.add("Chuyển " + reanchored + " đề xuất thêm người sang hôn nhân còn lại");
        }
        int eventsMoved = eventService.reassignPerson(duplicateId, targetId, actorId, request.reason());
        graveService.reassignPerson(duplicateId, targetId, actorId, request.reason()).ifPresent(dropped -> {
            // Dropped rather than moved, so its photos and citations would orphan behind no foreign key.
            RowsForgotten gone = purgeService.forgetGraveRows(dropped.graveId(), actorId, request.reason());
            notes.add(dropped.note() + (gone.files() + gone.citations() > 0
                    ? "; xoá theo " + gone.files() + " tệp và " + gone.citations() + " trích dẫn của mộ đó"
                    : ""));
        });
        CitationsMoved citations = sourceService.reassignTarget(
                CitationTargetType.PERSON, duplicateId, targetId, actorId, request.reason());
        // A folded citation keeps its quote on the survivor's copy, but the merge still says it happened (§8.8 #8).
        notes.addAll(citations.folded());
        int citationsMoved = citations.moved();
        SuggestionsMoved suggestions =
                suggestionService.reassignTarget(duplicateId, targetId, actorId, request.reason());
        if (suggestions.moved() > 0) {
            notes.add("Chuyển " + suggestions.moved() + " đề xuất sang người được giữ"
                    + (suggestions.turnedIntoNotes() > 0
                            ? "; " + suggestions.turnedIntoNotes() + " đề xuất sửa đang chờ đổi thành góp ý, "
                                    + "vì chúng được viết cho bản trùng"
                            : ""));
        }
        // The union's photos count too: a merge that folded a wedding album used to report "0 tệp" (§8.9 #6).
        int mediaMoved = unionMediaMoved + mediaService.reassignTarget(
                MediaTargetType.PERSON, duplicateId, targetId, actorId, request.reason());

        int namesMoved = personService.absorb(targetId, duplicateId, actorId, request.reason());
        // Đời is recomputed across the whole graph, never patched (§3.4), and the graph just changed shape.
        familyService.recomputeGenerations();

        PersonView target = personService.getById(targetId, Role.ADMIN);
        log.info("Merged person {} into {} by member {}: {}", duplicateId, targetId, actorId, request.reason());
        return new MergeResponse(
                targetId,
                target.displayName(),
                duplicateId,
                namesMoved,
                family.unionsMoved(),
                family.unionsCollapsed(),
                family.childLinksMoved(),
                eventsMoved,
                citationsMoved,
                mediaMoved,
                List.copyOf(notes));
    }

    /**
     * Folds one source into another: its citations and its scans move onto the kept source, and it is deleted.
     *
     * @param request which source is folded into which, and why
     * @param actorId id of the member running the merge
     * @return the kept source and what moved
     */
    @Override
    @Transactional
    public SourceMergeResponse mergeSources(SourceMergeRequest request, Long actorId) {
        // The source merge refuses bad ids before anything moves; the scans follow once it has run.
        SourceMergeResponse merged = sourceService.merge(request, actorId);
        int scans = mediaService.reassignTarget(
                MediaTargetType.SOURCE, request.duplicateId(), request.targetId(), actorId, request.reason());
        return new SourceMergeResponse(merged.source(), merged.citationsMoved(), scans, merged.folded());
    }

    /**
     * Carries the rows keyed on a union id through the unions the merge folded away or dropped.
     *
     * @param family what the family feature did to the duplicate's unions
     * @param notes collects what was deleted rather than moved
     * @param actorId the member running the merge
     * @param reason the merge's reason, recorded on every event moved or deleted
     * @return how many of the folded unions' files moved
     */
    private int followTheUnions(ReassignResult family, List<String> notes, Long actorId, String reason) {
        // No FK ties events, citations or media to `families`, so a dead union id leaves them unreachable.
        int mediaMoved = 0;
        for (Map.Entry<Long, Long> fold : family.unionsFoldedInto().entrySet()) {
            Long folded = fold.getKey();
            Long kept = fold.getValue();
            eventService.reassignFamily(folded, kept, actorId, reason);
            notes.addAll(sourceService.reassignTarget(CitationTargetType.FAMILY, folded, kept, actorId, reason)
                    .folded());
            mediaMoved += mediaService.reassignTarget(MediaTargetType.FAMILY, folded, kept, actorId, reason);
        }

        for (Long droppedId : family.unionsDropped()) {
            RowsForgotten gone = purgeService.forgetUnionRows(droppedId, actorId, reason);
            if (gone.events() + gone.citations() + gone.files() > 0) {
                notes.add("Hôn nhân #" + droppedId + " bị bỏ nên xoá theo: " + gone.events() + " sự kiện, "
                        + gone.citations() + " trích dẫn, " + gone.files() + " tệp");
            }
        }
        return mediaMoved;
    }

    /**
     * Refuses a merge that would corrupt the graph rather than tidy it.
     *
     * @param targetId the person to keep
     * @param duplicateId the person to absorb
     */
    private void requireMergeable(Long targetId, Long duplicateId) {
        if (targetId.equals(duplicateId)) {
            throw new BadRequestException("Không thể gộp một người vào chính họ");
        }
        if (!personService.exists(targetId)) {
            throw new BadRequestException("Không có người với id " + targetId);
        }
        if (!personService.exists(duplicateId)) {
            throw new BadRequestException("Không có người với id " + duplicateId);
        }
        requireNoSharedUnionWithChildren(targetId, duplicateId);
        // Merging an ancestor into a descendant makes them their own ancestor, an ERROR under §5.1.
        if (familyService.inOneLineOfDescent(targetId, duplicateId)) {
            throw new BadRequestException("Hai người này là tổ tiên/con cháu của nhau, gộp lại sẽ thành vòng lặp");
        }
    }

    /**
     * Refuses a merge that would drop a union whose children would lose their recorded parents.
     *
     * @param targetId the person to keep
     * @param duplicateId the person to absorb
     */
    private void requireNoSharedUnionWithChildren(Long targetId, Long duplicateId) {
        // `family_children` cascades on delete (V3), so dropping the union takes its children's parentage too.
        List<SharedUnion> withChildren = familyService.findSharedUnions(targetId, duplicateId).stream()
                .filter(union -> union.childCount() > 0)
                .toList();
        if (withChildren.isEmpty()) {
            return;
        }

        SharedUnion union = withChildren.getFirst();
        throw new BadRequestException(
                "Hai người này đang được ghi là vợ chồng của nhau ở hôn nhân #" + union.familyId()
                        + ", mà hôn nhân đó có " + union.childCount() + " người con. Gộp lại sẽ xoá mất"
                        + " liên kết cha/mẹ của họ, nên hãy sửa hôn nhân #" + union.familyId()
                        + " trước rồi gộp sau.");
    }
}
