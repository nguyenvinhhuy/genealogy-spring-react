package com.genealogy.family.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.genealogy.common.model.Gender;
import com.genealogy.family.service.impl.GenerationCalculator.Union;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for numbering đời from the parentage graph (CLAUDE.md §3.4, §4.2). */
class GenerationCalculatorTest {

    private final Map<Long, Gender> genders = new HashMap<>();

    /**
     * Records the given people as men.
     *
     * @param ids the people
     */
    private void men(Long... ids) {
        for (Long id : ids) {
            genders.put(id, Gender.MALE);
        }
    }

    /**
     * Records the given people as women.
     *
     * @param ids the people
     */
    private void women(Long... ids) {
        for (Long id : ids) {
            genders.put(id, Gender.FEMALE);
        }
    }

    /**
     * Builds one union.
     *
     * @param partner1 first partner, may be null
     * @param partner2 second partner, may be null
     * @param children the children linked into it
     * @return the union
     */
    private static Union union(Long partner1, Long partner2, Long... children) {
        return Union.ofBirthChildren(partner1, partner2, List.of(children));
    }

    /**
     * Builds one union whose single child is adopted, not born to the partners.
     *
     * @param partner1 first partner, may be null
     * @param partner2 second partner, may be null
     * @param child the adopted child
     * @return the union
     */
    private static Union adopting(Long partner1, Long partner2, Long child) {
        return new Union(partner1, partner2, List.of(new GenerationCalculator.Child(child, false, false)));
    }

    @Test
    @DisplayName("a man married into two unions at different đời gets one answer, and the walk ends")
    void spouseInTwoUnionsAtDifferentGenerations() {
        // The old alignment loop pulled person 10 back and forth between đời 2 and 3 and never returned.
        men(1L, 2L, 3L);
        List<Union> unions = List.of(union(1L, null, 2L), union(2L, null, 3L), union(2L, 10L), union(3L, 10L));

        Map<Long, Integer> generations = assertTimeoutPreemptively(
                Duration.ofSeconds(2), () -> GenerationCalculator.compute(unions, genders));

        assertThat(generations).containsEntry(1L, 1).containsEntry(2L, 2).containsEntry(3L, 3);
        assertThat(generations).containsEntry(10L, 2);
    }

    @Test
    @DisplayName("the founder's wife's recorded father does not displace the founder, even two đời deep")
    void wifeWithRecordedParentsDoesNotBecomeTheFounder() {
        men(1L, 3L, 20L);
        women(2L, 21L);
        List<Union> unions = List.of(union(1L, 2L, 3L), union(20L, 21L, 2L));

        Map<Long, Integer> generations = GenerationCalculator.compute(unions, genders);

        assertThat(generations).containsEntry(1L, 1).containsEntry(2L, 1).containsEntry(3L, 2);
        // Above the founder there is no đời to give, so her parents are left unnumbered rather than "đời 0".
        assertThat(generations).doesNotContainKeys(20L, 21L);
    }

    @Test
    @DisplayName("the tree opens on the founder of the largest family, not on a wife's recorded father")
    void founderOfTheLargestFamily() {
        men(1L, 3L, 20L, 40L);
        women(2L, 21L);
        // The clan of 1: five people with the wife's parents. A separate couple 40 with one child is smaller.
        List<Union> unions = List.of(union(1L, 2L, 3L), union(20L, 21L, 2L), union(40L, null, 41L));

        assertThat(GenerationCalculator.founderOfLargestFamily(unions, genders)).contains(1L);
        assertThat(GenerationCalculator.founderOfLargestFamily(List.of(), genders)).isEmpty();
    }

    @Test
    @DisplayName("a con dâu's child from another marriage sits one below her, and that husband beside her")
    void conDauWithAChildFromAnotherUnion() {
        men(1L, 3L, 30L);
        women(4L);
        List<Union> unions = List.of(union(1L, null, 3L), union(3L, 4L), union(30L, 4L, 31L));

        Map<Long, Integer> generations = GenerationCalculator.compute(unions, genders);

        assertThat(generations).containsEntry(3L, 2).containsEntry(4L, 2);
        assertThat(generations).containsEntry(30L, 2).containsEntry(31L, 3);
    }

    @Test
    @DisplayName("people on an ancestry cycle get no đời, and the walk still ends")
    void ancestryCycleIsLeftUnnumbered() {
        // 1 -> 2 -> 3, and 3 is also recorded as a parent of 2.
        List<Union> unions = List.of(union(1L, null, 2L), union(2L, null, 3L), union(3L, null, 2L));

        assertThatCode(() -> GenerationCalculator.compute(unions, genders)).doesNotThrowAnyException();
        Map<Long, Integer> generations = GenerationCalculator.compute(unions, genders);

        assertThat(generations).containsEntry(1L, 1).doesNotContainKeys(2L, 3L);
    }

    @Test
    @DisplayName("with no sex recorded anywhere, the root with the most descendants is the founder")
    void unknownSexFallsBackToDescendantCount() {
        // Root 50 reaches three people, roots 1 and 2 two each; its higher id rules out the lowest-id tie-break.
        List<Union> unions = List.of(
                union(50L, 2L, 3L), union(50L, 9L, 7L), union(3L, 4L, 5L), union(1L, null, 4L));

        Map<Long, Integer> generations = GenerationCalculator.compute(unions, genders);

        assertThat(generations).containsEntry(50L, 1).containsEntry(3L, 2).containsEntry(5L, 3);
        assertThat(generations).containsEntry(4L, 2).containsEntry(1L, 1);
    }

    @Test
    @DisplayName("a con nuôi is numbered one below the adoptive parent and never becomes the founder")
    void adoptedChildIsPlacedBesideTheLineNotInIt() {
        men(1L, 2L, 3L);
        // 2 is adopted by 1 and has a son of their own: with no birth parent, they must not be taken for a root.
        List<Union> unions = List.of(union(1L, null, 3L), adopting(1L, null, 2L), union(2L, null, 4L));

        Map<Long, Integer> generations = GenerationCalculator.compute(unions, genders);

        assertThat(GenerationCalculator.founderOfLargestFamily(unions, genders)).contains(1L);
        assertThat(generations).containsEntry(1L, 1).containsEntry(3L, 2).containsEntry(2L, 2);
        // The adoptee's own son follows them, so the line below an adopted child is still counted (§8.12 D1).
        assertThat(generations).containsEntry(4L, 3);
    }

    @Test
    @DisplayName("a child born to one partner and step to the other is in the line of the first only")
    void stepToOnePartnerIsStillPlaced() {
        men(1L);
        women(2L);
        // Child 3 is born to man 1 and step to woman 2, who is placed beside her husband, not above her stepchild.
        List<Union> unions = List.of(new Union(1L, 2L, List.of(new GenerationCalculator.Child(3L, true, false))));

        Map<Long, Integer> generations = GenerationCalculator.compute(unions, genders);

        assertThat(GenerationCalculator.founderOfLargestFamily(unions, genders)).contains(1L);
        assertThat(generations).containsEntry(1L, 1).containsEntry(2L, 1).containsEntry(3L, 2);
    }

    @Test
    @DisplayName("two unrelated families are each numbered from their own founder")
    void separateFamiliesAreNumberedSeparately() {
        List<Union> unions = List.of(union(1L, null, 2L), union(10L, null, 11L));

        Map<Long, Integer> generations = GenerationCalculator.compute(unions, genders);

        assertThat(generations).containsEntry(1L, 1).containsEntry(2L, 2);
        assertThat(generations).containsEntry(10L, 1).containsEntry(11L, 2);
    }
}
