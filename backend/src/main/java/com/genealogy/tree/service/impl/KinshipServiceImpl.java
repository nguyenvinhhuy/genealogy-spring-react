package com.genealogy.tree.service.impl;

import com.genealogy.common.exception.NotFoundException;
import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.RelationType;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.tree.domain.KinshipSide;
import com.genealogy.tree.dto.response.KinshipResponse;
import com.genealogy.tree.service.KinshipService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link KinshipService}, following the Northern convention and blood paths only (CLAUDE.md §5.3). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KinshipServiceImpl implements KinshipService {

    // Beyond this many generations either way the words stop being ones anyone uses.
    private static final int MAX_STEPS = 6;
    private static final String DISTANT = "họ hàng xa";

    private static final List<String> DESCENDING = List.of("con", "cháu", "chắt", "chút", "chít");
    private static final List<String> ASCENDING_MALE = List.of("cha", "ông", "cụ ông", "kỵ", "tổ");
    private static final List<String> ASCENDING_FEMALE = List.of("mẹ", "bà", "cụ bà", "kỵ", "tổ");
    // Old records often lack a sex (§3.1), and an honest pair of words beats a confident wrong one (§5.3).
    private static final List<String> ASCENDING_UNKNOWN = List.of("cha/mẹ", "ông/bà", "cụ", "kỵ", "tổ");

    private final PersonService personService;
    private final FamilyService familyService;

    /**
     * Names the relationship from one person's point of view.
     *
     * @param fromId the speaker
     * @param toId the person being named
     * @return the relationship
     */
    @Override
    public KinshipResponse describe(Long fromId, Long toId) {
        // The whole clan's unions are two queries; walking them layer by layer cost up to 124 (§4).
        BloodGraph graph = BloodGraph.of(familyService.findAllUnions());

        Set<Long> wanted = new HashSet<>(graph.parentsOf(fromId));
        wanted.add(fromId);
        wanted.add(toId);
        Map<Long, Gender> genders = personService.findNodes(wanted).stream()
                .collect(Collectors.toMap(PersonNodeResponse::id, PersonNodeResponse::gender));
        if (!genders.containsKey(fromId)) {
            throw new NotFoundException("Không có người với id " + fromId);
        }
        if (!genders.containsKey(toId)) {
            throw new NotFoundException("Không có người với id " + toId);
        }
        if (fromId.equals(toId)) {
            return new KinshipResponse(fromId, toId, "chính mình", 0, 0, fromId, null);
        }

        Gender targetGender = genders.get(toId);
        Optional<String> spouse = spouseTerm(graph, fromId, toId, targetGender);
        if (spouse.isPresent()) {
            return new KinshipResponse(fromId, toId, spouse.get(), 0, 0, null, null);
        }

        Map<Long, Integer> fromAncestors = graph.ancestorDistances(fromId);
        Map<Long, Integer> toAncestors = graph.ancestorDistances(toId);
        Optional<Long> lca = fromAncestors.keySet().stream()
                .filter(toAncestors::containsKey)
                // The *lowest* common ancestor is the one closest to both; ties break toward the speaker.
                .min(Comparator
                        .comparingInt((Long id) -> fromAncestors.get(id) + toAncestors.get(id))
                        .thenComparingInt(fromAncestors::get));
        if (lca.isEmpty()) {
            return new KinshipResponse(fromId, toId, null, 0, 0, null, null);
        }

        Long ancestorId = lca.get();
        int up = fromAncestors.get(ancestorId);
        int down = toAncestors.get(ancestorId);
        KinshipSide side = up >= 2 ? sideOf(graph, fromId, ancestorId, up, genders) : null;
        Boolean targetLineElder = up >= 1 && down >= 1
                ? graph.comparesBranches(ancestorId, toAncestors, down, fromAncestors, up)
                : null;

        return new KinshipResponse(
                fromId, toId, term(up, down, targetGender, side, targetLineElder), up, down, ancestorId, side);
    }

    /**
     * Chooses the Vietnamese term for a resolved path.
     *
     * @param up generations from speaker to the common ancestor
     * @param down generations from that ancestor to the target
     * @param gender the target's recorded sex
     * @param side the parent the path runs through, or null when unknown
     * @param targetLineElder whether the target's branch descends from the elder child of the ancestor, or null
     * @return the term
     */
    private static String term(int up, int down, Gender gender, KinshipSide side, Boolean targetLineElder) {
        if (up == 0) {
            return descendingTerm(down);
        }
        if (down == 0) {
            return ascendingTerm(up, gender, "");
        }
        if (up > MAX_STEPS || down > MAX_STEPS) {
            return DISTANT;
        }
        if (up == 1) {
            return down == 1 ? siblingTerm(gender, targetLineElder) : collateralDescendantTerm(down - 1, "");
        }
        if (down == 1) {
            // A grandparent's sibling is simply ông or bà; only the parent's generation splits into bác and chú.
            return up == 2 ? parentSiblingTerm(gender, side, targetLineElder, "") : ascendingTerm(up - 1, gender, "");
        }

        int difference = down - up;
        if (difference == 0) {
            return cousinTerm(up, gender, targetLineElder);
        }
        if (difference == -1) {
            return parentSiblingTerm(gender, side, targetLineElder, " họ");
        }
        return difference > 0
                ? collateralDescendantTerm(difference, " họ")
                : ascendingTerm(-difference, gender, " họ");
    }

    /**
     * Returns the term for a direct descendant that many generations down.
     *
     * @param steps generations down
     * @return the term
     */
    private static String descendingTerm(int steps) {
        return steps <= DESCENDING.size() ? DESCENDING.get(steps - 1) : DISTANT;
    }

    /**
     * Returns the term for a sibling's or cousin's descendant that many generations below the speaker.
     *
     * @param steps generations below the speaker
     * @param suffix " họ" for a cousin's line, empty for a sibling's
     * @return the term
     */
    private static String collateralDescendantTerm(int steps, String suffix) {
        // A nephew and a grand-nephew are both cháu; only from the third generation down does the word change.
        int index = Math.max(steps, 2) - 1;
        return index < DESCENDING.size() ? DESCENDING.get(index) + suffix : DISTANT;
    }

    /**
     * Returns the term for an ancestor, or an ancestor's sibling, that many generations up.
     *
     * @param steps generations up
     * @param gender the person's recorded sex
     * @param suffix " họ" for a collateral line, empty otherwise
     * @return the term
     */
    private static String ascendingTerm(int steps, Gender gender, String suffix) {
        if (steps > ASCENDING_MALE.size()) {
            return DISTANT;
        }
        List<String> words = switch (gender) {
            case MALE -> ASCENDING_MALE;
            case FEMALE -> ASCENDING_FEMALE;
            case UNKNOWN -> ASCENDING_UNKNOWN;
        };
        return words.get(steps - 1) + suffix;
    }

    /**
     * Names a sibling, which needs birth order to tell anh/chị from em.
     *
     * @param gender the sibling's recorded sex
     * @param elder whether the sibling is the elder, or null when birth order does not tell
     * @return the term
     */
    private static String siblingTerm(Gender gender, Boolean elder) {
        if (elder == null) {
            return "anh/chị/em ruột";
        }
        return elder ? elderWord(gender) : "em";
    }

    /**
     * Names a cousin of the speaker's own generation, by the seniority of the branch they descend from.
     *
     * @param up generations from the speaker to the common ancestor
     * @param gender the cousin's recorded sex
     * @param elder whether the cousin's branch is the elder one, or null when birth order does not tell
     * @return the term
     */
    private static String cousinTerm(int up, Gender gender, Boolean elder) {
        // Northern usage: con nhà bác is anh or chị to con nhà chú whatever their ages, so the branch decides.
        String word = elder == null ? "anh/chị/em" : elder ? elderWord(gender) : "em";
        return up == 2 ? word + " họ" : word + " họ đời " + (up - 1);
    }

    /**
     * Returns the word for an elder of the speaker's own generation.
     *
     * @param gender their recorded sex
     * @return anh, chị, or both when the sex is unrecorded
     */
    private static String elderWord(Gender gender) {
        return switch (gender) {
            case MALE -> "anh";
            case FEMALE -> "chị";
            case UNKNOWN -> "anh/chị";
        };
    }

    /**
     * Names someone of the speaker's parents' generation: bác, chú, cô, cậu or dì.
     *
     * @param gender the relative's recorded sex
     * @param side the parent the path runs through, or null when unknown
     * @param elder whether the relative's branch is the elder one, or null when birth order does not tell
     * @param suffix " họ" for a parent's cousin, empty for a parent's sibling
     * @return the term
     */
    private static String parentSiblingTerm(Gender gender, KinshipSide side, Boolean elder, String suffix) {
        if (elder == null) {
            // Saying "bác hoặc chú" is honest; picking one at random is how a family gets offended.
            String options = side == KinshipSide.PATERNAL ? "bác/chú/cô"
                    : side == KinshipSide.MATERNAL ? "bác/cậu/dì"
                    : "bác/chú/cô/cậu/dì";
            return options + suffix;
        }
        // Bác is the elder on either side, so it needs neither the side nor the sex.
        return (elder ? "bác" : youngerParentSiblingTerm(gender, side)) + suffix;
    }

    /**
     * Names a younger sibling of the speaker's parent.
     *
     * @param gender the relative's recorded sex
     * @param side the parent the path runs through, or null when unknown
     * @return chú, cô, cậu or dì, or the honest alternatives when the sex or the side is unknown
     */
    private static String youngerParentSiblingTerm(Gender gender, KinshipSide side) {
        String male = side == KinshipSide.PATERNAL ? "chú" : side == KinshipSide.MATERNAL ? "cậu" : "chú/cậu";
        String female = side == KinshipSide.PATERNAL ? "cô" : side == KinshipSide.MATERNAL ? "dì" : "cô/dì";
        return switch (gender) {
            case MALE -> male;
            case FEMALE -> female;
            case UNKNOWN -> male + "/" + female;
        };
    }

    /**
     * Works out which of the speaker's parents a path to an ancestor runs through.
     *
     * @param graph the blood graph
     * @param fromId the speaker
     * @param ancestorId the common ancestor
     * @param up generations from the speaker to that ancestor
     * @param genders the recorded sex of the speaker's parents
     * @return the side, or null when the parent's sex is unrecorded or both parents lead there equally
     */
    private static KinshipSide sideOf(
            BloodGraph graph, Long fromId, Long ancestorId, int up, Map<Long, Gender> genders) {
        Set<Gender> through = graph.parentsOf(fromId).stream()
                .filter(parentId -> Objects.equals(graph.ancestorDistances(parentId).get(ancestorId), up - 1))
                .map(parentId -> genders.getOrDefault(parentId, Gender.UNKNOWN))
                .collect(Collectors.toSet());
        // Parents who are themselves cousins both reach the ancestor, and then neither side is the answer.
        if (through.size() != 1) {
            return null;
        }
        return switch (through.iterator().next()) {
            case MALE -> KinshipSide.PATERNAL;
            case FEMALE -> KinshipSide.MATERNAL;
            case UNKNOWN -> null;
        };
    }

    /**
     * Names a direct spouse, if the two are partners in a union.
     *
     * @param graph the blood graph, which also indexes unions by partner
     * @param fromId the speaker
     * @param toId the other person
     * @param gender the other person's recorded sex
     * @return the term, or empty when they are not partners
     */
    private static Optional<String> spouseTerm(BloodGraph graph, Long fromId, Long toId, Gender gender) {
        List<UnionEdgeResponse> shared = graph.unionsOf(fromId).stream()
                .filter(union -> toId.equals(union.partner1Id()) || toId.equals(union.partner2Id()))
                .toList();
        if (shared.isEmpty()) {
            return Optional.empty();
        }
        String word = switch (gender) {
            case FEMALE -> "vợ";
            case MALE -> "chồng";
            case UNKNOWN -> "vợ/chồng";
        };
        // A couple who divorced and remarried have both unions; the current one is what they are now.
        boolean current = shared.stream().anyMatch(union -> union.status() != FamilyStatus.DIVORCED);
        return Optional.of(current ? word : word + " cũ");
    }

    /**
     * The clan's blood parentage, indexed for walking.
     *
     * @param parents each person's birth parents
     * @param unionsByPartner each person's unions
     */
    private record BloodGraph(Map<Long, Set<Long>> parents, Map<Long, List<UnionEdgeResponse>> unionsByPartner) {

        /**
         * Indexes every union, keeping only the birth links as parentage.
         *
         * @param unions every union in the clan
         * @return the graph
         */
        static BloodGraph of(List<UnionEdgeResponse> unions) {
            Map<Long, Set<Long>> parents = new HashMap<>();
            Map<Long, List<UnionEdgeResponse>> byPartner = new HashMap<>();
            for (UnionEdgeResponse union : unions) {
                for (Long partnerId : partnersOf(union)) {
                    byPartner.computeIfAbsent(partnerId, key -> new ArrayList<>()).add(union);
                }
                for (ChildEdgeResponse child : union.children()) {
                    // A step, adoptive or foster link is a real relationship but not a blood path (§5.3).
                    if (union.partner1Id() != null && child.relationToP1() == RelationType.BIRTH) {
                        parents.computeIfAbsent(child.childId(), key -> new LinkedHashSet<>()).add(union.partner1Id());
                    }
                    if (union.partner2Id() != null && child.relationToP2() == RelationType.BIRTH) {
                        parents.computeIfAbsent(child.childId(), key -> new LinkedHashSet<>()).add(union.partner2Id());
                    }
                }
            }
            return new BloodGraph(parents, byPartner);
        }

        /**
         * Lists a union's recorded partners.
         *
         * @param union the union
         * @return its partner ids, without the unrecorded ones
         */
        private static List<Long> partnersOf(UnionEdgeResponse union) {
            List<Long> partners = new ArrayList<>(2);
            if (union.partner1Id() != null) {
                partners.add(union.partner1Id());
            }
            if (union.partner2Id() != null) {
                partners.add(union.partner2Id());
            }
            return partners;
        }

        /**
         * Returns a person's birth parents.
         *
         * @param personId the person
         * @return their birth parents, empty when none is recorded
         */
        Set<Long> parentsOf(Long personId) {
            return parents.getOrDefault(personId, Set.of());
        }

        /**
         * Returns the unions a person is a partner in.
         *
         * @param personId the person
         * @return their unions, empty when none
         */
        List<UnionEdgeResponse> unionsOf(Long personId) {
            return unionsByPartner.getOrDefault(personId, List.of());
        }

        /**
         * Walks upward from a person, recording how many generations away each blood ancestor is.
         *
         * @param personId the person to start from
         * @return each reachable ancestor and its shortest distance, with the person at 0
         */
        Map<Long, Integer> ancestorDistances(Long personId) {
            Map<Long, Integer> distances = new HashMap<>();
            // Including the person at distance 0 makes a direct ancestor fall out as the LCA, with no special case.
            distances.put(personId, 0);
            Deque<Long> pending = new ArrayDeque<>(List.of(personId));
            while (!pending.isEmpty()) {
                Long current = pending.removeFirst();
                for (Long parentId : parentsOf(current)) {
                    if (!distances.containsKey(parentId)) {
                        distances.put(parentId, distances.get(current) + 1);
                        pending.addLast(parentId);
                    }
                }
            }
            return distances;
        }

        /**
         * Reports whether the target's branch below a common ancestor is the elder of the two branches.
         *
         * @param ancestorId the common ancestor
         * @param targetAncestors the target's ancestor distances
         * @param down generations from the ancestor to the target
         * @param speakerAncestors the speaker's ancestor distances
         * @param up generations from the ancestor to the speaker
         * @return true when the target's branch is elder, false when younger, null when birth order does not tell
         */
        Boolean comparesBranches(
                Long ancestorId, Map<Long, Integer> targetAncestors, int down, Map<Long, Integer> speakerAncestors,
                int up) {
            Set<Long> targetBranches = branchesToward(ancestorId, targetAncestors, down);
            Set<Long> speakerBranches = branchesToward(ancestorId, speakerAncestors, up);
            Boolean answer = null;
            for (Long target : targetBranches) {
                for (Long speaker : speakerBranches) {
                    Boolean elder = comparesElder(ancestorId, target, speaker);
                    // Two routes that disagree, like one that is unrecorded, give no honest answer.
                    if (elder == null || (answer != null && !answer.equals(elder))) {
                        return null;
                    }
                    answer = elder;
                }
            }
            return answer;
        }

        /**
         * Finds the ancestor's children through which a shortest path to one person runs.
         *
         * @param ancestorId the common ancestor
         * @param personAncestors that person's ancestor distances
         * @param distance generations from the ancestor to that person
         * @return the children that start such a path
         */
        private Set<Long> branchesToward(Long ancestorId, Map<Long, Integer> personAncestors, int distance) {
            Set<Long> branches = new HashSet<>();
            for (UnionEdgeResponse union : unionsOf(ancestorId)) {
                for (ChildEdgeResponse child : union.children()) {
                    if (parentsOf(child.childId()).contains(ancestorId)
                            && Objects.equals(personAncestors.get(child.childId()), distance - 1)) {
                        branches.add(child.childId());
                    }
                }
            }
            return branches;
        }

        /**
         * Reports whether one child of a person was born before another.
         *
         * @param parentId the shared parent
         * @param candidateId the child being tested
         * @param referenceId the child to compare against
         * @return true when the candidate is elder, false when younger, null when birth order does not tell
         */
        private Boolean comparesElder(Long parentId, Long candidateId, Long referenceId) {
            for (UnionEdgeResponse union : unionsOf(parentId)) {
                Integer candidate = birthOrderIn(union, candidateId);
                Integer reference = birthOrderIn(union, referenceId);
                if (candidate != null && reference != null) {
                    // Twins, or a slip at entry, share a number; that is no evidence of which is elder.
                    return candidate.equals(reference) ? null : candidate < reference;
                }
            }
            return null;
        }

        /**
         * Reads a child's birth order within one union.
         *
         * @param union the union
         * @param childId the child
         * @return the birth order, or null when the child is absent or unordered
         */
        private static Integer birthOrderIn(UnionEdgeResponse union, Long childId) {
            // findFirst() throws on a null element, so the nullable birth order is mapped on the Optional after.
            return union.children().stream()
                    .filter(child -> child.childId().equals(childId))
                    .findFirst()
                    .map(ChildEdgeResponse::birthOrder)
                    .orElse(null);
        }
    }
}
