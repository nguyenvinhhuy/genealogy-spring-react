package com.genealogy.quality.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.lenient;

import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.RelationType;
import com.genealogy.event.dto.response.EventFactResponse;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.quality.domain.IssueCode;
import com.genealogy.quality.domain.IssueSeverity;
import com.genealogy.quality.dto.response.QualityIssueResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the consistency rules in CLAUDE.md §5.1 (required by §4.2). */
@ExtendWith(MockitoExtension.class)
class QualityServiceImplTest {

    @Mock
    private PersonService personService;

    @Mock
    private FamilyService familyService;

    @Mock
    private EventService eventService;

    private QualityServiceImpl quality;

    private final List<EventFactResponse> facts = new ArrayList<>();
    private final List<UnionEdgeResponse> unions = new ArrayList<>();
    private final Map<Long, Gender> genders = new HashMap<>();
    private final Map<Long, String> names = new HashMap<>();
    private final Map<Long, Integer> generations = new HashMap<>();

    @BeforeEach
    void setUp() {
        quality = new QualityServiceImpl(personService, familyService, eventService);

        lenient().when(eventService.findAllFacts()).thenReturn(facts);
        lenient().when(familyService.findAllUnions()).thenReturn(unions);
        lenient().when(personService.findAllNodes()).thenAnswer(call -> everyone().stream()
                .map(id -> new PersonNodeResponse(
                        id,
                        names.getOrDefault(id, "Người " + id),
                        genders.getOrDefault(id, Gender.UNKNOWN),
                        generations.get(id),
                        true))
                .toList());
    }

    /**
     * Collects every person id the fixture mentions anywhere.
     *
     * @return the ids, lowest first
     */
    private TreeSet<Long> everyone() {
        TreeSet<Long> ids = new TreeSet<>(names.keySet());
        ids.addAll(genders.keySet());
        ids.addAll(generations.keySet());
        facts.stream()
                .filter(fact -> fact.subjectType() == EventSubjectType.PERSON)
                .forEach(fact -> ids.add(fact.subjectId()));
        for (UnionEdgeResponse union : unions) {
            if (union.partner1Id() != null) {
                ids.add(union.partner1Id());
            }
            if (union.partner2Id() != null) {
                ids.add(union.partner2Id());
            }
            union.children().forEach(child -> ids.add(child.childId()));
        }
        return ids;
    }

    /**
     * Records one exactly dated person event.
     *
     * @param personId the subject
     * @param type what happened
     * @param date when
     */
    private void personEvent(long personId, EventType type, LocalDate date) {
        personSpan(personId, type, date, date);
    }

    /**
     * Records one vaguely dated person event as the span it allows.
     *
     * @param personId the subject
     * @param type what happened
     * @param earliest the first possible day, or null
     * @param latest the last possible day, or null
     */
    private void personSpan(long personId, EventType type, LocalDate earliest, LocalDate latest) {
        Integer year = earliest != null ? earliest.getYear() : latest.getYear();
        facts.add(new EventFactResponse(EventSubjectType.PERSON, personId, type, year, earliest, latest));
    }

    /**
     * Records one exactly dated union event.
     *
     * @param familyId the subject
     * @param type what happened
     * @param date when
     */
    private void unionEvent(long familyId, EventType type, LocalDate date) {
        facts.add(new EventFactResponse(EventSubjectType.FAMILY, familyId, type, date.getYear(), date, date));
    }

    /**
     * Registers a union whose children are birth children of both partners.
     *
     * @param familyId the union
     * @param p1 first partner
     * @param p2 second partner
     * @param childIds the children
     */
    private void union(long familyId, Long p1, Long p2, Long... childIds) {
        List<ChildEdgeResponse> children = Arrays.stream(childIds)
                .map(id -> new ChildEdgeResponse(id, RelationType.BIRTH, RelationType.BIRTH, null))
                .toList();
        unions.add(new UnionEdgeResponse(familyId, p1, p2, FamilyStatus.MARRIED, 0, children));
    }

