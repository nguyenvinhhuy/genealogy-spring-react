package com.genealogy.tree.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.RelationType;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.tree.domain.TreeDirection;
import com.genealogy.tree.dto.response.KinshipResponse;
import com.genealogy.tree.dto.response.SubgraphResponse;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the bounded subgraph walk (CLAUDE.md §3.5). */
@ExtendWith(MockitoExtension.class)
class TreeServiceImplTest {

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    @Mock
    private EventService eventService;

    private TreeServiceImpl treeService;

    private final List<UnionEdgeResponse> allUnions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        treeService = new TreeServiceImpl(personService, familyService, eventService);

        // A four-generation line: 1+2 -> 3, 3+4 -> 5, 5+6 -> 7, where 7 has no union of their own.
        allUnions.add(union(10L, 1L, 2L, 3L));
        allUnions.add(union(11L, 3L, 4L, 5L));
        allUnions.add(union(12L, 5L, 6L, 7L));

        lenient().when(personService.exists(1L)).thenReturn(true);
        lenient().when(personService.exists(7L)).thenReturn(true);
        lenient().when(personService.findNodes(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return ids.stream()
                    .map(id -> new PersonNodeResponse(id, "Người " + id, Gender.UNKNOWN, null, true))
                    .toList();
        });
        lenient().when(eventService.findRecordedDead(anyCollection())).thenReturn(Set.of());

