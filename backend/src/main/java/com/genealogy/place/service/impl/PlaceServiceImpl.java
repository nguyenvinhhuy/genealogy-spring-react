package com.genealogy.place.service.impl;

import com.genealogy.audit.service.AuditService;
import com.genealogy.common.exception.BadRequestException;
import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.AuditAction;
import com.genealogy.common.model.AuditEntityType;
import com.genealogy.common.model.PlaceType;
import com.genealogy.common.util.AdvisoryLock;
import com.genealogy.common.util.LikePattern;
import com.genealogy.common.util.StaleEdit;
import com.genealogy.common.util.TextCut;
import com.genealogy.common.web.PageRequests;
import com.genealogy.place.domain.Place;
import com.genealogy.place.dto.request.PlacePath;
import com.genealogy.place.dto.request.PlaceRequest;
import com.genealogy.place.dto.response.PlaceResponse;
import com.genealogy.place.mapper.PlaceMapper;
import com.genealogy.place.repository.PlaceRepository;
import com.genealogy.place.service.PlaceService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link PlaceService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceServiceImpl implements PlaceService {

    private static final AuditEntityType ENTITY_TYPE = AuditEntityType.PLACE;

    // A four-part path with no FORM is xã, huyện, tỉnh, quốc gia: the one length whose levels are not a guess.
    private static final List<PlaceType> FULL_PATH =
            List.of(PlaceType.WARD, PlaceType.DISTRICT, PlaceType.PROVINCE, PlaceType.COUNTRY);

    private static final String PATH_SEPARATOR = ", ";

    private final PlaceRepository placeRepository;
    private final PlaceMapper placeMapper;
    private final AuditService auditService;
    private final AdvisoryLock advisoryLock;

    /**
     * Lists places in name order, optionally filtered by an accent-insensitive name search.
     *
     * @param query the search text, or null for no filter
     * @param pageable which page; any requested order is replaced by name order
     * @return the matching page, each place carrying its full path
     */
    @Override
    public Page<PlaceResponse> search(String query, Pageable pageable) {
        // A client sort on a native query reaches SQL as a raw column name, and name order is the only useful one.
        Pageable page = PageRequests.capped(pageable);
        String like = LikePattern.orNull(query);
        Page<Place> found = like == null
                ? placeRepository.findAllByOrderByNameAscIdAsc(page)
                : placeRepository.searchByName(like, page);
        Map<Long, String> paths = pathsOf(found.getContent());
        return found.map(place -> placeMapper.toResponse(place, paths.get(place.getId())));
    }

    /**
     * Returns one place.
     *
     * @param id place id
     * @return the place, with its full path
     */
    @Override
    public PlaceResponse getById(Long id) {
        return toResponse(require(id));
    }

    /**
     * Returns a batch of places, each with its full path, in one query.
     *
     * @param ids the places wanted
     * @return the places that exist, in no particular order
     */
    @Override
    public List<PlaceResponse> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Place> places = placeRepository.findAllById(ids);
        Map<Long, String> paths = pathsOf(places);
        return places.stream().map(place -> placeMapper.toResponse(place, paths.get(place.getId()))).toList();
    }

    /**
     * Creates a place and records it in the audit trail.
     *
     * @param request the place to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created place
     */
    @Override
    @Transactional
    public PlaceResponse create(PlaceRequest request, Long actorId) {
        advisoryLock.lock(AdvisoryLock.PLACE_TREE);
        requireValid(request);
        return insert(request, actorId);
    }

    /**
     * Updates a place and records it, refusing a stale form, a cycle, or a parent narrower than the place.
     *
     * @param id place id
     * @param request the new values
     * @param actorId the member making the change
     * @return the updated place
     */
    @Override
    @Transactional
    public PlaceResponse update(Long id, PlaceRequest request, Long actorId) {
        // Two moves checked side by side could each pass and together close a loop, so they queue instead.
        advisoryLock.lock(AdvisoryLock.PLACE_TREE);
        Place place = require(id);
        StaleEdit.refuseIfStale(request.version(), place.getVersion(), AuditEntityType.PLACE);
        requireValid(request);
        if (request.parentId() != null && placeRepository.findIdsWithDescendants(id).contains(request.parentId())) {
            throw new BadRequestException("Không thể đặt một nơi chốn vào bên trong chính nó hoặc nơi chốn con của nó");
        }

        // Widening or narrowing a place's own level must not strand a child that no longer fits beneath it (§8.12 #7).
        for (Place child : placeRepository.findByParentId(id)) {
            if (!child.getType().fitsInside(request.type())) {
                throw new BadRequestException("Không thể đổi cấp của \"" + place.getName() + "\": nơi chốn con \""
                        + child.getName() + "\" phải có cấp nhỏ hơn");
            }
        }
        PlaceResponse before = toResponse(place);
        placeMapper.update(request, place);
        placeRepository.flush();
        PlaceResponse after = toResponse(place);
        // A rename changes what every event there says, so "why does cụ's birthplace read this" needs a row (§8.8 D1).
        auditService.record(ENTITY_TYPE, id, AuditAction.UPDATE, before, after, actorId, request.changeNote());
        return after;
    }

    /**
     * Deletes a place that has no child places and records it in the audit trail.
     *
     * @param id place id
     * @param actorId the member making the change
     * @param changeNote why the place is being deleted, or null
     */
    @Override
    @Transactional
    public void delete(Long id, Long actorId, String changeNote) {
        advisoryLock.lock(AdvisoryLock.PLACE_TREE);
        Place place = require(id);
        long children = placeRepository.countByParentId(id);
        if (children > 0) {
            throw new BadRequestException("Nơi chốn \"" + place.getName() + "\" còn " + children
                    + " nơi chốn con. Hãy chuyển hoặc xoá các nơi chốn con trước.");
        }
        PlaceResponse before = toResponse(place);
        placeRepository.delete(place);
        placeRepository.flush();
        // Recorded after the delete, and the trail outlives its subject (§3.8): this is the place's last state.
        auditService.record(ENTITY_TYPE, id, AuditAction.DELETE, before, null, actorId, changeNote);
    }

    /**
     * Reports whether a place exists.
     *
     * @param id place id
     * @return true if it exists
     */
    @Override
    public boolean exists(Long id) {
        return placeRepository.existsById(id);
    }

    /**
     * Fails with a 400 when a place a request names does not exist.
     *
     * @param id the place id, or null for none
     */
    @Override
    public void requireExists(Long id) {
        if (id != null && !placeRepository.existsById(id)) {
            throw new BadRequestException("Không có nơi chốn với id " + id);
        }
    }

    /**
     * Returns a place and every place beneath it in the hierarchy.
     *
     * @param placeId the place to start from
     * @return that place's id and all of its descendants
     */
    @Override
    public Set<Long> findWithDescendants(Long placeId) {
        // A tỉnh must match events recorded against a xã inside it, or precise records become unfindable.
        return Set.copyOf(placeRepository.findIdsWithDescendants(placeId));
    }

    /**
     * Lists every place, for a feature that renders the whole hierarchy in one pass.
     *
     * @return all places, each carrying its parent id and full path
     */
    @Override
    public List<PlaceResponse> findAll() {
        List<Place> places = placeRepository.findAll();
        Map<Long, Place> byId = places.stream().collect(Collectors.toMap(Place::getId, Function.identity()));
        // Built in memory from the one list, rather than one ancestry query per place.
        return places.stream()
                .map(place -> placeMapper.toResponse(place, pathFrom(place, byId)))
                .toList();
    }

    /**
     * Finds, or creates, the chain of places a GEDCOM PLAC path names.
     *
     * @param path the path's names, its levels when the file gives them, and the most specific place's coordinates
     * @param actorId the member running the import
     * @return the id of the most specific place, or empty when the path names nothing
     */
    @Override
    @Transactional
    public Optional<Long> findOrCreatePath(PlacePath path, Long actorId) {
        // Locked, or two imports naming the same new tỉnh at once would each find nothing and each create it.
        advisoryLock.lock(AdvisoryLock.PLACE_TREE);
        List<Segment> segments = segments(path);
        if (segments.isEmpty()) {
            return Optional.empty();
        }

        Long parentId = null;
        // Walked widest first, so each level can be matched or created under the one that contains it.
        for (int i = segments.size() - 1; i >= 0; i--) {
            Segment segment = segments.get(i);
            Long existing = parentId == null
                    ? widestMatch(segment.name())
                    : firstOf(placeRepository.findIdsByNameUnder(segment.name(), parentId));
            if (existing != null) {
                parentId = existing;
                continue;
            }
            boolean leaf = i == 0;
            boolean located = leaf && path.latitude() != null && path.longitude() != null;
            PlaceRequest request = new PlaceRequest(
                    segment.name(),
                    fittedLevel(segment.level(), parentId),
                    parentId,
                    located ? path.latitude() : null,
                    located ? path.longitude() : null,
                    null,
                    null);
            parentId = insert(request, actorId).id();
        }
        return Optional.of(parentId);
    }

    /**
     * Saves a new place and records its creation, with no validation of its own.
     *
     * @param request the already validated place
     * @param actorId the member making the change
     * @return the created place
     */
    private PlaceResponse insert(PlaceRequest request, Long actorId) {
        PlaceResponse created = toResponse(placeRepository.saveAndFlush(placeMapper.toEntity(request)));
        auditService.record(
                ENTITY_TYPE, created.id(), AuditAction.CREATE, null, created, actorId, request.changeNote());
        return created;
    }

    /**
     * Refuses a request whose parent is missing or narrower than it, or whose coordinates come alone.
     *
     * @param request the inbound payload
     */
    private void requireValid(PlaceRequest request) {
        // A lone coordinate is a slip; the DB rejects it too, but this gives a sentence, not a constraint.
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new BadRequestException("Phải điền cả vĩ độ lẫn kinh độ, hoặc để trống cả hai");
        }
        if (request.parentId() == null) {
            return;
        }
        Place parent = placeRepository.findById(request.parentId())
                .orElseThrow(() -> new BadRequestException("Không có nơi chốn với id " + request.parentId()));
        if (!request.type().fitsInside(parent.getType())) {
            throw new BadRequestException("Không thể đặt một nơi chốn cấp này vào bên trong \"" + parent.getName()
                    + "\": cấp của nơi chốn cha phải rộng hơn");
        }
    }

    /**
     * Loads a place or fails.
     *
     * @param id place id
     * @return the entity
     */
    private Place require(Long id) {
        return placeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không có nơi chốn với id " + id));
    }

    /**
     * Renders one place with its path.
     *
     * @param place the entity
     * @return the response
     */
    private PlaceResponse toResponse(Place place) {
        return placeMapper.toResponse(place, pathsOf(List.of(place)).get(place.getId()));
    }

    /**
     * Reads the full path of each place, most specific first, in one query.
     *
     * @param places the places whose paths are wanted
     * @return each place's path, by id
     */
    private Map<Long, String> pathsOf(Collection<Place> places) {
        if (places.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> names = new LinkedHashMap<>();
        for (PlaceRepository.PathRow row : placeRepository.findPathRows(places.stream().map(Place::getId).toList())) {
            names.computeIfAbsent(row.getStartId(), key -> new ArrayList<>()).add(row.getName());
        }
        Map<Long, String> paths = new HashMap<>();
        names.forEach((id, parts) -> paths.put(id, String.join(PATH_SEPARATOR, parts)));
        return paths;
    }

    /**
     * Renders a place's path from an in-memory index of every place.
     *
     * @param place the place
     * @param byId every place, by id
     * @return the path, most specific first
     */
    private static String pathFrom(Place place, Map<Long, Place> byId) {
        List<String> parts = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Place cursor = place;
        // Bounded by `seen`: a parent cycle is refused on write, and would otherwise spin here for ever.
        while (cursor != null && seen.add(cursor.getId())) {
            parts.add(cursor.getName());
            cursor = cursor.getParentId() == null ? null : byId.get(cursor.getParentId());
        }
        return String.join(PATH_SEPARATOR, parts);
    }

    /**
     * Pairs each non-blank name of a path with its level, most specific first.
     *
     * @param path the path as the file gave it
     * @return one segment per non-blank level
     */
    private static List<Segment> segments(PlacePath path) {
        List<String> names = path.names() == null ? List.of() : path.names();
        // FORM counts levels from the raw path, so it is aligned before blanks are dropped, never after.
        List<PlaceType> given = path.levels() != null && path.levels().size() == names.size() ? path.levels() : null;
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            // Cut to the column here, or one over-long component fails the insert and escapes as a 409 (§8.8 #4).
            String name = names.get(i) == null ? "" : TextCut.toLength(names.get(i).strip(), PlaceRequest.MAX_NAME);
            if (!name.isEmpty()) {
                segments.add(new Segment(name, given == null ? null : given.get(i)));
            }
        }
        boolean positional = given == null && segments.size() == FULL_PATH.size();
        List<Segment> levelled = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            PlaceType level = segment.level() != null
                    ? segment.level()
                    : positional ? FULL_PATH.get(i) : PlaceType.OTHER;
            levelled.add(new Segment(segment.name(), level));
        }
        return levelled;
    }

    /**
     * Finds the place the widest component of a path names: top-level first, then at any level.
     *
     * @param name that component's name
     * @return the place id, or null when the clan holds no place of that name
     */
    private Long widestMatch(String name) {
        // A bare "Hà Nội" in a foreign file means the Hà Nội already under Việt Nam, not a second one at the top.
        Long topLevel = firstOf(placeRepository.findTopLevelIdsByName(name));
        return topLevel != null ? topLevel : firstOf(placeRepository.findIdsByName(name));
    }

    /**
     * Keeps a level that fits under its parent, and falls back to OTHER when it does not.
     *
     * @param level the level the file gave or the path implied
     * @param parentId the parent the new place goes under, or null
     * @return the level to store
     */
    private PlaceType fittedLevel(PlaceType level, Long parentId) {
        if (parentId == null) {
            return level;
        }
        PlaceType parentType = placeRepository.findById(parentId).map(Place::getType).orElse(PlaceType.OTHER);
        // An import must not be refused over a stranger's hierarchy, so a misfit is stored honestly as OTHER.
        return level.fitsInside(parentType) ? level : PlaceType.OTHER;
    }

    /**
     * Returns the first id of a list, the oldest when the list is ordered by id.
     *
     * @param ids the ids
     * @return the first, or null when there is none
     */
    private static Long firstOf(List<Long> ids) {
        return ids.isEmpty() ? null : ids.getFirst();
    }

    /**
     * One level of a path being found or created.
     *
     * @param name the level's name
     * @param level the level's type, or null before it is decided
     */
    private record Segment(String name, PlaceType level) {
    }
}
