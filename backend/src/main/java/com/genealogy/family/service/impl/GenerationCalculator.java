package com.genealogy.family.service.impl;

import com.genealogy.common.model.Gender;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Numbers every person's đời from the clan's parentage graph (CLAUDE.md §3.4). */
final class GenerationCalculator {

    private static final int FOUNDER_GENERATION = 1;

    private GenerationCalculator() {
    }

    /**
     * One child of a union, and which of its partners the child is born to.
     *
     * @param id the child
     * @param bornToPartner1 whether the link to the first partner is a birth link
     * @param bornToPartner2 whether the link to the second partner is a birth link
     */
    record Child(Long id, boolean bornToPartner1, boolean bornToPartner2) {
    }

    /**
     * One union's partners and the children linked into it.
     *
     * @param partner1Id first partner, or null
     * @param partner2Id second partner, or null
     * @param children the children, each with the kind of link to each partner
     */
    record Union(Long partner1Id, Long partner2Id, List<Child> children) {

        /**
         * Builds a union whose every child is born to every partner it has.
         *
         * @param partner1Id first partner, or null
         * @param partner2Id second partner, or null
         * @param childIds the children
         * @return the union
         */
        static Union ofBirthChildren(Long partner1Id, Long partner2Id, List<Long> childIds) {
            return new Union(
                    partner1Id, partner2Id, childIds.stream().map(id -> new Child(id, true, true)).toList());
        }

        /**
         * Lists every child, whatever kind of link ties them to the union.
         *
         * @return the children's ids
         */
        List<Long> allChildIds() {
            return children.stream().map(Child::id).toList();
        }
    }

    /**
     * Computes the đời of everyone who appears in a union.
     *
     * @param unions every union in the clan
     * @param genders each person's recorded sex, used only to find the founder of each family line
     * @return the đời of each person the graph places; people on an ancestry cycle are absent
     */
    static Map<Long, Integer> compute(List<Union> unions, Map<Long, Gender> genders) {
        Graph graph = Graph.of(unions);
        Map<Long, Integer> generations = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (Long personId : graph.everyone()) {
            if (seen.add(personId)) {
                Set<Long> component = graph.componentOf(personId);
                seen.addAll(component);
                number(graph, component, genders, generations);
            }
        }
        return generations;
    }

    /**
     * Finds the founder of the largest connected family, the thuỷ tổ the clan's tree starts from.
     *
     * @param unions every union in the clan
     * @param genders each person's recorded sex
     * @return the founder, or empty when nobody is in a union or every family is a cycle
     */
    static Optional<Long> founderOfLargestFamily(List<Union> unions, Map<Long, Gender> genders) {
        Graph graph = Graph.of(unions);
        Set<Long> largest = Set.of();
        Set<Long> seen = new HashSet<>();
        for (Long personId : graph.everyone()) {
            if (seen.add(personId)) {
                Set<Long> component = graph.componentOf(personId);
                seen.addAll(component);
                // The same founder `compute` counts đời 1 from, so the tree opens where the numbering starts.
                if (component.size() > largest.size() && founderOf(graph, component, genders) != null) {
                    largest = component;
                }
            }
        }
        return largest.isEmpty() ? Optional.empty() : Optional.of(founderOf(graph, largest, genders));
    }

    /**
     * Numbers one connected family, counting from its founder.
     *
     * @param graph the whole graph
     * @param component the people of one connected family
     * @param genders each person's recorded sex
     * @param generations the result, filled in place
     */
    private static void number(
            Graph graph, Set<Long> component, Map<Long, Gender> genders, Map<Long, Integer> generations) {
        Long founder = founderOf(graph, component, genders);
        if (founder == null) {
            return;
        }
        // The clan is the founder and everyone descended from them; everyone else is placed through them.
        Set<Long> clan = graph.descendantsOf(founder);
        Map<Long, Integer> placed = numberClan(graph, clan, founder);
        Set<Long> onCycle = new HashSet<>(clan);
        onCycle.removeAll(placed.keySet());
        placeRelatives(graph, placed, onCycle);
        generations.putAll(placed);
    }

