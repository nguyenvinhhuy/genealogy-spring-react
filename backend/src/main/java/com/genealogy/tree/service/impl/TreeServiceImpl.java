package com.genealogy.tree.service.impl;

import com.genealogy.common.exception.NotFoundException;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.person.service.PersonService;
import com.genealogy.tree.domain.TreeDirection;
import com.genealogy.tree.dto.response.FounderResponse;
import com.genealogy.tree.dto.response.SubgraphResponse;
import com.genealogy.tree.service.TreeService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link TreeService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreeServiceImpl implements TreeService {

    // Past this, a chart stops being readable long before it stops being renderable (CLAUDE.md §3.5).
    private static final int MAX_DEPTH = 8;

    private static final int MIN_DEPTH = 1;

    // Depth 8 from a thuỷ tổ is the whole clan, which §3.5 forbids; past this many people no new generation is walked.
    static final int MAX_PEOPLE = 1500;

    // Cross-feature by interface only (CLAUDE.md §4): no person, family or event entity is imported here.
    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;

    /**
     * Names the person the tree opens on when none is chosen: the clan's thuỷ tổ.
     *
     * @return the founder, whose id may be null when no union is recorded
     */
    @Override
    public FounderResponse founder() {
        // Only an id: the subgraph that follows applies the §3.6 redaction to whoever this turns out to be.
        return new FounderResponse(familyService.findFounderId().orElse(null));
    }

    /**
     * Walks the graph outward from one person and returns what it reached.
     *
     * @param focusId the person to centre on
     * @param direction which way to expand
     * @param depth how many generations to walk, clamped to a safe maximum
     * @return the subgraph
     */
    @Override
    public SubgraphResponse subgraph(Long focusId, TreeDirection direction, int depth) {
        if (!personService.exists(focusId)) {
            throw new NotFoundException("Không có người với id " + focusId);
        }
        int bounded = Math.clamp(depth, MIN_DEPTH, MAX_DEPTH);

        Map<Long, UnionEdgeResponse> unions = new HashMap<>();
        Set<Long> people = new HashSet<>(Set.of(focusId));

        boolean truncatedBelow = direction != TreeDirection.ANCESTORS
                && walkDown(focusId, bounded, people, unions);
        boolean truncatedAbove = direction != TreeDirection.DESCENDANTS
                && walkUp(focusId, bounded, people, unions);

        return new SubgraphResponse(
                focusId,
                direction,
                bounded,
                truncatedBelow,
                truncatedAbove,
                personService.findNodes(people),
                List.copyOf(unions.values()),
                eventService.findRecordedDead(people));
    }

    /**
     * Expands downward layer by layer, collecting descendants and the people they married.
     *
     * @param focusId the person to start from
     * @param depth how many generations of children to walk
     * @param people every person reached, added to in place
     * @param unions every union reached, added to in place
     * @return true when the last generation reached has children of its own
     */
    private boolean walkDown(Long focusId, int depth, Set<Long> people, Map<Long, UnionEdgeResponse> unions) {
        // A HashSet, not Set.of: the frontier is handed to other features, and Set.of throws on contains(null).
        Set<Long> frontier = new HashSet<>(Set.of(focusId));
        boolean moreBelow = false;
        int lastLayer = depth;
        // One pass past the last generation too, so the deepest people are drawn with their spouses (§3.5 couples).
        for (int layer = 0; layer <= lastLayer && !frontier.isEmpty(); layer++) {
            if (layer < lastLayer && people.size() >= MAX_PEOPLE) {
                lastLayer = layer;
            }
            // One call per layer, not per person: the projection exists so a layer costs two queries (§4).
            List<UnionEdgeResponse> reached = familyService.findUnionsByPartners(frontier);
            Set<Long> next = new HashSet<>();
            for (UnionEdgeResponse union : reached) {
                unions.putIfAbsent(union.familyId(), union);
                addIfPresent(people, union.partner1Id());
                addIfPresent(people, union.partner2Id());
                if (layer == lastLayer) {
                    moreBelow |= !union.children().isEmpty();
                } else {
                    union.children().forEach(child -> next.add(child.childId()));
                }
            }
            people.addAll(next);
            frontier = next;
        }
        return moreBelow;
    }

    /**
     * Expands upward layer by layer, collecting parents but not their other children.
     *
     * @param focusId the person to start from
     * @param depth how many generations of parents to walk
     * @param people every person reached, added to in place
     * @param unions every union reached, added to in place
     * @return true when the last generation reached has recorded parents of its own
     */
    private boolean walkUp(Long focusId, int depth, Set<Long> people, Map<Long, UnionEdgeResponse> unions) {
        Set<Long> frontier = new HashSet<>(Set.of(focusId));
        int lastLayer = depth;
        for (int layer = 0; layer <= lastLayer && !frontier.isEmpty(); layer++) {
            if (layer < lastLayer && people.size() >= MAX_PEOPLE) {
                lastLayer = layer;
            }
            List<UnionEdgeResponse> reached = familyService.findUnionsByChildren(frontier);
            if (layer == lastLayer) {
                // Asked only to learn whether anyone lies beyond: none of this is added to the slice.
                return !reached.isEmpty();
            }
            Set<Long> next = new HashSet<>();
            for (UnionEdgeResponse union : reached) {
                unions.putIfAbsent(union.familyId(), union);
                // An ancestor chart draws parents only, so their other children are left out of the slice.
                addIfPresent(next, union.partner1Id());
                addIfPresent(next, union.partner2Id());
            }
            people.addAll(next);
            frontier = next;
        }
        return false;
    }

    /**
     * Adds an id to a set when it is not null.
     *
     * @param target the set to add to
     * @param personId the id, may be null
     */
    private static void addIfPresent(Set<Long> target, Long personId) {
        if (personId != null) {
            target.add(personId);
        }
    }
}
