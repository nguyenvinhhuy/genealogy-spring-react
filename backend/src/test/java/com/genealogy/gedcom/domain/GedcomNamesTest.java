package com.genealogy.gedcom.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.model.PersonNameType;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.response.PersonNameResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for converting a Vietnamese name to and from a GEDCOM NAME payload. */
class GedcomNamesTest {

    /**
     * Builds a name response with the given parts.
     *
     * @param surname họ, may be null
     * @param middleName tên đệm, may be null
     * @param givenName tên
     * @return the name
     */
    private static PersonNameResponse name(String surname, String middleName, String givenName) {
        return new PersonNameResponse(1L, PersonNameType.BIRTH, surname, middleName, givenName, true, "");
    }

    @Test
    @DisplayName("a name renders given-first with the surname in slashes, as GEDCOM requires")
    void rendersGedcomOrder() {
        assertThat(GedcomNames.format(name("Nguyễn", "Văn", "Ánh"))).isEqualTo("Văn Ánh /Nguyễn/");
    }

    @Test
    @DisplayName("a name with no surname still renders empty slashes, so a re-import invents no họ")
    void rendersEmptySlashesWithoutSurname() {
        assertThat(GedcomNames.format(name(null, null, "Đảm"))).isEqualTo("Đảm //");
        assertThat(GedcomNames.format(name(null, "Văn", "An"))).isEqualTo("Văn An //");
    }

    @Test
    @DisplayName("a surname-less name survives its own round trip without gaining a họ")
    void roundTripsWithoutInventingSurname() {
        String payload = GedcomNames.format(name(null, "Văn", "An"));
        PersonNameRequest parsed = GedcomNames.parse(payload, PersonNameType.BIRTH, true);

        assertThat(parsed).isNotNull();
        // Without the empty slashes this read họ "Văn", tên "An" — a surname the family never had.
        assertThat(parsed.surname()).isNull();
        assertThat(parsed.middleName()).isEqualTo("Văn");
        assertThat(parsed.givenName()).isEqualTo("An");
    }

    @Test
    @DisplayName("name parts in GIVN and SURN are read when the payload carries only slashes")
    void readsStructuredSubtags() {
        PersonNameRequest parsed = GedcomNames.parse("//", "An", "Nguyễn", PersonNameType.BIRTH, true);

        assertThat(parsed).isNotNull();
        assertThat(parsed.surname()).isEqualTo("Nguyễn");
        assertThat(parsed.givenName()).isEqualTo("An");
    }

    @Test
    @DisplayName("a slashed payload parses back into Vietnamese parts")
    void parsesSlashedPayload() {
        PersonNameRequest parsed = GedcomNames.parse("Văn Ánh /Nguyễn/", PersonNameType.BIRTH, true);

        assertThat(parsed).isNotNull();
        assertThat(parsed.surname()).isEqualTo("Nguyễn");
        assertThat(parsed.middleName()).isEqualTo("Văn");
        assertThat(parsed.givenName()).isEqualTo("Ánh");
    }

    @Test
    @DisplayName("a payload with no slashes is read surname-first, the way Vietnamese names are written")
    void parsesUnslashedAsVietnameseOrder() {
        PersonNameRequest parsed = GedcomNames.parse("Nguyễn Văn Ánh", PersonNameType.BIRTH, true);

        assertThat(parsed).isNotNull();
        assertThat(parsed.surname()).isEqualTo("Nguyễn");
        assertThat(parsed.middleName()).isEqualTo("Văn");
        assertThat(parsed.givenName()).isEqualTo("Ánh");
    }

    @Test
    @DisplayName("a single-token payload is the given name, not a surname")
    void parsesSingleToken() {
        PersonNameRequest parsed = GedcomNames.parse("Đảm", PersonNameType.HUY, false);

        assertThat(parsed).isNotNull();
        assertThat(parsed.givenName()).isEqualTo("Đảm");
        assertThat(parsed.surname()).isNull();
        assertThat(parsed.middleName()).isNull();
    }

    @Test
    @DisplayName("a name survives a round trip through GEDCOM unchanged")
    void roundTripsName() {
        PersonNameRequest parsed =
                GedcomNames.parse(GedcomNames.format(name("Nguyễn", "Thị Thu", "Hà")), PersonNameType.BIRTH, true);

        assertThat(parsed).isNotNull();
        assertThat(parsed.surname()).isEqualTo("Nguyễn");
        assertThat(parsed.middleName()).isEqualTo("Thị Thu");
        assertThat(parsed.givenName()).isEqualTo("Hà");
    }

    @Test
    @DisplayName("a Vietnamese name kind survives our own round trip, and a foreign one falls back sensibly")
    void mapsNameTypes() {
        assertThat(GedcomNames.typeOf("HUY")).isEqualTo(PersonNameType.HUY);
        assertThat(GedcomNames.typeOf("THUY")).isEqualTo(PersonNameType.THUY);
        assertThat(GedcomNames.typeOf("BIRTH")).isEqualTo(PersonNameType.BIRTH);
        assertThat(GedcomNames.typeOf("AKA")).isEqualTo(PersonNameType.ALIAS);
        assertThat(GedcomNames.typeOf("something else")).isEqualTo(PersonNameType.ALIAS);
        assertThat(GedcomNames.typeOf(null)).isEqualTo(PersonNameType.BIRTH);
    }

    @Test
    @DisplayName("an empty payload yields nothing rather than a blank person")
    void rejectsEmptyPayload() {
        assertThat(GedcomNames.parse(null, PersonNameType.BIRTH, true)).isNull();
        assertThat(GedcomNames.parse("   ", PersonNameType.BIRTH, true)).isNull();
    }
}
