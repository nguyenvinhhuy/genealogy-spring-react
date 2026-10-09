package com.genealogy.person.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.Role;
import com.genealogy.common.util.Blank;
import com.genealogy.common.util.LikePattern;
import com.genealogy.common.util.NameKey;
import com.genealogy.common.util.StaleEdit;
import com.genealogy.common.web.PageRequests;
import com.genealogy.person.domain.Person;
import com.genealogy.person.domain.PersonName;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.dto.request.PersonSearchCriteria;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.dto.response.PersonRedactedResponse;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import com.genealogy.person.dto.response.PersonView;
import com.genealogy.person.mapper.PersonMapper;
import com.genealogy.person.repository.PersonRepository;
import com.genealogy.person.service.PersonService;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link PersonService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonServiceImpl implements PersonService {

    // The name this feature's rows carry in the audit trail.
    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.PERSON;

    private final PersonRepository personRepository;
    private final PersonMapper personMapper;
    // Cross-feature by interface only (CLAUDE.md §4): no other feature's entity is imported here.
    private final BranchService branchService;
    private final AuditService auditService;

    /**
     * Finds persons matching every filter that was given.
     *
     * @param criteria the filters, every one optional
     * @param pageable paging information
     * @return the matching page
     */
    @Override
    public Page<PersonSummaryResponse> search(PersonSearchCriteria criteria, Pageable pageable) {
        Collection<Long> ids = criteria.restrictToIds();
        // An empty restriction means another filter matched nobody, which is not the same as no filter.
        if (ids != null && ids.isEmpty()) {
            return Page.empty(pageable);
        }
        Collection<Long> branchIds = criteria.branchIds();
        if (branchIds != null && branchIds.isEmpty()) {
            return Page.empty(pageable);
        }
        boolean restrict = ids != null;
        boolean byBranch = branchIds != null;
        String query = LikePattern.orNull(criteria.query());

        return personRepository
                .search(
                        query,
                        byBranch,
                        // IN () is not valid SQL, so an unused restriction still needs a value to bind.
                        byBranch ? branchIds : List.of(-1L),
                        criteria.generation(),
                        criteria.living(),
                        restrict,
                        restrict ? ids : List.of(-1L),
                        criteria.alternateNames(),
                        // Order fixed by §5.2 and size capped: this was the one list a size=2000 still reached (#22).
                        PageRequests.capped(pageable))
                .map(personMapper::toSummary);
    }

    /**
     * Returns one person, redacted when the caller may not see a living person's details.
     *
     * @param id person id
     * @param role the calling member's access level
     * @return the full person, or the redacted view
     */
    @Override
    public PersonView getById(Long id, Role role) {
        Person person = require(id);
        // In the service, not a controller or mapper: §3.6 exists because a hidden rule fails silently.
        if (person.isLiving() && !Role.maySeeLivingDetails(role)) {
            return PersonRedactedResponse.of(
                    person.getId(), personMapper.displayName(person), person.getGender(), person.getGeneration());
        }
        return personMapper.toDetail(person);
    }

    /**
     * Reports whether a person is treated as living, for the living-person guard.
     *
     * @param id person id
     * @return true when living, and true as well when the person is unknown, so a bad id cannot leak
     */
    @Override
    public boolean isLiving(Long id) {
        // Fails closed on an unknown id, or a wrong id becomes a way to probe who the clan contains.
        return personRepository.findById(id).map(Person::isLiving).orElse(true);
    }

    /**
     * Returns a person's current field values in the shape an update takes.
     *
     * @param id person id
     * @return the current values, with no change note
     */
    @Override
    public PersonRequest currentState(Long id) {
        return personMapper.toRequest(require(id));
    }

    /**
     * Creates a person together with their names.
     *
     * @param request the person to create
     * @param createdBy id of the member making the change
     * @return the created person
     */
    @Override
    @Transactional
    public PersonDetailResponse create(PersonRequest request, Long createdBy) {
        requireBranchExists(request.branchId());

        Person person = new Person();
        personMapper.applyFields(request, person);
        person.setCreatedBy(createdBy);
        person.getNames().addAll(buildNames(request.names()));

        PersonDetailResponse created = personMapper.toDetail(personRepository.save(person));
        auditService.record(
                ENTITY_TYPE, created.id(), AuditAction.CREATE, null, created, createdBy,
                request.changeNote());
        return created;
    }

    /**
     * Replaces a person's fields and name list, refusing an edit made from an older version.
     *
     * @param id person id
     * @param request the new values
     * @param changedBy id of the member making the change
     * @return the updated person
     */
    @Override
    @Transactional
    public PersonDetailResponse update(Long id, PersonRequest request, Long changedBy) {
        Person person = personRepository.findForEdit(id)
                .orElseThrow(() -> new NotFoundException("Không có người với id " + id));
        StaleEdit.refuseIfStale(request.version(), person.getVersion(), AuditEntityType.PERSON);
        // Names are rows of their own, so a name-only edit would leave Person clean and its version unmoved.
        person.setUpdatedAt(Instant.now());
        requireBranchExists(request.branchId());

        // Snapshot before mutating: the entity is managed, so reading it afterwards gives the new state.
        PersonDetailResponse before = personMapper.toDetail(person);

        personMapper.applyFields(request, person);
        applyNames(person, request.names());
        // Flushed so the response carries the bumped version, or the form's next save is refused as stale.
        personRepository.flush();

        PersonDetailResponse after = personMapper.toDetail(person);
        auditService.record(
                ENTITY_TYPE, id, AuditAction.UPDATE, before, after, changedBy, request.changeNote());
        return after;
    }

    /**
     * Deletes a person.
     *
     * @param id person id
     * @param changedBy id of the member making the change
     * @param changeNote why the person was deleted, or null
     */
    @Override
    @Transactional
    public void delete(Long id, Long changedBy, String changeNote) {
        Person person = require(id);
        PersonDetailResponse before = personMapper.toDetail(person);

        personRepository.delete(person);
        auditService.record(ENTITY_TYPE, id, AuditAction.DELETE, before, null, changedBy, changeNote);
    }

    /**
     * Reports whether a person exists.
     *
     * @param id person id
     * @return true if it exists
     */
    @Override
    public boolean exists(Long id) {
        return personRepository.existsById(id);
    }

    /**
     * Fails with a 400 when a person a request body names does not exist.
     *
     * @param id the person id
     */
    @Override
    public void requireExists(Long id) {
        if (id == null || !personRepository.existsById(id)) {
            throw new BadRequestException("Không có người với id " + id);
        }
    }

    /**
     * Counts the people recorded in one chi, not counting its sub-branches.
     *
     * @param branchId the branch id
     * @return how many people belong to it directly
     */
    @Override
    public long countInBranch(Long branchId) {
        return personRepository.countByBranchId(branchId);
    }

    /**
     * Returns the chi a person is recorded under.
     *
     * @param personId the person id
     * @return the branch id, or null when they have none or the person is unknown
     */
    @Override
    public Long branchIdOf(Long personId) {
        return personRepository.findBranchId(personId).orElse(null);
    }

    /**
     * Sets every person's đời to the given value, clearing it for anyone the map leaves out.
     *
     * @param generationByPersonId the đời of each person the parentage graph places
     */
    @Override
    @Transactional
    public void applyGenerations(Map<Long, Integer> generationByPersonId) {
        // Read light, then load only the people whose đời moves: a recompute usually changes a handful, not the clan.
        List<Long> changed = personRepository.findAllLineage().stream()
                .filter(row -> !Objects.equals(row.getGeneration(), generationByPersonId.get(row.getId())))
                .map(PersonRepository.LineageRow::getId)
                .toList();
        if (!changed.isEmpty()) {
            personRepository.findAllById(changed)
                    .forEach(person -> person.setGeneration(generationByPersonId.get(person.getId())));
        }
    }

    /**
     * Reads every person's recorded sex in one light query.
     *
     * @return each person's recorded sex
     */
    @Override
    public Map<Long, Gender> findAllGenders() {
        Map<Long, Gender> genders = new HashMap<>();
        personRepository.findAllLineage().forEach(row -> genders.put(row.getId(), row.getGender()));
        return genders;
    }

    /**
     * Reads every person's stored living flag in one light query.
     *
     * @return each person's living flag
     */
    @Override
    public Map<Long, Boolean> findAllLiving() {
        Map<Long, Boolean> living = new HashMap<>();
        personRepository.findAllLineage().forEach(row -> living.put(row.getId(), row.getLiving()));
        return living;
    }

    /**
     * Writes the recomputed living flag, which the event feature owns the dates to derive.
     *
     * @param personId the person whose flag changed
     * @param living whether the person is still treated as living
     */
    @Override
    @Transactional
    public void applyLiving(Long personId, boolean living) {
        personRepository.findById(personId).ifPresent(person -> person.setLiving(living));
    }

    /**
     * Loads the node projection for a batch of persons in one query.
     *
     * @param ids the persons to load
     * @return their node projections, in no particular order
     */
    @Override
    public List<PersonNodeResponse> findNodes(Collection<Long> ids) {
        // No empty-list guard: Spring Data's findAllById already answers an empty input without a query.
        return personRepository.findAllById(ids).stream().map(personMapper::toNode).toList();
    }

    /**
     * Loads the node projection of every person in the clan in one query.
     *
     * @return every person's node projection, lowest id first
     */
    @Override
    public List<PersonNodeResponse> findAllNodes() {
        return personRepository.findAllWithNames().stream().map(personMapper::toNode).toList();
    }

    /**
     * Loads every person with every name.
     *
     * @return every person, ordered by id so an export is reproducible
     */
    @Override
    public List<PersonDetailResponse> findAllDetails() {
        return personRepository.findAllWithNames().stream().map(personMapper::toDetail).toList();
    }

    /**
     * Folds one person's names and fields into another and deletes them, for a merge.
     *
     * @param targetId the person to keep
     * @param duplicateId the person to absorb and delete
     * @param actorId id of the member running the merge
     * @param reason why the two are the same person, recorded as the basis in the audit trail
     * @return how many of the duplicate's names were kept as alternates
     */
    @Override
    @Transactional
    public int absorb(Long targetId, Long duplicateId, Long actorId, String reason) {
        Person target = require(targetId);
        Person duplicate = require(duplicateId);
        PersonDetailResponse targetBefore = personMapper.toDetail(target);
        PersonDetailResponse duplicateBefore = personMapper.toDetail(duplicate);

        int namesMoved = absorbNames(target, duplicate);
        absorbFields(target, duplicate);
        // Touched, since the names are rows of their own: the survivor's version must move or an open form stays valid.
        target.setUpdatedAt(Instant.now());

        personRepository.delete(duplicate);
        personRepository.flush();

        auditService.record(
                ENTITY_TYPE, duplicateId, AuditAction.DELETE, duplicateBefore, null, actorId,
                "Gộp vào #" + targetId + ": " + reason);
        auditService.record(
                ENTITY_TYPE, targetId, AuditAction.UPDATE, targetBefore, personMapper.toDetail(target), actorId,
                "Gộp #" + duplicateId + " vào: " + reason);
        return namesMoved;
    }

    /**
     * Copies the duplicate's names onto the survivor, keeping the survivor's own primary name.
     *
     * @param target the person being kept
     * @param duplicate the person being absorbed
     * @return how many names were copied
     */
    private int absorbNames(Person target, Person duplicate) {
        Set<String> existing = target.getNames().stream().map(PersonServiceImpl::nameKey).collect(Collectors.toSet());
        int moved = 0;
        for (PersonName name : duplicate.getNames()) {
            if (!existing.add(nameKey(name))) {
                continue;
            }
            target.getNames().add(personMapper.copyAsAlternate(name));
            moved++;
        }
        return moved;
    }

    /**
     * Fills the survivor's empty fields from the duplicate, never overwriting what the survivor records.
     *
     * @param target the person being kept
     * @param duplicate the person being absorbed
     */
    private static void absorbFields(Person target, Person duplicate) {
        if (target.getGender() == Gender.UNKNOWN && duplicate.getGender() != Gender.UNKNOWN) {
            target.setGender(duplicate.getGender());
        }
        if (target.getBranchId() == null) {
            target.setBranchId(duplicate.getBranchId());
        }
        // Joined, not replaced: a merge cannot tell which of two sentences is worth keeping.
        target.setNotes(Blank.join(target.getNotes(), duplicate.getNotes()));
    }

    /**
     * Builds the key that decides whether two names are the same spelling of the same kind.
     *
     * @param name the name to key
     * @return the kind and parts as one comparable string
     */
    private static String nameKey(PersonName name) {
        return NameKey.of(name.getType(), name.getSurname(), name.getMiddleName(), name.getGivenName());
    }

    /**
     * Loads a person or fails.
     *
     * @param id person id
     * @return the entity
     */
    private Person require(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có người với id " + id));
    }

    /**
     * Fails when a branch id is given but names no branch.
     *
     * @param branchId the branch id, may be null
     */
    private void requireBranchExists(Long branchId) {
        if (branchId != null && !branchService.exists(branchId)) {
            throw new BadRequestException("Không có chi với id " + branchId);
        }
    }

    /**
     * Converts name payloads to entities, guaranteeing exactly one primary.
     *
     * @param requests the inbound names, never empty
     * @return the name entities
     */
    private List<PersonName> buildNames(List<PersonNameRequest> requests) {
        int primaryAt = primaryIndexOf(requests);
        List<PersonName> names = requests.stream().map(personMapper::toNameEntity).toList();
        for (int at = 0; at < names.size(); at++) {
            names.get(at).setPrimary(at == primaryAt);
        }
        return names;
    }

    /**
     * Rewrites a person's names from a request, updating existing rows in place rather than replacing them.
     *
     * @param person the person being edited
     * @param requests the names as they should now stand, never empty
     */
    private void applyNames(Person person, List<PersonNameRequest> requests) {
        // In place, by position: delete-and-insert gave every name a new id on every save, even an untouched one.
        int primaryAt = primaryIndexOf(requests);
        List<PersonName> names = person.getNames();
        // Cleared and flushed first: uq_person_names_one_primary is checked per statement, in whatever order they run.
        names.forEach(name -> name.setPrimary(false));
        personRepository.flush();

        for (int at = 0; at < requests.size(); at++) {
            if (at < names.size()) {
                personMapper.updateNameEntity(requests.get(at), names.get(at));
            } else {
                names.add(personMapper.toNameEntity(requests.get(at)));
            }
            names.get(at).setPrimary(at == primaryAt);
        }
        if (names.size() > requests.size()) {
            names.subList(requests.size(), names.size()).clear();
        }
    }

    /**
     * Finds which requested name is the primary one, refusing a list that flags more than one.
     *
     * @param requests the inbound names, never empty
     * @return the index of the primary name
     */
    private static int primaryIndexOf(List<PersonNameRequest> requests) {
        long primaries = requests.stream().filter(PersonNameRequest::primary).count();
        if (primaries > 1) {
            throw new BadRequestException("Mỗi người chỉ có một tên chính");
        }
        // Data entry must stay cheap (§6.1), so an unflagged list promotes its first name, not fails.
        for (int at = 0; at < requests.size(); at++) {
            if (requests.get(at).primary()) {
                return at;
            }
        }
        return 0;
    }
}