        lenient().when(familyService.findUnionsByPartners(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return allUnions.stream()
                    .filter(u -> ids.contains(u.partner1Id()) || ids.contains(u.partner2Id()))
                    .toList();
        });
        lenient().when(familyService.findUnionsByChildren(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return allUnions.stream()
                    .filter(u -> u.children().stream().anyMatch(c -> ids.contains(c.childId())))
                    .toList();
        });
    }

    /**
     * Builds a union with its birth children.
     *
     * @param familyId the union id
     * @param p1 first partner
     * @param p2 second partner
     * @param childIds the children
     * @return the union projection
     */
    private static UnionEdgeResponse union(Long familyId, Long p1, Long p2, Long... childIds) {
        return new UnionEdgeResponse(
                familyId,
                p1,
                p2,
                FamilyStatus.MARRIED,
                0,
                Arrays.stream(childIds)
                        .map(id -> new ChildEdgeResponse(id, RelationType.BIRTH, RelationType.BIRTH, 1))
                        .toList());
    }

    /**
     * Collects the person ids in a subgraph.
     *
     * @param result the subgraph
     * @return the ids it contains
     */
    private static List<Long> ids(SubgraphResponse result) {
        return result.persons().stream().map(PersonNodeResponse::id).sorted().toList();
    }

    @Test
    @DisplayName("depth 1 downward returns the focus couple and their children, drawn with the children's spouses")
    void depthOneDownward() {
        SubgraphResponse result = treeService.subgraph(1L, TreeDirection.DESCENDANTS, 1);

        assertThat(ids(result)).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("depth 2 downward reaches the grandchildren and their married-in parent")
    void depthTwoDownward() {
        SubgraphResponse result = treeService.subgraph(1L, TreeDirection.DESCENDANTS, 2);

        assertThat(ids(result)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    @DisplayName("truncatedBelow is true only when the last generation drawn has children of its own")
    void truncationBelowIsHonest() {
        assertThat(treeService.subgraph(1L, TreeDirection.DESCENDANTS, 2).truncatedBelow()).isTrue();
        // 7 is drawn at depth 3 and has no union at all, so nothing lies beyond it.
        assertThat(treeService.subgraph(1L, TreeDirection.DESCENDANTS, 3).truncatedBelow()).isFalse();
        assertThat(treeService.subgraph(1L, TreeDirection.DESCENDANTS, 8).truncatedBelow()).isFalse();
    }

    @Test
    @DisplayName("truncatedAbove is true only when the top generation drawn has recorded parents")
    void truncationAboveIsHonest() {
        assertThat(treeService.subgraph(7L, TreeDirection.ANCESTORS, 2).truncatedAbove()).isTrue();
        assertThat(treeService.subgraph(7L, TreeDirection.ANCESTORS, 3).truncatedAbove()).isFalse();
    }

    @Test
    @DisplayName("an hourglass reports each half's truncation separately")
    void hourglassTruncationIsPerHalf() {
        when(personService.exists(5L)).thenReturn(true);

        SubgraphResponse result = treeService.subgraph(5L, TreeDirection.HOURGLASS, 1);

        assertThat(result.truncatedBelow()).isFalse();
        assertThat(result.truncatedAbove()).isTrue();
    }

    @Test
    @DisplayName("ancestors walk upward and leave out the ancestors' other children")
    void ancestorsWalkUpward() {
        allUnions.set(1, union(11L, 3L, 4L, 5L, 8L));

        SubgraphResponse result = treeService.subgraph(7L, TreeDirection.ANCESTORS, 2);

        assertThat(ids(result)).containsExactly(3L, 4L, 5L, 6L, 7L);
        // Nothing below the focus person is pulled in when only ancestors were asked for.
        verify(familyService, atMostOnce()).findUnionsByPartners(anyCollection());
    }

    @Test
    @DisplayName("the people recorded as dead are named, so the chart need not guess from the living flag")
    void recordedDeadAreReturned() {
        when(eventService.findRecordedDead(anyCollection())).thenReturn(Set.of(1L));

        assertThat(treeService.subgraph(1L, TreeDirection.DESCENDANTS, 1).recordedDeadIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("depth is clamped so a runaway request cannot walk the whole clan")
    void depthIsClamped() {
        assertThat(treeService.subgraph(1L, TreeDirection.DESCENDANTS, 9999).depth()).isEqualTo(8);
        assertThat(treeService.subgraph(1L, TreeDirection.DESCENDANTS, 0).depth()).isEqualTo(1);
    }

    @Test
    @DisplayName("each layer costs one projection call, not one per person")
    void oneCallPerLayer() {
        treeService.subgraph(1L, TreeDirection.DESCENDANTS, 2);

        // Two layers plus the closing pass for the deepest spouses - never one call per person in the frontier.
        verify(familyService, times(3)).findUnionsByPartners(anyCollection());
    }

    @Test
    @DisplayName("a generation that takes the slice past the people cap is the last one walked, and says so")
    void peopleCapStopsTheWalk() {
        allUnions.clear();
        List<Long> children = new ArrayList<>();
        for (long child = 100; child < 100 + TreeServiceImpl.MAX_PEOPLE; child++) {
            children.add(child);
            allUnions.add(union(10_000 + child, child, null, 50_000 + child));
        }
        allUnions.add(union(10L, 1L, 2L, children.toArray(Long[]::new)));

        SubgraphResponse result = treeService.subgraph(1L, TreeDirection.DESCENDANTS, 8);

        assertThat(result.persons()).hasSize(2 + TreeServiceImpl.MAX_PEOPLE);
        assertThat(result.truncatedBelow()).isTrue();
    }

    @Test
    @DisplayName("what a MEMBER receives about anyone, living included, carries no dates, notes or chi (§3.6)")
    void treeShapesCarryNoLivingDetails() {
        // The tree is served to every role unredacted, so its records must never grow a field that needs redacting.
        assertThat(PersonNodeResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("id", "displayName", "gender", "generation", "living");
        assertThat(UnionEdgeResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("familyId", "partner1Id", "partner2Id", "status", "orderIndex", "children");
        assertThat(ChildEdgeResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("childId", "relationToP1", "relationToP2", "birthOrder");
        assertThat(KinshipResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("fromId", "toId", "term", "stepsUp", "stepsDown", "commonAncestorId", "side");
        assertThat(SubgraphResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly(
                        "focusId", "direction", "depth", "truncatedBelow", "truncatedAbove", "persons", "unions",
                        "recordedDeadIds");
    }

    @Test
    @DisplayName("an unknown focus person is a not-found, not an empty chart")
    void unknownFocusPerson() {
        when(personService.exists(404L)).thenReturn(false);

        assertThatThrownBy(() -> treeService.subgraph(404L, TreeDirection.DESCENDANTS, 3))
                .isInstanceOf(NotFoundException.class);
    }
}
