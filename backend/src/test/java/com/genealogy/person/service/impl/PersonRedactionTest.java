package com.genealogy.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.model.Role;
import com.genealogy.person.domain.Person;
import com.genealogy.person.domain.PersonName;
import com.genealogy.person.dto.response.PersonDetailResponse;
import com.genealogy.person.dto.response.PersonRedactedResponse;
import com.genealogy.person.dto.response.PersonSummaryResponse;
import com.genealogy.person.dto.response.PersonView;
import com.genealogy.person.mapper.PersonMapper;
import com.genealogy.person.repository.PersonRepository;
import java.lang.reflect.RecordComponent;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for living-person redaction per role (CLAUDE.md §3.6, §4.2). */
@ExtendWith(MockitoExtension.class)
class PersonRedactionTest {

    private static final Long PERSON_ID = 3L;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private BranchService branchService;

    @Mock
    private AuditService auditService;

    private PersonServiceImpl persons;

    @BeforeEach
    void setUp() {
        // The real MapStruct mapper, per CLAUDE.md §4.1 — a stubbed mapper would hide a leak in the mapping.
        PersonMapper personMapper = Mappers.getMapper(PersonMapper.class);
        persons = new PersonServiceImpl(personRepository, personMapper, branchService, auditService);
    }

    /**
     * Builds a person carrying a private note and an alternate name.
     *
     * @param living whether the person is treated as living
     * @return the entity
     */
    private static Person person(boolean living) {
        Person person = new Person();
        person.setId(PERSON_ID);
        person.setGender(Gender.MALE);
        person.setGeneration(4);
        person.setBranchId(9L);
        person.setNotes("Số điện thoại 090xxxx, địa chỉ nhà riêng");
        person.setLiving(living);

        PersonName birth = new PersonName();
        birth.setType(PersonNameType.BIRTH);
        birth.setSurname("Nguyễn");
        birth.setMiddleName("Văn");
        birth.setGivenName("Ánh");
        birth.setPrimary(true);

        PersonName huy = new PersonName();
        huy.setType(PersonNameType.HUY);
        huy.setGivenName("Đảm");

        person.getNames().add(birth);
        person.getNames().add(huy);
        return person;
    }

    @Test
    @DisplayName("a MEMBER sees a living person redacted, with nothing private in the payload")
    void memberSeesLivingPersonRedacted() {
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person(true)));

        PersonView view = persons.getById(PERSON_ID, Role.MEMBER);

        assertThat(view).isInstanceOf(PersonRedactedResponse.class);
        PersonRedactedResponse redacted = (PersonRedactedResponse) view;
        assertThat(redacted.id()).isEqualTo(PERSON_ID);
        assertThat(redacted.living()).isTrue();
        assertThat(redacted.redacted()).isTrue();
        // Name, gender and đời stay: without them the tree cannot be drawn at all (§3.1, §3.5).
        assertThat(redacted.displayName()).isEqualTo("Nguyễn Văn Ánh");
        assertThat(redacted.gender()).isEqualTo(Gender.MALE);
        assertThat(redacted.generation()).isEqualTo(4);
        // A separate DTO means there is no notes, branch or names field to forget to null out.
        assertThat(redacted.toString()).doesNotContain("090xxxx").doesNotContain("Đảm");
    }

    @Test
    @DisplayName("a MEMBER sees a deceased person in full — the rule is about the living only")
    void memberSeesDeceasedPersonInFull() {
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person(false)));

        PersonView view = persons.getById(PERSON_ID, Role.MEMBER);

        assertThat(view).isInstanceOf(PersonDetailResponse.class);
        PersonDetailResponse detail = (PersonDetailResponse) view;
        assertThat(detail.notes()).contains("090xxxx");
        assertThat(detail.names()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "EDITOR"})
    @DisplayName("an EDITOR and an ADMIN see a living person in full")
    void editorsSeeLivingPersonInFull(Role role) {
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person(true)));

        PersonView view = persons.getById(PERSON_ID, role);

        assertThat(view).isInstanceOf(PersonDetailResponse.class);
        assertThat(((PersonDetailResponse) view).notes()).contains("090xxxx");
    }

    @Test
    @DisplayName("a null role is treated as the least privileged, not the most")
    void unauthenticatedCallerIsRedacted() {
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person(true)));

        // Must fail closed: a null role reaching a `!= MEMBER` check would hand an anonymous caller all.
        assertThat(persons.getById(PERSON_ID, null)).isInstanceOf(PersonRedactedResponse.class);
        assertThat(Role.maySeeLivingDetails(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("only EDITOR and ADMIN may read living-person details")
    void rolePredicateIsExhaustive(Role role) {
        assertThat(Role.maySeeLivingDetails(role)).isEqualTo(role == Role.ADMIN || role == Role.EDITOR);
    }

    @Test
    @DisplayName("a summary carries no chi, because a list is served to every role unredacted")
    void theSummaryHasNoBranchField() {
        // The detail view withholds chi for a living person, so a list that carries it undoes §3.6.
        assertThat(PersonSummaryResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("branchId", "notes", "names", "createdAt");
    }

    @Test
    @DisplayName("an unknown person counts as living, so a bad id cannot be used to probe who exists")
    void isLivingFailsClosedForAnUnknownId() {
        when(personRepository.findById(999L)).thenReturn(Optional.empty());

        // Every feature's §3.6 guard now routes through here, so this is the one place the rule can be lost.
        assertThat(persons.isLiving(999L)).isTrue();
    }

    @Test
    @DisplayName("isLiving reports what the derived column says")
    void isLivingReadsTheDerivedFlag() {
        when(personRepository.findById(PERSON_ID)).thenReturn(Optional.of(person(false)));

        assertThat(persons.isLiving(PERSON_ID)).isFalse();
    }
}
