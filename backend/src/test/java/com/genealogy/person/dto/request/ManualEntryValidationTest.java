package com.genealogy.person.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.model.Gender;
import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.web.ManualEntry;
import com.genealogy.family.dto.request.AddRelationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins the họ and giới tính a person typed in by hand must carry, and an import need not (§6.1). */
class ManualEntryValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /**
     * Builds a one-name list.
     *
     * @param surname the họ, may be null or blank
     * @return the names
     */
    private static List<PersonNameRequest> names(String surname) {
        return List.of(new PersonNameRequest(PersonNameType.BIRTH, surname, "Văn", "An", true));
    }

    /**
     * Collects the paths of the violations a manual entry gets.
     *
     * @param request the request
     * @return the property paths
     */
    private static List<String> manualViolations(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request, Default.class, ManualEntry.class);
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).sorted().toList();
    }

    @Test
    @DisplayName("a person typed in by hand needs a họ and a chosen sex; Chưa rõ counts as chosen")
    void manualPersonNeedsSurnameAndSex() {
        assertThat(manualViolations(new PersonRequest(null, null, null, names(" "), null, null)))
                .containsExactly("gender", "surname");
        assertThat(manualViolations(new PersonRequest(Gender.UNKNOWN, null, null, names("Nguyễn"), null, null)))
                .isEmpty();
    }

    @Test
    @DisplayName("the họ is read from the primary name, not from an alternate")
    void surnameIsReadFromThePrimaryName() {
        List<PersonNameRequest> names = List.of(
                new PersonNameRequest(PersonNameType.HUY, "Nguyễn", null, "Đảm", false),
                new PersonNameRequest(PersonNameType.BIRTH, null, null, "Anh", true));
        assertThat(manualViolations(new PersonRequest(Gender.MALE, null, null, names, null, null)))
                .containsExactly("surname");
    }

    @Test
    @DisplayName("an imported person, validated without the manual group, may lack both")
    void importNeedsNeither() {
        assertThat(validator.validate(new PersonRequest(null, null, null, names(null), null, null))).isEmpty();
    }

    @Test
    @DisplayName("a spouse or child added from the tree follows the same rule")
    void addRelationNeedsSurnameAndSex() {
        assertThat(manualViolations(
                new AddRelationRequest(AddRelationRequest.Kind.CHILD, null, names(null), null, null)))
                .containsExactly("gender", "surname");
        assertThat(manualViolations(
                new AddRelationRequest(AddRelationRequest.Kind.CHILD, Gender.FEMALE, names("Lê"), null, null)))
                .isEmpty();
    }
}
