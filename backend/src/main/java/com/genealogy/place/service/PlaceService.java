package com.genealogy.place.service;

import com.genealogy.place.dto.request.PlacePath;
import com.genealogy.place.dto.request.PlaceRequest;
import com.genealogy.place.dto.response.PlaceResponse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Place operations. */
public interface PlaceService {

    /**
     * Lists places in name order, optionally filtered by an accent-insensitive name search.
     *
     * @param query the search text, or null for no filter
     * @param pageable which page; any requested order is replaced by name order
     * @return the matching page, each place carrying its full path
     */
    Page<PlaceResponse> search(String query, Pageable pageable);

    /**
     * Returns one place.
     *
     * @param id place id
     * @return the place, with its full path
     */
    PlaceResponse getById(Long id);

    /**
     * Returns a batch of places, each with its full path, in one query.
     *
     * @param ids the places wanted
     * @return the places that exist, in no particular order
     */
    List<PlaceResponse> findByIds(Collection<Long> ids);

    /**
     * Creates a place and records it in the audit trail.
     *
     * @param request the place to create
     * @param actorId the member making the change, may be null for a system change
     * @return the created place
     */
    PlaceResponse create(PlaceRequest request, Long actorId);

    /**
     * Updates a place and records it, refusing a stale form, a cycle, or a parent narrower than the place.
     *
     * @param id place id
     * @param request the new values
     * @param actorId the member making the change
     * @return the updated place
     */
    PlaceResponse update(Long id, PlaceRequest request, Long actorId);

    /**
     * Deletes a place that has no child places and records it in the audit trail.
     *
     * @param id place id
     * @param actorId the member making the change
     * @param changeNote why the place is being deleted, or null
     */
    // Events and graves are checked by the caller: `event` and `grave` depend on `place`, so it cannot ask them.
    void delete(Long id, Long actorId, String changeNote);

    /**
     * Reports whether a place exists.
     *
     * @param id place id
     * @return true if it exists
     */
    boolean exists(Long id);

    /**
     * Fails with a 400 when a place a request names does not exist.
     *
     * @param id the place id, or null for none
     */
    // The one "no such place" check, so event, grave and place itself refuse a bad id in the same words.
    void requireExists(Long id);

    /**
     * Returns a place and every place beneath it in the hierarchy (§3.7).
     *
     * @param placeId the place to start from
     * @return that place's id and all of its descendants
     */
    Set<Long> findWithDescendants(Long placeId);

    /**
     * Lists every place, for a feature that renders the whole hierarchy in one pass.
     *
     * @return all places, each carrying its parent id and full path
     */
    List<PlaceResponse> findAll();

    /**
     * Finds, or creates, the chain of places a GEDCOM PLAC path names.
     *
     * @param path the path's names, its levels when the file gives them, and the most specific place's coordinates
     * @param actorId the member running the import
     * @return the id of the most specific place, or empty when the path names nothing
     */
    Optional<Long> findOrCreatePath(PlacePath path, Long actorId);
}