    /**
     * Picks the root of a connected family with the most men in its male line, then the most descendants.
     *
     * @param graph the whole graph
     * @param component the people of one connected family
     * @param genders each person's recorded sex
     * @return the founder, or null when every person has a parent (a cycle with no root)
     */
    private static Long founderOf(Graph graph, Set<Long> component, Map<Long, Gender> genders) {
        // A wife's recorded father also has every clan child as a descendant; only the paternal line tells them apart.
        // A con nuôi or con riêng has no birth parent here but is not a root: they are placed through their parent.
        List<Long> roots = component.stream()
                .filter(personId -> graph.parentsOf(personId).isEmpty() && !graph.isNonBloodChild(personId))
                .toList();
        // Each root's two walks are made once: inside the comparator they ran again on every comparison.
        Map<Long, Integer> men = new HashMap<>();
        Map<Long, Integer> descendants = new HashMap<>();
        for (Long root : roots) {
            men.put(root, graph.paternalLineMen(root, genders));
            descendants.put(root, graph.descendantsOf(root).size());
        }
        return roots.stream()
                .max(Comparator
                        .comparingInt((Long root) -> men.get(root))
                        .thenComparingInt(descendants::get)
                        .thenComparing(Comparator.<Long>reverseOrder()))
                .orElse(null);
    }

    /**
     * Numbers the clan itself: one below the deepest parent who is also of the clan.
     *
     * @param graph the whole graph
     * @param clan the founder and their descendants
     * @param founder the founder
     * @return the đời of every clan member reachable without passing through a cycle
     */
    private static Map<Long, Integer> numberClan(Graph graph, Set<Long> clan, Long founder) {
        Map<Long, Integer> remaining = new HashMap<>();
        for (Long member : clan) {
            remaining.put(member, (int) graph.parentsOf(member).stream().filter(clan::contains).count());
        }
        Map<Long, Integer> placed = new HashMap<>();
        placed.put(founder, FOUNDER_GENERATION);
        Deque<Long> ready = new ArrayDeque<>(List.of(founder));
        while (!ready.isEmpty()) {
            Long parent = ready.removeFirst();
            for (Long child : graph.childrenOf(parent)) {
                if (!clan.contains(child)) {
                    continue;
                }
                // One below the DEEPEST clan parent, so a cousin marriage places the child under the later line.
                placed.merge(child, placed.get(parent) + 1, Math::max);
                if (remaining.merge(child, -1, Integer::sum) == 0) {
                    ready.add(child);
                }
            }
        }
        // A member whose clan parents never all resolved sits on or below a cycle, and gets no đời.
        placed.keySet().retainAll(resolved(remaining, founder));
        return placed;
    }

    /**
     * Lists the clan members whose clan parents were all numbered.
     *
     * @param remaining each member's count of clan parents still unnumbered
     * @param founder the founder, who has none
     * @return the members that can be trusted
     */
    private static Set<Long> resolved(Map<Long, Integer> remaining, Long founder) {
        Set<Long> resolved = new HashSet<>();
        remaining.forEach((member, count) -> {
            if (count == 0 || member.equals(founder)) {
                resolved.add(member);
            }
        });
        return resolved;
    }

    /**
     * Places everyone outside the clan relative to the clan: a spouse at their partner's đời, a parent one above.
     *
     * @param graph the whole graph
     * @param placed the đời assigned so far, extended in place
     * @param onCycle clan members on or below an ancestry cycle, who must stay unnumbered
     */
    private static void placeRelatives(Graph graph, Map<Long, Integer> placed, Set<Long> onCycle) {
        // Breadth first from the clan and first-come, so a person reached two ways cannot flip between answers.
        Deque<Long> frontier = new ArrayDeque<>(placed.keySet().stream().sorted().toList());
        while (!frontier.isEmpty()) {
            Long personId = frontier.removeFirst();
            int generation = placed.get(personId);
            for (Long spouse : graph.spousesOf(personId)) {
                placeIfAbsent(placed, frontier, onCycle, spouse, generation);
            }
            // Never above the founder: a con dâu's father at "đời 0" is a number nobody could read.
            if (generation > FOUNDER_GENERATION) {
                for (Long parent : graph.allParentsOf(personId)) {
                    placeIfAbsent(placed, frontier, onCycle, parent, generation - 1);
                }
            }
            // Adoptive and step children too: placed one below, though they are no part of the clan's own line.
            for (Long child : graph.allChildrenOf(personId)) {
                placeIfAbsent(placed, frontier, onCycle, child, generation + 1);
            }
        }
    }