    /**
     * Registers a union with one child whose relation to each partner is given.
     *
     * @param familyId the union
     * @param p1 first partner
     * @param p2 second partner
     * @param childId the child
     * @param toP1 the child's relation to the first partner
     * @param toP2 the child's relation to the second partner
     */
    private void unionWith(long familyId, Long p1, Long p2, long childId, RelationType toP1, RelationType toP2) {
        unions.add(new UnionEdgeResponse(
                familyId, p1, p2, FamilyStatus.MARRIED, 0, List.of(new ChildEdgeResponse(childId, toP1, toP2, null))));
    }

    /**
     * Collects the codes the checker reported.
     *
     * @return the codes
     */
    private List<IssueCode> codes() {
        return quality.checkAll().stream().map(QualityIssueResponse::code).toList();
    }

    /**
     * Collects the duplicate findings the checker reported.
     *
     * @return the POSSIBLE_DUPLICATE findings
     */
    private List<QualityIssueResponse> duplicates() {
        return quality.checkAll().stream().filter(found -> found.code() == IssueCode.POSSIBLE_DUPLICATE).toList();
    }

    @Test
    @DisplayName("dying before being born is flagged")
    void deathBeforeBirth() {
        personEvent(1L, EventType.BIRTH, LocalDate.of(1900, 1, 1));
        personEvent(1L, EventType.DEATH, LocalDate.of(1890, 1, 1));

        assertThat(codes()).contains(IssueCode.DEATH_BEFORE_BIRTH);
    }

    @Test
    @DisplayName("being buried before dying is flagged")
    void burialBeforeDeath() {
        personEvent(1L, EventType.DEATH, LocalDate.of(1950, 6, 1));
        personEvent(1L, EventType.BURIAL, LocalDate.of(1950, 5, 1));

        assertThat(codes()).contains(IssueCode.BURIAL_BEFORE_DEATH);
    }

    @Test
    @DisplayName("a burial recorded only by year is not before a death later that year")
    void yearOnlyBurialIsNotBeforeDeath() {
        personEvent(1L, EventType.DEATH, LocalDate.of(1945, 6, 15));
        personSpan(1L, EventType.BURIAL, LocalDate.of(1945, 1, 1), LocalDate.of(1945, 12, 31));

        assertThat(codes()).doesNotContain(IssueCode.BURIAL_BEFORE_DEATH);
    }

    @Test
    @DisplayName("a birth range that overlaps the death is not called death-before-birth")
    void betweenBirthIsNotBeforeDeath() {
        personSpan(1L, EventType.BIRTH, LocalDate.of(1918, 1, 1), LocalDate.of(1922, 12, 31));
        personSpan(1L, EventType.DEATH, LocalDate.of(1921, 1, 1), LocalDate.of(1921, 12, 31));

        assertThat(codes()).doesNotContain(IssueCode.DEATH_BEFORE_BIRTH);
    }

    @Test
    @DisplayName("a death after a bound with no upper limit is never before the birth")
    void openEndedDeathIsNotBeforeBirth() {
        personEvent(1L, EventType.BIRTH, LocalDate.of(1952, 1, 1));
        personSpan(1L, EventType.DEATH, LocalDate.of(1951, 1, 1), null);

        assertThat(codes()).doesNotContain(IssueCode.DEATH_BEFORE_BIRTH);
    }

    @Test
    @DisplayName("a lifespan over 110 years is flagged")
    void implausibleLifespan() {
        personEvent(1L, EventType.BIRTH, LocalDate.of(1800, 1, 1));
        personEvent(1L, EventType.DEATH, LocalDate.of(1920, 1, 1));

        assertThat(codes()).contains(IssueCode.IMPLAUSIBLE_LIFESPAN);
    }

    @Test
    @DisplayName("a long but possible life is not flagged")
    void longLifeIsAccepted() {
        personEvent(1L, EventType.BIRTH, LocalDate.of(1900, 1, 1));
        personEvent(1L, EventType.DEATH, LocalDate.of(2005, 1, 1));

        assertThat(codes()).doesNotContain(IssueCode.IMPLAUSIBLE_LIFESPAN);
    }

