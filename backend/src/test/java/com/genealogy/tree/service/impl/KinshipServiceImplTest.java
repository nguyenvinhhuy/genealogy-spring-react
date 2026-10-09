package com.genealogy.tree.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the Vietnamese kinship calculator (CLAUDE.md §4.2). */
@ExtendWith(MockitoExtension.class)
class KinshipServiceImplTest {

    private static final long EGO = 31L;

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    private KinshipServiceImpl kinship;

    private final List<UnionEdgeResponse> unions = new ArrayList<>();
    private final Map<Long, Gender> genders = new HashMap<>();
    private final Set<Long> missing = new HashSet<>();

    @BeforeEach
    void setUp() {
        kinship = new KinshipServiceImpl(personService, familyService);

        // Ông bà nội 1+2 with bác 10, cha 11, chú 12, cô 13; ông bà ngoại 3+4 with mẹ 20, cậu 21, dì 22.
        union(100L, 1L, 2L, child(10L, 1), child(11L, 2), child(12L, 3), child(13L, 4));
        union(101L, 11L, 20L, child(30L, 1), child(31L, 2), child(32L, 3));
        union(102L, 3L, 4L, child(20L, 1), child(21L, 2), child(22L, 3));
        union(103L, 10L, 14L, child(40L, 1));
        union(104L, 31L, 33L, child(50L, 1));
        union(105L, 50L, 51L, child(60L, 1));
        union(106L, 40L, 42L, child(41L, 1));

        for (long id : new long[] {1, 3, 10, 11, 12, 21, 30, 31, 40, 41, 50, 60}) {
            genders.put(id, Gender.MALE);
        }
        for (long id : new long[] {2, 4, 13, 14, 20, 22, 32, 33, 42, 51}) {
            genders.put(id, Gender.FEMALE);
        }

        lenient().when(familyService.findAllUnions()).thenReturn(unions);
        lenient().when(personService.findNodes(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return ids.stream()
                    .filter(id -> !missing.contains(id))
                    .map(id -> new PersonNodeResponse(
                            id, "Người " + id, genders.getOrDefault(id, Gender.UNKNOWN), null, true))
                    .toList();
        });
    }

    /**
     * Registers a union whose children are birth children of both partners.
     *
     * @param id family id
     * @param p1 first partner
     * @param p2 second partner
     * @param children the children
     */
    private void union(long id, Long p1, Long p2, ChildEdgeResponse... children) {
        unions.add(new UnionEdgeResponse(id, p1, p2, FamilyStatus.MARRIED, 0, List.of(children)));
    }

    /**
     * Replaces a registered union with a new version of it.
     *
     * @param replacement the union to keep under that id
     */
    private void replaceUnion(UnionEdgeResponse replacement) {
        unions.removeIf(union -> union.familyId().equals(replacement.familyId()));
        unions.add(replacement);
    }

    /**
     * Builds one birth-child link.
     *
     * @param id the child
     * @param order their birth order, or null when unrecorded
     * @return the link
     */
    private static ChildEdgeResponse child(long id, Integer order) {
        return new ChildEdgeResponse(id, RelationType.BIRTH, RelationType.BIRTH, order);
    }

    /**
     * Asks what someone is to EGO.
     *
     * @param toId the person being named
     * @return the term
     */
    private String termFor(long toId) {
        return kinship.describe(EGO, toId).term();
    }

    @Test
    @DisplayName("lineal ancestors: cha, ông, cụ")
    void linealAncestors() {
        assertThat(termFor(11L)).isEqualTo("cha");
        assertThat(termFor(20L)).isEqualTo("mẹ");
        assertThat(termFor(1L)).isEqualTo("ông");
        assertThat(termFor(2L)).isEqualTo("bà");
    }

    @Test
    @DisplayName("lineal descendants: con, cháu")
    void linealDescendants() {
        assertThat(termFor(50L)).isEqualTo("con");
        assertThat(termFor(60L)).isEqualTo("cháu");
    }

    @Test
    @DisplayName("siblings need birth order to tell anh and chị from em")
    void siblings() {
        assertThat(termFor(30L)).as("elder brother").isEqualTo("anh");
        assertThat(termFor(32L)).as("younger sister").isEqualTo("em");
    }