    /**
     * Assigns a đời to a person not yet placed, and queues them to place their own relatives.
     *
     * @param placed the đời assigned so far
     * @param frontier the people still to expand from
     * @param onCycle people who must stay unnumbered
     * @param personId the person to place
     * @param generation the đời they take
     */
    private static void placeIfAbsent(
            Map<Long, Integer> placed, Deque<Long> frontier, Set<Long> onCycle, Long personId, int generation) {
        if (!onCycle.contains(personId) && placed.putIfAbsent(personId, generation) == null) {
            frontier.addLast(personId);
        }
    }

    /**
     * The parentage and marriage edges of the clan, indexed for walking.
     *
     * @param parents each person's birth parents
     * @param children each person's birth children
     * @param spouses each person's partners
     * @param otherParents each person's adoptive, step and foster parents
     * @param otherChildren each person's adoptive, step and foster children
     */
    private record Graph(
            Map<Long, List<Long>> parents,
            Map<Long, List<Long>> children,
            Map<Long, List<Long>> spouses,
            Map<Long, List<Long>> otherParents,
            Map<Long, List<Long>> otherChildren) {

        /**
         * Indexes every union.
         *
         * @param unions every union in the clan
         * @return the graph
         */
        static Graph of(List<Union> unions) {
            Map<Long, List<Long>> parents = new HashMap<>();
            Map<Long, List<Long>> children = new HashMap<>();
            Map<Long, List<Long>> spouses = new HashMap<>();
            Map<Long, List<Long>> otherParents = new HashMap<>();
            Map<Long, List<Long>> otherChildren = new HashMap<>();
            for (Union union : unions) {
                List<Long> partners = new ArrayList<>(2);
                if (union.partner1Id() != null) {
                    partners.add(union.partner1Id());
                }
                if (union.partner2Id() != null) {
                    partners.add(union.partner2Id());
                }
                for (Long partner : partners) {
                    spouses.computeIfAbsent(partner, key -> new ArrayList<>());
                    for (Long other : partners) {
                        if (!other.equals(partner)) {
                            spouses.get(partner).add(other);
                        }
                    }
                }
                for (Child link : union.children()) {
                    Long child = link.id();
                    // Only a birth link is blood (§5.3): a con nuôi or con riêng is placed beside the line, not in it.
                    link(child, union.partner1Id(), link.bornToPartner1(),
                            parents, children, otherParents, otherChildren);
                    link(child, union.partner2Id(), link.bornToPartner2(),
                            parents, children, otherParents, otherChildren);
                    spouses.computeIfAbsent(child, key -> new ArrayList<>());
                }
            }
            return new Graph(parents, children, spouses, otherParents, otherChildren);
        }

        /**
         * Records one child's link to one partner as a blood edge or an adoptive one.
         *
         * @param child the child
         * @param partner the partner, or null when the union has none in that slot
         * @param blood whether the link is a birth link
         * @param parents the birth parents index, extended in place
         * @param children the birth children index, extended in place
         * @param otherParents the non-birth parents index, extended in place
         * @param otherChildren the non-birth children index, extended in place
         */
        private static void link(
                Long child,
                Long partner,
                boolean blood,
                Map<Long, List<Long>> parents,
                Map<Long, List<Long>> children,
                Map<Long, List<Long>> otherParents,
                Map<Long, List<Long>> otherChildren) {
            if (partner == null) {
                return;
            }
            (blood ? parents : otherParents).computeIfAbsent(child, key -> new ArrayList<>()).add(partner);
            (blood ? children : otherChildren).computeIfAbsent(partner, key -> new ArrayList<>()).add(child);
        }

        /**
         * Lists every person appearing in a union, as partner or child.
         *
         * @return the people, lowest id first
         */
        List<Long> everyone() {
            return spouses.keySet().stream().sorted().toList();
        }

        /**
         * Returns a person's recorded parents.
         *
         * @param personId the person
         * @return their parents, empty when none is recorded
         */
        List<Long> parentsOf(Long personId) {
            return parents.getOrDefault(personId, List.of());
        }

        /**
         * Returns a person's recorded children.
         *
         * @param personId the person
         * @return their children, empty when none is recorded
         */
        List<Long> childrenOf(Long personId) {
            return children.getOrDefault(personId, List.of());
        }

        /**
         * Returns a person's parents of every kind: birth, adoptive, step and foster.
         *
         * @param personId the person
         * @return their parents, birth parents first
         */
        List<Long> allParentsOf(Long personId) {
            return Stream.concat(parentsOf(personId).stream(), otherParents.getOrDefault(personId, List.of()).stream())
                    .toList();
        }

        /**
         * Returns a person's children of every kind: birth, adoptive, step and foster.
         *
         * @param personId the person
         * @return their children, birth children first
         */
        List<Long> allChildrenOf(Long personId) {
            return Stream.concat(
                            childrenOf(personId).stream(), otherChildren.getOrDefault(personId, List.of()).stream())
                    .toList();
        }

        /**
         * Reports whether a person is linked to parents only by non-birth links.
         *
         * @param personId the person
         * @return true for a con nuôi or con riêng with no recorded birth parent
         */
        boolean isNonBloodChild(Long personId) {
            return parentsOf(personId).isEmpty() && otherParents.containsKey(personId);
        }

        /**
         * Returns a person's partners in every union.
         *
         * @param personId the person
         * @return their partners, empty when none
         */
        List<Long> spousesOf(Long personId) {
            return spouses.getOrDefault(personId, List.of());
        }

        /**
         * Collects everyone connected to a person by parentage or marriage, in any direction.
         *
         * @param personId the person to start from
         * @return the connected family, including the person
         */
        Set<Long> componentOf(Long personId) {
            Set<Long> seen = new HashSet<>(List.of(personId));
            Deque<Long> pending = new ArrayDeque<>(List.of(personId));
            while (!pending.isEmpty()) {
                Long current = pending.removeFirst();
                for (List<Long> next : List.of(allParentsOf(current), allChildrenOf(current), spousesOf(current))) {
                    for (Long other : next) {
                        if (seen.add(other)) {
                            pending.addLast(other);
                        }
                    }
                }
            }
            return seen;
        }

        /**
         * Collects a person and everyone descended from them.
         *
         * @param personId the person to start from
         * @return the person and their descendants
         */
        Set<Long> descendantsOf(Long personId) {
            Set<Long> seen = new HashSet<>(List.of(personId));
            Deque<Long> pending = new ArrayDeque<>(List.of(personId));
            while (!pending.isEmpty()) {
                for (Long child : childrenOf(pending.removeFirst())) {
                    if (seen.add(child)) {
                        pending.addLast(child);
                    }
                }
            }
            return seen;
        }

        /**
         * Counts the sons, grandsons and so on reached from a root through fathers only — the line a gia phả follows.
         *
         * @param personId the root to count from
         * @param genders each person's recorded sex
         * @return how many men descend from the root in the male line
         */
        int paternalLineMen(Long personId, Map<Long, Gender> genders) {
            // Men only: counting the daughter too tied a wife's recorded father with the founder of a two-đời clan.
            Set<Long> seen = new HashSet<>(List.of(personId));
            Deque<Long> pending = new ArrayDeque<>(List.of(personId));
            int men = 0;
            while (!pending.isEmpty()) {
                Long current = pending.removeFirst();
                if (!isMale(current, genders)) {
                    continue;
                }
                for (Long child : childrenOf(current)) {
                    if (seen.add(child) && isMale(child, genders)) {
                        men++;
                        pending.addLast(child);
                    }
                }
            }
            return men;
        }

        /**
         * Reports whether a person is recorded as male.
         *
         * @param personId the person
         * @param genders each person's recorded sex
         * @return true only for a recorded MALE, never for an unrecorded sex (§5.3)
         */
        private static boolean isMale(Long personId, Map<Long, Gender> genders) {
            return genders.getOrDefault(personId, Gender.UNKNOWN) == Gender.MALE;
        }
    }
}
