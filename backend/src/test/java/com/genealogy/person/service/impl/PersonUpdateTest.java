package com.genealogy.person.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.audit.service.AuditService;
import com.genealogy.branch.service.BranchService;
import com.genealogy.common.exception.ConflictException;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.PersonNameType;
import com.genealogy.person.domain.Person;
import com.genealogy.person.domain.PersonName;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.request.PersonRequest;
import com.genealogy.person.mapper.PersonMapper;
import com.genealogy.person.repository.PersonRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for editing a person: names kept in place, and a stale form refused. */
@ExtendWith(MockitoExtension.class)
class PersonUpdateTest {

    private static final Long PERSON_ID = 3L;
    private static final Long ACTOR = 9L;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private BranchService branchService;

    @Mock
    private AuditService auditService;

    private PersonServiceImpl persons;
    private Person person;

    @BeforeEach
    void setUp() {
        persons = new PersonServiceImpl(
                personRepository, Mappers.getMapper(PersonMapper.class), branchService, auditService);
        person = new Person();
        person.setId(PERSON_ID);
        person.setGender(Gender.MALE);
        person.setVersion(4);
        person.getNames().add(name(11L, PersonNameType.BIRTH, "Ánh", true));
        person.getNames().add(name(12L, PersonNameType.HUY, "Đảm", false));
        when(personRepository.findForEdit(PERSON_ID)).thenReturn(Optional.of(person));
    }

    /**
     * Builds a stored name.
     *
     * @param id row id
     * @param type what kind of name
     * @param given the given name
     * @param primary whether it is the primary name
     * @return the entity
     */
    private static PersonName name(Long id, PersonNameType type, String given, boolean primary) {
        PersonName name = new PersonName();
        name.setId(id);
        name.setType(type);
        name.setSurname("Nguyễn");
        name.setGivenName(given);
        name.setPrimary(primary);
        return name;
    }

    /**
     * Builds an update carrying the given names.
     *
     * @param version the version the form was loaded at
     * @param names the names as they should now stand
     * @return the request
     */
    private static PersonRequest update(Long version, PersonNameRequest... names) {
        return new PersonRequest(Gender.MALE, null, null, List.of(names), "sửa tên", version);
    }

    @Test
    @DisplayName("editing a name keeps its row and id rather than deleting and re-inserting every name")
    void namesAreUpdatedInPlace() {
        PersonName birth = person.getNames().get(0);
        PersonName huy = person.getNames().get(1);

        persons.update(PERSON_ID, update(4L,
                new PersonNameRequest(PersonNameType.BIRTH, "Nguyễn", "Văn", "Ánh", true),
                new PersonNameRequest(PersonNameType.HUY, null, null, "Đảm Sửa", false)), ACTOR);

        assertThat(person.getNames()).containsExactly(birth, huy);
        assertThat(huy.getId()).isEqualTo(12L);
        assertThat(huy.getGivenName()).isEqualTo("Đảm Sửa");
        assertThat(birth.getMiddleName()).isEqualTo("Văn");
    }

    @Test
    @DisplayName("a name dropped from the form is removed, one added is appended, and the primary moves with the flag")
    void namesAreAddedRemovedAndPrimaryMoves() {
        persons.update(PERSON_ID, update(4L,
                new PersonNameRequest(PersonNameType.BIRTH, "Nguyễn", null, "Ánh", false),
                new PersonNameRequest(PersonNameType.TU, null, null, "Minh", true),
                new PersonNameRequest(PersonNameType.ALIAS, null, null, "Cả", false)), ACTOR);

        assertThat(person.getNames()).extracting(PersonName::getGivenName).containsExactly("Ánh", "Minh", "Cả");
        assertThat(person.getNames()).extracting(PersonName::isPrimary).containsExactly(false, true, false);

        persons.update(PERSON_ID, update(null,
                new PersonNameRequest(PersonNameType.BIRTH, "Nguyễn", null, "Ánh", false)), ACTOR);

        // An unflagged list promotes its first name, so a person is never left without a primary one (§6.1).
        assertThat(person.getNames()).extracting(PersonName::isPrimary).containsExactly(true);
    }

    @Test
    @DisplayName("a name-only edit still marks the person changed, so its version moves and a stale form is caught")
    void nameOnlyEditMovesTheVersion() {
        Instant untouched = person.getUpdatedAt();

        persons.update(PERSON_ID, update(4L,
                new PersonNameRequest(PersonNameType.BIRTH, "Nguyễn", null, "Ánh Mới", true)), ACTOR);

        assertThat(person.getUpdatedAt()).isNotEqualTo(untouched);
    }

    @Test
    @DisplayName("an edit made from an older version is refused before anything changes")
    void staleFormIsRefused() {
        assertThatThrownBy(() -> persons.update(PERSON_ID, update(3L,
                new PersonNameRequest(PersonNameType.BIRTH, "Lê", null, "Khác", true)), ACTOR))
                .isInstanceOf(ConflictException.class);

        assertThat(person.getNames().getFirst().getSurname()).isEqualTo("Nguyễn");
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }
}
