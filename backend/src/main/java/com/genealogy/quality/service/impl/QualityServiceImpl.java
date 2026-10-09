package com.genealogy.quality.service.impl;

import com.genealogy.common.model.EventSubjectType;
import com.genealogy.common.model.EventType;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.RelationType;
import com.genealogy.common.util.SearchKey;
import com.genealogy.event.dto.response.EventFactResponse;
import com.genealogy.event.service.EventService;
import com.genealogy.family.dto.response.ChildEdgeResponse;
import com.genealogy.family.dto.response.UnionEdgeResponse;
import com.genealogy.family.service.FamilyService;
import com.genealogy.person.dto.response.PersonNodeResponse;
import com.genealogy.person.service.PersonService;
import com.genealogy.quality.domain.IssueCode;
import com.genealogy.quality.dto.response.QualityIssueResponse;
import com.genealogy.quality.service.QualityService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link QualityService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityServiceImpl implements QualityService {

    private static final int MIN_MOTHER_AGE = 12;
    private static final int MAX_MOTHER_AGE = 55;
    private static final int MIN_MARRIAGE_AGE = 13;
    private static final int MAX_LIFESPAN_YEARS = 110;
    // A child born within this many days of the father's death is his; beyond it, something is wrong.
    private static final long POSTHUMOUS_GRACE_DAYS = 305;
    // Born more than this long before the marriage is worth noting, not correcting.
    private static final long BEFORE_MARRIAGE_GRACE_DAYS = 275;

    // Cross-feature by interface only (CLAUDE.md §4): no entity from another feature is imported here.
    private final PersonService personService;
    private final FamilyService familyService;
    private final EventService eventService;

    /**
     * Runs every consistency rule over the whole clan.
     *
     * @return the findings, most serious first
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<QualityIssueResponse> checkAll() {
        // Each load runs in its callee's own transaction; the rule work after it needs no pooled connection.
        Snapshot snapshot = loadSnapshot();
        List<QualityIssueResponse> issues = new ArrayList<>(findCycles(snapshot));

        snapshot.names().keySet().forEach(personId -> checkOnePerson(snapshot, personId, issues));
        snapshot.unions().forEach(union -> checkOneUnion(snapshot, union, issues));

        issues.addAll(findDuplicates(snapshot));
        issues.sort(Comparator.comparing(QualityIssueResponse::severity));
        return issues;
    }

    /**
     * Finds everyone whose own parent chain leads back to them.
     *
     * @param snapshot the loaded clan
     * @return one finding per person on a cycle, lowest id first
     */
    private List<QualityIssueResponse> findCycles(Snapshot snapshot) {
        Map<Long, Set<Long>> childrenOf = new HashMap<>();
        Map<Long, Integer> parentCount = new HashMap<>();
        for (UnionEdgeResponse union : snapshot.unions()) {
            for (ChildEdgeResponse child : union.children()) {
                parentCount.putIfAbsent(child.childId(), 0);
                for (Long parentId : partnersOf(union)) {
                    parentCount.putIfAbsent(parentId, 0);
                    if (childrenOf.computeIfAbsent(parentId, key -> new HashSet<>()).add(child.childId())) {
                        parentCount.merge(child.childId(), 1, Integer::sum);
                    }
                }
            }
        }

        // Peeling off everyone whose parents are all placed leaves only people on a cycle or below one.
        Deque<Long> ready = new ArrayDeque<>();
        parentCount.forEach((personId, count) -> {
            if (count == 0) {
                ready.add(personId);
            }
        });
        Set<Long> unresolved = new HashSet<>(parentCount.keySet());
        while (!ready.isEmpty()) {
            Long personId = ready.removeFirst();
            unresolved.remove(personId);
            for (Long childId : childrenOf.getOrDefault(personId, Set.of())) {
                if (parentCount.merge(childId, -1, Integer::sum) == 0) {
                    ready.add(childId);
                }
            }
        }

        return unresolved.stream()
                .filter(personId -> reachesItself(personId, childrenOf, unresolved))
                .sorted()
                .map(personId -> snapshot.issue(IssueCode.ANCESTRY_CYCLE, personId, null, null, Map.of()))
                .toList();
    }

    /**
     * Reports whether walking down from a person leads back to that person.
     *
     * @param personId the person
     * @param childrenOf each parent's children
     * @param within the only people the walk may pass through
     * @return true when the person is their own ancestor
     */
    private static boolean reachesItself(Long personId, Map<Long, Set<Long>> childrenOf, Set<Long> within) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(childrenOf.getOrDefault(personId, Set.of()));
        while (!pending.isEmpty()) {
            Long next = pending.removeFirst();
            if (next.equals(personId)) {
                return true;
            }
            if (within.contains(next) && seen.add(next)) {
                pending.addAll(childrenOf.getOrDefault(next, Set.of()));
            }
        }
        return false;
    }

    /**
     * Checks the rules that concern one person on their own.
     *
     * @param snapshot the loaded clan
     * @param personId the person to check
     * @param issues the findings collected so far
     */
    private void checkOnePerson(Snapshot snapshot, Long personId, List<QualityIssueResponse> issues) {
        Span birth = snapshot.personSpan(personId, EventType.BIRTH);
        Span death = snapshot.personSpan(personId, EventType.DEATH);
        Span burial = snapshot.personSpan(personId, EventType.BURIAL);

        if (Span.certainlyBefore(death, birth)) {
            issues.add(snapshot.issue(IssueCode.DEATH_BEFORE_BIRTH, personId, null, null, Map.of()));
        }
        if (Span.certainlyBefore(burial, death)) {
            issues.add(snapshot.issue(IssueCode.BURIAL_BEFORE_DEATH, personId, null, null, Map.of()));
        }
        Long shortestLifespan = Span.fewestYears(birth, death);
        if (shortestLifespan != null && shortestLifespan > MAX_LIFESPAN_YEARS) {
            issues.add(snapshot.issue(
                    IssueCode.IMPLAUSIBLE_LIFESPAN, personId, null, null, Map.of("years", shortestLifespan)));
        }
        for (EventType type : List.of(EventType.BIRTH, EventType.DEATH)) {
            // A merge keeps both records on purpose (§8 F11), so this page is where the contradiction surfaces.
            if (Span.disagree(snapshot.personSpans(personId, type))) {
                issues.add(snapshot.issue(
                        IssueCode.CONFLICTING_DATES, personId, null, null, Map.of("type", type.name())));
            }
        }
    }

    /**
     * Checks the rules that concern a union and its children.
     *
     * @param snapshot the loaded clan
     * @param union the union to check
     * @param issues the findings collected so far
     */
    private void checkOneUnion(Snapshot snapshot, UnionEdgeResponse union, List<QualityIssueResponse> issues) {
        Span marriage = snapshot.unionSpan(union.familyId(), EventType.MARRIAGE);
        if (marriage != null) {
            checkMarriageAge(snapshot, union, union.partner1Id(), union.partner2Id(), marriage, issues);
            checkMarriageAge(snapshot, union, union.partner2Id(), union.partner1Id(), marriage, issues);
        }
        for (ChildEdgeResponse child : union.children()) {
            checkOneChild(snapshot, union, child, marriage, issues);
        }
    }

    /**
     * Checks one partner's age at a union's marriage.
     *
     * @param snapshot the loaded clan
     * @param union the union
     * @param partnerId the partner to check, may be null
     * @param spouseId the other partner, may be null
     * @param marriage the marriage date
     * @param issues the findings collected so far
     */
    private void checkMarriageAge(
            Snapshot snapshot,
            UnionEdgeResponse union,
            Long partnerId,
            Long spouseId,
            Span marriage,
            List<QualityIssueResponse> issues) {
        if (partnerId == null) {
            return;
        }
        Span birth = snapshot.personSpan(partnerId, EventType.BIRTH);
        if (Span.certainlyBefore(marriage, birth)) {
            issues.add(snapshot.issue(IssueCode.MARRIED_BEFORE_BIRTH, partnerId, spouseId, union.familyId(), Map.of()));
            return;
        }
        Long oldestPossibleAge = Span.mostYears(birth, marriage);
        if (oldestPossibleAge != null && oldestPossibleAge < MIN_MARRIAGE_AGE) {
            issues.add(snapshot.issue(
                    IssueCode.MARRIED_TOO_YOUNG, partnerId, spouseId, union.familyId(),
                    Map.of("age", oldestPossibleAge)));
        }
    }

    /**
     * Checks one child against the marriage date and each birth parent.
     *
     * @param snapshot the loaded clan
     * @param union the union the child belongs to
     * @param child the child's link into the union
     * @param marriage the parents' marriage date, or null
     * @param issues the findings collected so far
     */
    private void checkOneChild(
            Snapshot snapshot,
            UnionEdgeResponse union,
            ChildEdgeResponse child,
            Span marriage,
            List<QualityIssueResponse> issues) {
        Span birth = snapshot.personSpan(child.childId(), EventType.BIRTH);
        if (birth == null) {
            return;
        }

        // A con riêng was born before a marriage they were never the fruit of, which is no finding at all.
        boolean ofThisCouple = isBirthLink(union.partner1Id(), child.relationToP1())
                && isBirthLink(union.partner2Id(), child.relationToP2());
        if (ofThisCouple && marriage != null && birth.latest() != null && marriage.earliest() != null
                && birth.latest().isBefore(marriage.earliest().minusDays(BEFORE_MARRIAGE_GRACE_DAYS))) {
            issues.add(snapshot.issue(
                    IssueCode.CHILD_BORN_BEFORE_MARRIAGE, child.childId(), null, union.familyId(), Map.of()));
        }

        checkBirthParent(snapshot, child.childId(), union.partner1Id(), child.relationToP1(), birth, issues);
        checkBirthParent(snapshot, child.childId(), union.partner2Id(), child.relationToP2(), birth, issues);
    }

    /**
     * Reports whether one side of a child's link is a birth parent, or absent.
     *
     * @param partnerId the partner on that side, may be null
     * @param relation the child's relation to that partner
     * @return true when there is no partner or the partner is a birth parent
     */
    private static boolean isBirthLink(Long partnerId, RelationType relation) {
        return partnerId == null || relation == RelationType.BIRTH;
    }

    /**
     * Checks a child's birth against one parent's own birth and death.
     *
     * @param snapshot the loaded clan
     * @param childId the child
     * @param parentId the parent, may be null
     * @param relation how the child relates to that parent
     * @param childBirth the child's birth
     * @param issues the findings collected so far
     */
    private void checkBirthParent(
            Snapshot snapshot,
            Long childId,
            Long parentId,
            RelationType relation,
            Span childBirth,
            List<QualityIssueResponse> issues) {
        // A step, adoptive or foster parent's dates say nothing about when this child could have been born.
        if (parentId == null || relation != RelationType.BIRTH) {
            return;
        }
        Span parentBirth = snapshot.personSpan(parentId, EventType.BIRTH);
        Span parentDeath = snapshot.personSpan(parentId, EventType.DEATH);

        if (Span.certainlyBefore(childBirth, parentBirth)) {
            issues.add(snapshot.issue(IssueCode.BORN_BEFORE_PARENT, childId, parentId, null, Map.of()));
            return;
        }

        Gender gender = snapshot.genderOf(parentId);
        if (gender == Gender.FEMALE) {
            if (Span.certainlyBefore(parentDeath, childBirth)) {
                issues.add(snapshot.issue(IssueCode.BORN_AFTER_MOTHER_DEATH, childId, parentId, null, Map.of()));
            }
            Long oldestPossibleAge = Span.mostYears(parentBirth, childBirth);
            if (oldestPossibleAge != null && oldestPossibleAge < MIN_MOTHER_AGE) {
                issues.add(snapshot.issue(
                        IssueCode.MOTHER_TOO_YOUNG, childId, parentId, null, Map.of("age", oldestPossibleAge)));
            }
            Long youngestPossibleAge = Span.fewestYears(parentBirth, childBirth);
            if (youngestPossibleAge != null && youngestPossibleAge > MAX_MOTHER_AGE) {
                issues.add(snapshot.issue(
                        IssueCode.MOTHER_TOO_OLD, childId, parentId, null, Map.of("age", youngestPossibleAge)));
            }
            return;
        }

        // An unrecorded sex gets only the father's looser bound, which holds whichever parent this is.
        if (parentDeath != null && parentDeath.latest() != null && childBirth.earliest() != null
                && childBirth.earliest().isAfter(parentDeath.latest().plusDays(POSTHUMOUS_GRACE_DAYS))) {
            IssueCode code = gender == Gender.MALE
                    ? IssueCode.BORN_LONG_AFTER_FATHER_DEATH
                    : IssueCode.BORN_LONG_AFTER_PARENT_DEATH;
            issues.add(snapshot.issue(code, childId, parentId, null, Map.of()));
        }
    }

    /**
     * Finds people whose primary name matches and whose birth year, or failing that đời, agrees.
     *
     * @param snapshot the loaded clan
     * @return the suspected duplicates
     */
    private List<QualityIssueResponse> findDuplicates(Snapshot snapshot) {
        Map<String, List<Long>> byKey = new HashMap<>();
        snapshot.names().forEach((personId, name) -> {
            String normalised = SearchKey.of(name);
            Integer year = snapshot.birthYear(personId);
            Integer generation = snapshot.generationOf(personId);
            // Name alone flags every Nguyễn Văn An; đời stands in for the year so a name-only import is still caught.
            String anchor = year != null ? "year " + year : generation != null ? "đời " + generation : null;
            if (anchor == null || normalised.isEmpty()) {
                return;
            }
            byKey.computeIfAbsent(normalised + "|" + anchor, ignored -> new ArrayList<>()).add(personId);
        });

        List<QualityIssueResponse> duplicates = new ArrayList<>();
        byKey.values().stream()
                .filter(group -> group.size() > 1)
                .forEach(group -> {
                    List<Long> sorted = group.stream().sorted().toList();
                    for (int i = 1; i < sorted.size(); i++) {
                        duplicates.add(snapshot.issue(
                                IssueCode.POSSIBLE_DUPLICATE, sorted.get(i), sorted.get(0), null, Map.of()));
                    }
                });
        duplicates.sort(Comparator.comparing(QualityIssueResponse::personId));
        return duplicates;
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
     * Loads the whole clan through the three whole-graph projections.
     *
     * @return the snapshot every rule reads from
     */
    private Snapshot loadSnapshot() {
        // Every person, not only those with a dated event: a name-only double import is the commonest duplicate.
        return Snapshot.of(eventService.findAllFacts(), familyService.findAllUnions(), personService.findAllNodes());
    }

    /**
     * The span a fuzzy date allows, as the earliest and latest Gregorian day it could be.
     *
     * @param earliest the first possible day, or null when unbounded
     * @param latest the last possible day, or null when unbounded
     */
    private record Span(LocalDate earliest, LocalDate latest) {

        /**
         * Merges several records of one fact into the smallest span that covers all of them.
         *
         * @param spans the records, possibly empty
         * @return the covering span, or null when there is no record
         */
        static Span covering(List<Span> spans) {
            if (spans.isEmpty()) {
                return null;
            }
            LocalDate earliest = spans.getFirst().earliest();
            LocalDate latest = spans.getFirst().latest();
            for (Span span : spans) {
                earliest = earliest == null || span.earliest() == null ? null : min(earliest, span.earliest());
                latest = latest == null || span.latest() == null ? null : max(latest, span.latest());
            }
            return new Span(earliest, latest);
        }

        /**
         * Reports whether one fact certainly happened before another, whatever the vague parts turn out to be.
         *
         * @param first the fact that should be earlier, may be null
         * @param second the fact that should be later, may be null
         * @return true only when even the latest reading of the first precedes the earliest reading of the second
         */
        static boolean certainlyBefore(Span first, Span second) {
            return first != null && second != null && first.latest() != null && second.earliest() != null
                    && first.latest().isBefore(second.earliest());
        }

        /**
         * Returns the fewest whole years that can separate two facts.
         *
         * @param from the earlier fact, may be null
         * @param to the later fact, may be null
         * @return the years, or null when either side is unbounded or missing
         */
        static Long fewestYears(Span from, Span to) {
            if (from == null || to == null || from.latest() == null || to.earliest() == null) {
                return null;
            }
            return ChronoUnit.YEARS.between(from.latest(), to.earliest());
        }

        /**
         * Returns the most whole years that can separate two facts.
         *
         * @param from the earlier fact, may be null
         * @param to the later fact, may be null
         * @return the years, or null when either side is unbounded or missing
         */
        static Long mostYears(Span from, Span to) {
            if (from == null || to == null || from.earliest() == null || to.latest() == null) {
                return null;
            }
            return ChronoUnit.YEARS.between(from.earliest(), to.latest());
        }

        /**
         * Reports whether two records of one fact cannot both be true.
         *
         * @param spans the records
         * @return true when some pair does not overlap at all
         */
        static boolean disagree(List<Span> spans) {
            for (Span a : spans) {
                for (Span b : spans) {
                    if (certainlyBefore(a, b)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Returns the earlier of two days.
         *
         * @param a one day
         * @param b the other
         * @return the earlier
         */
        private static LocalDate min(LocalDate a, LocalDate b) {
            return a.isBefore(b) ? a : b;
        }

        /**
         * Returns the later of two days.
         *
         * @param a one day
         * @param b the other
         * @return the later
         */
        private static LocalDate max(LocalDate a, LocalDate b) {
            return a.isAfter(b) ? a : b;
        }
    }

    /**
     * Identifies one kind of fact about one subject.
     *
     * @param subjectType a person or a union
     * @param subjectId the subject
     * @param type the event type
     */
    private record FactKey(EventSubjectType subjectType, Long subjectId, EventType type) {
    }

    /**
     * Everything the rules need, indexed once so no rule scans a list.
     *
     * @param names every person's display name, lowest id first
     * @param genders every person's recorded sex
     * @param generations every placed person's đời
     * @param unions every union in the clan
     * @param spans each subject's recorded dates by event type
     * @param birthYears each person's earliest recorded birth year
     */
    private record Snapshot(
            Map<Long, String> names,
            Map<Long, Gender> genders,
            Map<Long, Integer> generations,
            List<UnionEdgeResponse> unions,
            Map<FactKey, List<Span>> spans,
            Map<Long, Integer> birthYears) {

        /**
         * Indexes the three projections.
         *
         * @param facts every dated event
         * @param unions every union
         * @param nodes every person
         * @return the snapshot
         */
        static Snapshot of(
                List<EventFactResponse> facts, List<UnionEdgeResponse> unions, List<PersonNodeResponse> nodes) {
            Map<Long, String> names = new LinkedHashMap<>();
            Map<Long, Gender> genders = new HashMap<>();
            Map<Long, Integer> generations = new HashMap<>();
            for (PersonNodeResponse node : nodes) {
                names.put(node.id(), node.displayName());
                genders.put(node.id(), node.gender());
                if (node.generation() != null) {
                    generations.put(node.id(), node.generation());
                }
            }

            Map<FactKey, List<Span>> spans = new HashMap<>();
            Map<Long, Integer> birthYears = new HashMap<>();
            for (EventFactResponse fact : facts) {
                if (fact.subjectId() == null) {
                    continue;
                }
                spans.computeIfAbsent(new FactKey(fact.subjectType(), fact.subjectId(), fact.type()),
                                key -> new ArrayList<>())
                        .add(new Span(fact.earliest(), fact.latest()));
                if (fact.subjectType() == EventSubjectType.PERSON && fact.type() == EventType.BIRTH
                        && fact.year() != null) {
                    // The recorded year, not a derived day: "khoảng 1890" is an 1890 birth to anyone (§8 F20).
                    birthYears.merge(fact.subjectId(), fact.year(), Math::min);
                }
            }
            return new Snapshot(names, genders, generations, unions, spans, birthYears);
        }

        /**
         * Returns every recorded date of one kind of a person's events.
         *
         * @param personId the person
         * @param type the event type
         * @return the recorded spans, empty when there is none
         */
        List<Span> personSpans(Long personId, EventType type) {
            return spans.getOrDefault(new FactKey(EventSubjectType.PERSON, personId, type), List.of());
        }

        /**
         * Returns the span covering every recorded date of one kind of a person's events.
         *
         * @param personId the person
         * @param type the event type
         * @return the span, or null when unrecorded
         */
        Span personSpan(Long personId, EventType type) {
            return Span.covering(personSpans(personId, type));
        }

        /**
         * Returns the span covering every recorded date of one kind of a union's events.
         *
         * @param familyId the union
         * @param type the event type
         * @return the span, or null when unrecorded
         */
        Span unionSpan(Long familyId, EventType type) {
            return Span.covering(spans.getOrDefault(new FactKey(EventSubjectType.FAMILY, familyId, type), List.of()));
        }

        /**
         * Returns a person's recorded sex.
         *
         * @param personId the person
         * @return the sex, UNKNOWN when the person is not in the clan
         */
        Gender genderOf(Long personId) {
            return genders.getOrDefault(personId, Gender.UNKNOWN);
        }

        /**
         * Returns a person's đời.
         *
         * @param personId the person
         * @return the đời, or null when the parentage graph has not placed them
         */
        Integer generationOf(Long personId) {
            return generations.get(personId);
        }

        /**
         * Returns a person's earliest recorded birth year.
         *
         * @param personId the person
         * @return the year, or null when no birth year is recorded
         */
        Integer birthYear(Long personId) {
            return birthYears.get(personId);
        }

        /**
         * Builds one finding, resolving both names.
         *
         * @param code the rule that fired
         * @param personId the person it is about
         * @param relatedId the other person involved, or null
         * @param familyId the union it concerns, or null
         * @param params the numbers the message needs
         * @return the finding
         */
        QualityIssueResponse issue(
                IssueCode code, Long personId, Long relatedId, Long familyId, Map<String, Object> params) {
            return new QualityIssueResponse(
                    code,
                    code.severity(),
                    personId,
                    names.get(personId),
                    relatedId,
                    relatedId == null ? null : names.get(relatedId),
                    familyId,
                    params);
        }
    }
}