    @Test
    @DisplayName("twins sharing a birth order are not ranked by accident")
    void equalBirthOrderIsNotAnAnswer() {
        replaceUnion(new UnionEdgeResponse(
                101L, 11L, 20L, FamilyStatus.MARRIED, 0, List.of(child(30L, 2), child(31L, 2), child(32L, 3))));

        assertThat(termFor(30L)).isEqualTo("anh/chị/em ruột");
    }

    @Test
    @DisplayName("father's elder brother is bác, his younger brother is chú, his sister is cô")
    void fathersSiblings() {
        assertThat(termFor(10L)).as("father's elder brother").isEqualTo("bác");
        assertThat(termFor(12L)).as("father's younger brother").isEqualTo("chú");
        assertThat(termFor(13L)).as("father's younger sister").isEqualTo("cô");
    }

    @Test
    @DisplayName("mother's younger brother is cậu and her younger sister is dì")
    void mothersSiblings() {
        assertThat(termFor(21L)).as("mother's younger brother").isEqualTo("cậu");
        assertThat(termFor(22L)).as("mother's younger sister").isEqualTo("dì");
    }

    @Test
    @DisplayName("mother's elder sibling is bác, on the mother's side as on the father's")
    void mothersElderSiblingIsBac() {
        replaceUnion(new UnionEdgeResponse(
                102L, 3L, 4L, FamilyStatus.MARRIED, 0, List.of(child(23L, 1), child(20L, 2), child(21L, 3))));
        genders.put(23L, Gender.FEMALE);

        assertThat(termFor(23L)).isEqualTo("bác");
        assertThat(termFor(21L)).isEqualTo("cậu");
    }

    @Test
    @DisplayName("the side is reported, so cậu is never confused with chú")
    void sideIsReported() {
        assertThat(kinship.describe(EGO, 21L).side()).isEqualTo(KinshipSide.MATERNAL);
        assertThat(kinship.describe(EGO, 12L).side()).isEqualTo(KinshipSide.PATERNAL);
        assertThat(kinship.describe(EGO, 30L).side()).as("a sibling has no side").isNull();
    }

    @Test
    @DisplayName("with the mother's sex unrecorded, neither the side nor her words are guessed")
    void unknownSexIsNotGuessed() {
        genders.remove(20L);

        assertThat(termFor(20L)).isEqualTo("cha/mẹ");
        assertThat(termFor(21L)).isEqualTo("chú/cậu");
        assertThat(kinship.describe(EGO, 21L).side()).isNull();
    }

    @Test
    @DisplayName("a spouse of unrecorded sex is vợ/chồng, not chồng")
    void unknownSexSpouse() {
        genders.remove(33L);

        assertThat(termFor(33L)).isEqualTo("vợ/chồng");
    }

    @Test
    @DisplayName("an elder sibling of unrecorded sex is anh/chị")
    void unknownSexElderSibling() {
        genders.remove(30L);

        assertThat(termFor(30L)).isEqualTo("anh/chị");
    }

    @Test
    @DisplayName("a first cousin through the elder branch is anh họ, whatever their own age")
    void firstCousinTakesTheBranchSeniority() {
        assertThat(termFor(40L)).isEqualTo("anh họ");
        assertThat(kinship.describe(40L, EGO).term()).isEqualTo("em họ");
    }

    @Test
    @DisplayName("a first cousin through branches of unrecorded order is anh/chị/em họ")
    void firstCousinUnordered() {
        replaceUnion(new UnionEdgeResponse(
                100L, 1L, 2L, FamilyStatus.MARRIED, 0, List.of(child(10L, null), child(11L, null))));

        assertThat(termFor(40L)).isEqualTo("anh/chị/em họ");
    }

    @Test
    @DisplayName("a cousin's child is cháu họ, not con họ")
    void cousinsChild() {
        assertThat(termFor(41L)).isEqualTo("cháu họ");
    }

    @Test
    @DisplayName("a parent's cousin is bác, chú or cô họ by branch, not cha họ")
    void parentsCousin() {
        assertThat(kinship.describe(50L, 40L).term()).isEqualTo("bác họ");
    }

    @Test
    @DisplayName("a grandparent's sibling is ông")
    void grandparentsSibling() {
        assertThat(kinship.describe(50L, 12L).term()).isEqualTo("ông");
    }

