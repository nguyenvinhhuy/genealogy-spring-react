package com.genealogy.grave.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.GraveKind;
import com.genealogy.common.model.Role;
import com.genealogy.common.util.StaleEdit;
import com.genealogy.grave.domain.Grave;
import com.genealogy.grave.dto.request.GraveRequest;
import com.genealogy.grave.dto.response.DroppedGrave;
import com.genealogy.grave.dto.response.GraveResponse;
import com.genealogy.grave.dto.response.GraveSaved;
import com.genealogy.grave.mapper.GraveMapper;
import com.genealogy.grave.repository.GraveRepository;
import com.genealogy.grave.service.GraveChanged;
import com.genealogy.grave.service.GraveService;
import com.genealogy.person.service.PersonService;
import com.genealogy.place.service.PlaceService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link GraveService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GraveServiceImpl implements GraveService {

    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.GRAVE;

    private final GraveRepository graveRepository;
    private final GraveMapper graveMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    // Cross-feature by interface only (CLAUDE.md §4).
    private final PersonService personService;
    private final PlaceService placeService;

    /**
     * Returns the grave recorded for one person, hidden from callers below EDITOR while its owner lives.
     *
     * @param personId the person id
     * @param role the calling member's access level
     * @return the grave, or empty when none is recorded or the caller may not see it
     */
    @Override
    public Optional<GraveResponse> findByPerson(Long personId, Role role) {
        // A sinh phần names a living person's plot and its coordinates, which §3.6 holds back like any detail.
        if (!Role.maySeeLivingDetails(role) && personService.isLiving(personId)) {
            return Optional.empty();
        }
        return graveRepository.findByPersonId(personId).map(graveMapper::toResponse);
    }

    /**
     * Returns the grave recorded for one person, failing with a 404 when there is none the caller may see.
     *
     * @param personId the person id
     * @param role the calling member's access level
     * @return the grave
     */
    @Override
    public GraveResponse getByPerson(Long personId, Role role) {
        // One 404 for "none", "unknown person" and "hidden", so a refusal cannot confirm a sinh phần exists.
        return findByPerson(personId, role)
                .orElseThrow(() -> new NotFoundException("Chưa ghi mộ phần cho người này"));
    }

    /**
     * Lists every grave that has coordinates, hiding a living owner's from callers below EDITOR.
     *
     * @param role the calling member's access level
     * @return the located graves
     */
    @Override
    public List<GraveResponse> findLocated(Role role) {
        List<Grave> located = graveRepository.findByLatitudeIsNotNull();
        if (Role.maySeeLivingDetails(role)) {
            return located.stream().map(graveMapper::toResponse).toList();
        }
        // One whole-clan read rather than one isLiving per grave; a person missing from it fails closed as living.
        Map<Long, Boolean> living = personService.findAllLiving();
        return located.stream()
                .filter(grave -> Boolean.FALSE.equals(living.get(grave.getPersonId())))
                .map(graveMapper::toResponse)
                .toList();
    }

    /**
     * Lists every grave in the clan.
     *
     * @return every grave, ordered by person id so an export is reproducible
     */
    @Override
    public List<GraveResponse> findAll() {
        return graveRepository.findAll(Sort.by("personId")).stream().map(graveMapper::toResponse).toList();
    }

    /**
     * Records or replaces a person's grave and records it in the audit trail.
     *
     * @param personId the person id
     * @param request the grave details
     * @param actorId the member making the change
     * @return the saved grave, and whether it is new
     */
    @Override
    @Transactional
    public GraveSaved save(Long personId, GraveRequest request, Long actorId) {
        if (!personService.exists(personId)) {
            throw new NotFoundException("Không có người với id " + personId);
        }
        placeService.requireExists(request.placeId());
        // A lone coordinate is a slip; the DB rejects it too, but this gives a sentence, not a constraint.
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new BadRequestException("Phải điền cả vĩ độ lẫn kinh độ, hoặc để trống cả hai");
        }

        Optional<Grave> existing = graveRepository.findByPersonId(personId);
        Grave grave = existing.orElseGet(() -> {
            Grave created = new Grave();
            created.setPersonId(personId);
            return created;
        });
        if (existing.isPresent()) {
            StaleEdit.refuseIfStale(request.version(), grave.getVersion(), AuditEntityType.GRAVE);
        } else if (request.version() != null) {
            // A version names a grave the form was read from; saving it now would recreate one somebody deleted.
            throw new ConflictException("Mộ phần này vừa bị xoá. Hãy tải lại trang trước khi ghi lại.");
        }
        GraveResponse before = existing.map(graveMapper::toResponse).orElse(null);
        graveMapper.update(request, grave);
        GraveResponse after = graveMapper.toResponse(graveRepository.saveAndFlush(grave));

        AuditAction action = before == null ? AuditAction.CREATE : AuditAction.UPDATE;
        auditService.record(ENTITY_TYPE, after.id(), action, before, after, actorId, request.changeNote());
        // A mộ counts as a recorded death and a sinh phần does not, so the owner's `living` may have changed (§8.8 D2).
        events.publishEvent(new GraveChanged(personId));
        return new GraveSaved(after, before == null);
    }

    /**
     * Removes a person's grave record and records it in the audit trail.
     *
     * @param personId the person id
     * @param actorId the member making the change
     * @param changeNote why the grave is being removed, or null
     */
    @Override
    @Transactional
    public void delete(Long personId, Long actorId, String changeNote) {
        Grave grave = graveRepository.findByPersonId(personId)
                .orElseThrow(() -> new NotFoundException("Chưa ghi mộ phần cho người này"));
        GraveResponse before = graveMapper.toResponse(grave);
        graveRepository.delete(grave);
        graveRepository.flush();
        // Recorded after the delete, and the trail outlives its subject (§3.8): this is the grave's last state.
        auditService.record(ENTITY_TYPE, before.id(), AuditAction.DELETE, before, null, actorId, changeNote);
        events.publishEvent(new GraveChanged(personId));
    }

    /**
     * Moves a grave off one person onto another, for a merge, recording each change.
     *
     * @param fromId the person being absorbed
     * @param toId the person being kept
     * @param actorId the member running the merge
     * @param changeNote the merge's reason
     * @return the grave that had to be dropped, or empty when it moved or there was none
     */
    @Override
    @Transactional
    public Optional<DroppedGrave> reassignPerson(Long fromId, Long toId, Long actorId, String changeNote) {
        Optional<Grave> moving = graveRepository.findByPersonId(fromId);
        if (moving.isEmpty()) {
            return Optional.empty();
        }
        Grave grave = moving.get();
        GraveResponse before = graveMapper.toResponse(grave);
        if (graveRepository.findByPersonId(toId).isPresent()) {
            // graves.person_id is UNIQUE, so one goes, described in full: cải táng may make it the right one.
            graveRepository.delete(grave);
            graveRepository.flush();
            auditService.record(ENTITY_TYPE, before.id(), AuditAction.DELETE, before, null, actorId, changeNote);
            events.publishEvent(new GraveChanged(fromId));
            return Optional.of(new DroppedGrave(before.id(), "Bỏ mộ phần của bản trùng: " + describe(before)));
        }
        grave.setPersonId(toId);
        graveRepository.flush();
        auditService.record(ENTITY_TYPE, before.id(), AuditAction.UPDATE, before,
                graveMapper.toResponse(grave), actorId, changeNote);
        events.publishEvent(new GraveChanged(fromId));
        events.publishEvent(new GraveChanged(toId));
        return Optional.empty();
    }

    /**
     * Reports whether a grave exists.
     *
     * @param graveId grave id
     * @return true if it exists
     */
    @Override
    public boolean exists(Long graveId) {
        return graveRepository.existsById(graveId);
    }

    /**
     * Returns whose grave one record is.
     *
     * @param graveId grave id
     * @return the person id, or empty when there is no such grave
     */
    @Override
    public Optional<Long> personIdOf(Long graveId) {
        return graveRepository.findById(graveId).map(Grave::getPersonId);
    }

    /**
     * Returns the id of one person's grave record.
     *
     * @param personId the person id
     * @return the grave id, or empty when none is recorded
     */
    @Override
    public Optional<Long> idOf(Long personId) {
        return graveRepository.findByPersonId(personId).map(Grave::getId);
    }

    /**
     * Reports whether a grave belongs to someone still treated as living, failing closed for an unknown grave.
     *
     * @param graveId the grave id
     * @return true when the owner lives, and true as well when there is no such grave
     */
    @Override
    public boolean involvesLiving(Long graveId) {
        // A grave is not automatically the dead's: a sinh phần is built for someone still alive (§8 F9).
        return personIdOf(graveId).map(personService::isLiving).orElse(true);
    }

    /**
     * Counts the graves recorded at one place.
     *
     * @param placeId the place id
     * @return how many graves name it
     */
    @Override
    public long countByPlace(Long placeId) {
        return graveRepository.countByPlaceId(placeId);
    }

    /**
     * Finds which of a batch of people have a mộ (not a sinh phần) recorded.
     *
     * @param personIds the people to check
     * @return the ids of those who do
     */
    @Override
    public Set<Long> findBuried(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(graveRepository.findPersonIdsWithKind(GraveKind.GRAVE, personIds));
    }

    /**
     * Lists every person with a mộ (not a sinh phần) recorded.
     *
     * @return their ids
     */
    @Override
    public Set<Long> findAllBuried() {
        return new HashSet<>(graveRepository.findAllPersonIdsWithKind(GraveKind.GRAVE));
    }

    /**
     * Describes a grave in one line, for a merge note: its plot, place and coordinates.
     *
     * @param grave the grave
     * @return the description, or a phrase saying it carries none
     */
    private static String describe(GraveResponse grave) {
        List<String> parts = new ArrayList<>();
        if (grave.plot() != null) {
            parts.add(grave.plot());
        }
        if (grave.placeId() != null) {
            parts.add("nơi chốn #" + grave.placeId());
        }
        if (grave.latitude() != null) {
            parts.add(grave.latitude() + ", " + grave.longitude());
        }
        return parts.isEmpty() ? "(không ghi vị trí)" : String.join("; ", parts);
    }
}