    @Test
    @DisplayName("two births that cannot both be true are flagged, and neither is silently picked")
    void conflictingBirthsAreFlagged() {
        personEvent(1L, EventType.BIRTH, LocalDate.of(1890, 1, 1));
        personEvent(1L, EventType.BIRTH, LocalDate.of(1910, 1, 1));
        personEvent(1L, EventType.DEATH, LocalDate.of(1905, 1, 1));

        // The death fits the first birth, so it must not be called impossible on the strength of the second.
        assertThat(codes()).contains(IssueCode.CONFLICTING_DATES).doesNotContain(IssueCode.DEATH_BEFORE_BIRTH);
    }

    @Test
    @DisplayName("a child born after the mother died is flagged")
    void bornAfterMotherDeath() {
        genders.put(2L, Gender.FEMALE);
        union(10L, 1L, 2L, 3L);
        personEvent(2L, EventType.DEATH, LocalDate.of(1930, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1931, 1, 1));

        assertThat(codes()).contains(IssueCode.BORN_AFTER_MOTHER_DEATH);
    }

    @Test
    @DisplayName("a mother who died in the year of the birth, recorded by year only, is not flagged")
    void motherDeathByYearOnly() {
        genders.put(2L, Gender.FEMALE);
        union(10L, 1L, 2L, 3L);
        personSpan(2L, EventType.DEATH, LocalDate.of(1920, 1, 1), LocalDate.of(1920, 12, 31));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1920, 8, 3));

        assertThat(codes()).doesNotContain(IssueCode.BORN_AFTER_MOTHER_DEATH);
    }

    @Test
    @DisplayName("a posthumous child of the father is accepted, but a year later is not")
    void posthumousChildGrace() {
        genders.put(1L, Gender.MALE);
        union(10L, 1L, null, 3L);
        personEvent(1L, EventType.DEATH, LocalDate.of(1930, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1930, 8, 1));

        assertThat(codes()).doesNotContain(IssueCode.BORN_LONG_AFTER_FATHER_DEATH);

        facts.clear();
        personEvent(1L, EventType.DEATH, LocalDate.of(1930, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1931, 6, 1));

        assertThat(codes()).contains(IssueCode.BORN_LONG_AFTER_FATHER_DEATH);
    }

    @Test
    @DisplayName("a parent of unrecorded sex is never called the father")
    void unknownSexParentIsNotCalledFather() {
        union(10L, 1L, null, 3L);
        personEvent(1L, EventType.DEATH, LocalDate.of(1930, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1931, 6, 1));

        assertThat(codes())
                .contains(IssueCode.BORN_LONG_AFTER_PARENT_DEATH)
                .doesNotContain(IssueCode.BORN_LONG_AFTER_FATHER_DEATH);
    }

    @Test
    @DisplayName("a mother under twelve or over fifty-five is flagged")
    void motherAgeBounds() {
        genders.put(2L, Gender.FEMALE);
        union(10L, 1L, 2L, 3L);
        personEvent(2L, EventType.BIRTH, LocalDate.of(1920, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1930, 1, 1));

        assertThat(codes()).contains(IssueCode.MOTHER_TOO_YOUNG);

        facts.clear();
        personEvent(2L, EventType.BIRTH, LocalDate.of(1900, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1960, 1, 1));

        assertThat(codes()).contains(IssueCode.MOTHER_TOO_OLD);
    }

    @Test
    @DisplayName("a child recorded as born before the parent is called that, not a negative age")
    void bornBeforeParent() {
        genders.put(2L, Gender.FEMALE);
        union(10L, 1L, 2L, 3L);
        personEvent(2L, EventType.BIRTH, LocalDate.of(1950, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1930, 1, 1));

        assertThat(codes()).contains(IssueCode.BORN_BEFORE_PARENT).doesNotContain(IssueCode.MOTHER_TOO_YOUNG);
    }

    @Test
    @DisplayName("a stepchild is not checked against the step-parent's age or the couple's wedding")
    void stepChildIsNotJudgedAsABirthChild() {
        genders.put(2L, Gender.FEMALE);
        unionWith(10L, 1L, 2L, 3L, RelationType.BIRTH, RelationType.STEP);
        personEvent(2L, EventType.BIRTH, LocalDate.of(1900, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1908, 1, 1));
        unionEvent(10L, EventType.MARRIAGE, LocalDate.of(1925, 1, 1));

        assertThat(codes())
                .doesNotContain(IssueCode.MOTHER_TOO_YOUNG)
                .doesNotContain(IssueCode.CHILD_BORN_BEFORE_MARRIAGE);
    }

    @Test
    @DisplayName("an heir adopted after the adoptive father's death is not a posthumous-birth problem")
    void adoptedHeirIsNotJudgedAsBorn() {
        genders.put(1L, Gender.MALE);
        unionWith(10L, 1L, null, 3L, RelationType.ADOPTED, RelationType.BIRTH);
        personEvent(1L, EventType.DEATH, LocalDate.of(1900, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1920, 1, 1));

        assertThat(codes()).doesNotContain(IssueCode.BORN_LONG_AFTER_FATHER_DEATH);
    }

    @Test
    @DisplayName("marrying before thirteen is flagged, naming the spouse and the union")
    void marriedTooYoung() {
        union(10L, 1L, 2L);
        personEvent(1L, EventType.BIRTH, LocalDate.of(1920, 1, 1));
        unionEvent(10L, EventType.MARRIAGE, LocalDate.of(1930, 1, 1));

        QualityIssueResponse issue = quality.checkAll().stream()
                .filter(found -> found.code() == IssueCode.MARRIED_TOO_YOUNG)
                .findFirst()
                .orElseThrow();

        assertThat(issue.relatedPersonId()).isEqualTo(2L);
        assertThat(issue.familyId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("a marriage recorded before the partner's birth is called that, not a negative age")
    void marriedBeforeBirth() {
        union(10L, 1L, 2L);
        personEvent(1L, EventType.BIRTH, LocalDate.of(1940, 1, 1));
        unionEvent(10L, EventType.MARRIAGE, LocalDate.of(1930, 1, 1));

        assertThat(codes()).contains(IssueCode.MARRIED_BEFORE_BIRTH).doesNotContain(IssueCode.MARRIED_TOO_YOUNG);
    }

    @Test
    @DisplayName("a child born well before the marriage is INFO, never a warning")
    void childBeforeMarriageIsInfoOnly() {
        union(10L, 1L, 2L, 3L);
        unionEvent(10L, EventType.MARRIAGE, LocalDate.of(1950, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1945, 1, 1));

        QualityIssueResponse issue = quality.checkAll().stream()
                .filter(found -> found.code() == IssueCode.CHILD_BORN_BEFORE_MARRIAGE)
                .findFirst()
                .orElseThrow();

        assertThat(issue.severity()).isEqualTo(IssueSeverity.INFO);
    }

    @Test
    @DisplayName("a child born just before the wedding is not even mentioned")
    void childBornJustBeforeMarriageIsSilent() {
        union(10L, 1L, 2L, 3L);
        unionEvent(10L, EventType.MARRIAGE, LocalDate.of(1950, 6, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1950, 3, 1));

        assertThat(codes()).doesNotContain(IssueCode.CHILD_BORN_BEFORE_MARRIAGE);
    }

    @Test
    @DisplayName("a person who is their own ancestor is an ERROR, and only the people on the cycle are named")
    void ancestryCycleIsAnError() {
        union(10L, 1L, null, 2L);
        union(11L, 2L, null, 3L);
        union(12L, 3L, null, 1L);
        union(13L, 3L, null, 4L);

        List<QualityIssueResponse> cycle = quality.checkAll().stream()
                .filter(found -> found.code() == IssueCode.ANCESTRY_CYCLE)
                .toList();

        assertThat(cycle).extracting(QualityIssueResponse::personId).containsExactly(1L, 2L, 3L);
        assertThat(cycle).allMatch(found -> found.severity() == IssueSeverity.ERROR);
    }

    @Test
    @DisplayName("cousins marrying close an undirected loop, which is not an ancestry cycle")
    void cousinMarriageIsNotACycle() {
        union(10L, 1L, 2L, 3L, 4L);
        union(11L, 3L, null, 5L);
        union(12L, 4L, null, 6L);
        union(13L, 5L, 6L, 7L);

        assertThat(codes()).doesNotContain(IssueCode.ANCESTRY_CYCLE);
    }

    @Test
    @DisplayName("clean data produces no findings at all")
    void cleanDataIsSilent() {
        genders.put(2L, Gender.FEMALE);
        union(10L, 1L, 2L, 3L);
        personEvent(1L, EventType.BIRTH, LocalDate.of(1900, 1, 1));
        personEvent(2L, EventType.BIRTH, LocalDate.of(1905, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1930, 1, 1));
        unionEvent(10L, EventType.MARRIAGE, LocalDate.of(1928, 1, 1));

        assertThat(quality.checkAll()).isEmpty();
    }

    @Test
    @DisplayName("two people with the same name and birth year are flagged as possible duplicates")
    void duplicateDetection() {
        names.put(1L, "Nguyễn Văn Ánh");
        names.put(2L, "Nguyen Van Anh");
        personEvent(1L, EventType.BIRTH, LocalDate.of(1900, 3, 1));
        personEvent(2L, EventType.BIRTH, LocalDate.of(1900, 7, 1));

        List<QualityIssueResponse> duplicates = duplicates();

        // Accents and case must not hide a duplicate; the birth year is what keeps it from flagging everyone.
        assertThat(duplicates).hasSize(1);
        assertThat(duplicates.getFirst().code()).isEqualTo(IssueCode.POSSIBLE_DUPLICATE);
    }

    @Test
    @DisplayName("đ matches d, as it does in the database's accent-insensitive search")
    void dStrokeMatchesD() {
        names.put(1L, "Đỗ Văn Đức");
        names.put(2L, "Do Van Duc");
        personEvent(1L, EventType.BIRTH, LocalDate.of(1900, 1, 1));
        personEvent(2L, EventType.BIRTH, LocalDate.of(1900, 1, 1));

        assertThat(duplicates()).hasSize(1);
    }

    @Test
    @DisplayName("the year compared is the one recorded, not a range's derived midpoint")
    void recordedYearIsCompared() {
        names.put(1L, "Nguyễn Văn Ánh");
        names.put(2L, "Nguyễn Văn Ánh");
        personEvent(1L, EventType.BIRTH, LocalDate.of(1889, 5, 1));
        personSpan(2L, EventType.BIRTH, LocalDate.of(1889, 1, 1), LocalDate.of(1892, 12, 31));

        assertThat(duplicates()).hasSize(1);
    }

    @Test
    @DisplayName("the same name in a different birth year is not a duplicate")
    void sameNameDifferentYearIsNotDuplicate() {
        names.put(1L, "Nguyễn Văn Ánh");
        names.put(2L, "Nguyễn Văn Ánh");
        personEvent(1L, EventType.BIRTH, LocalDate.of(1900, 1, 1));
        personEvent(2L, EventType.BIRTH, LocalDate.of(1935, 1, 1));

        assertThat(duplicates()).isEmpty();
    }

    @Test
    @DisplayName("a name-only double import is caught through the đời, and nothing at all is not evidence")
    void nameOnlyDuplicatesUseTheGeneration() {
        names.put(1L, "Nguyễn Văn An");
        names.put(2L, "Nguyễn Văn An");
        generations.put(1L, 4);
        generations.put(2L, 4);
        names.put(3L, "Nguyễn Văn Bình");
        names.put(4L, "Nguyễn Văn Bình");

        assertThat(duplicates())
                .extracting(QualityIssueResponse::personId, QualityIssueResponse::relatedPersonId)
                .containsExactly(tuple(2L, 1L));
    }

    @Test
    @DisplayName("findings come back most serious first")
    void findingsAreSortedBySeverity() {
        genders.put(2L, Gender.FEMALE);
        union(10L, 1L, 2L, 3L);
        union(11L, 3L, null, 1L);
        unionEvent(10L, EventType.MARRIAGE, LocalDate.of(1950, 1, 1));
        personEvent(3L, EventType.BIRTH, LocalDate.of(1940, 1, 1));
        personEvent(2L, EventType.DEATH, LocalDate.of(1935, 1, 1));

        List<QualityIssueResponse> issues = quality.checkAll();

        assertThat(issues.getFirst().severity()).isEqualTo(IssueSeverity.ERROR);
        assertThat(issues).extracting(QualityIssueResponse::severity).contains(IssueSeverity.WARNING);
        assertThat(issues.getLast().severity()).isEqualTo(IssueSeverity.INFO);
    }
}