    @Test
    @DisplayName("a spouse is named without going through the blood graph")
    void spouse() {
        assertThat(termFor(33L)).isEqualTo("vợ");
        assertThat(kinship.describe(EGO, 33L).commonAncestorId()).isNull();
    }

    @Test
    @DisplayName("a divorced spouse is named as a former one")
    void divorcedSpouse() {
        replaceUnion(new UnionEdgeResponse(104L, 31L, 33L, FamilyStatus.DIVORCED, 0, List.of(child(50L, 1))));

        assertThat(termFor(33L)).isEqualTo("vợ cũ");
    }

    @Test
    @DisplayName("asking about oneself says so")
    void self() {
        assertThat(termFor(EGO)).isEqualTo("chính mình");
    }

    @Test
    @DisplayName("the lowest common ancestor is chosen, not merely a common one")
    void lowestCommonAncestor() {
        // EGO and their elder brother share both the father (1 step) and the grandparents (2 steps).
        assertThat(kinship.describe(EGO, 30L).commonAncestorId()).isIn(11L, 20L);
        assertThat(kinship.describe(EGO, 30L).stepsUp()).isEqualTo(1);
    }

    @Test
    @DisplayName("with birth order unrecorded it offers both words rather than guessing")
    void unorderedSiblingsAreNotGuessed() {
        replaceUnion(new UnionEdgeResponse(
                100L, 1L, 2L, FamilyStatus.MARRIED, 0, List.of(child(10L, null), child(11L, null))));

        assertThat(termFor(10L)).isEqualTo("bác/chú/cô");
    }

    @Test
    @DisplayName("with birth order unrecorded on the mother's side it offers bác/cậu/dì")
    void unorderedMaternalSiblingsAreNotGuessed() {
        replaceUnion(new UnionEdgeResponse(
                102L, 3L, 4L, FamilyStatus.MARRIED, 0, List.of(child(20L, null), child(21L, null))));

        assertThat(termFor(21L)).isEqualTo("bác/cậu/dì");
    }

    @Test
    @DisplayName("a stepfather is not a blood relative, and neither are two stepchildren of one union")
    void stepLinksAreNotBlood() {
        genders.put(80L, Gender.MALE);
        genders.put(81L, Gender.FEMALE);
        unions.add(new UnionEdgeResponse(107L, 80L, 81L, FamilyStatus.MARRIED, 0, List.of(
                new ChildEdgeResponse(70L, RelationType.BIRTH, RelationType.STEP, 1),
                new ChildEdgeResponse(71L, RelationType.STEP, RelationType.BIRTH, 2))));

        assertThat(kinship.describe(70L, 80L).term()).isEqualTo("cha");
        assertThat(kinship.describe(70L, 81L).term()).isNull();
        assertThat(kinship.describe(70L, 71L).term()).isNull();
    }

    @Test
    @DisplayName("a common ancestor seven generations up is họ hàng xa, not no relation")
    void distantRelativesAreStillRelated() {
        unions.clear();
        long top = 900L;
        long left = top;
        long right = top;
        for (int generation = 1; generation <= 7; generation++) {
            long nextLeft = 900L + generation;
            long nextRight = 950L + generation;
            if (generation == 1) {
                union(1000L, top, null, child(nextLeft, 1), child(nextRight, 2));
            } else {
                union(1000L + generation, left, null, child(nextLeft, 1));
                union(1100L + generation, right, null, child(nextRight, 1));
            }
            left = nextLeft;
            right = nextRight;
        }

        KinshipResponse answer = kinship.describe(left, right);

        assertThat(answer.term()).isEqualTo("họ hàng xa");
        assertThat(answer.commonAncestorId()).isEqualTo(top);
    }

    @Test
    @DisplayName("two unrelated people get no term at all")
    void noRelation() {
        unions.clear();
        union(101L, 11L, 20L, child(EGO, 1));
        union(200L, 90L, 91L, child(92L, 1));

        assertThat(termFor(92L)).isNull();
    }

    @Test
    @DisplayName("an unknown person is a 404, not an empty answer")
    void unknownPerson() {
        missing.add(999L);

        assertThatThrownBy(() -> kinship.describe(EGO, 999L)).isInstanceOf(NotFoundException.class);
    }
}
