package com.genealogy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.model.PersonNameType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the one rule of when two names are the same name. */
class NameKeyTest {

    @Test
    @DisplayName("case and stray spacing do not make two names different")
    void caseAndSpacingAreIgnored() {
        assertThat(NameKey.of(PersonNameType.BIRTH, "Nguyễn", "Văn", "Ánh"))
                .isEqualTo(NameKey.of(PersonNameType.BIRTH, " nguyễn ", "văn", "ÁNH "));
        assertThat(NameKey.of(PersonNameType.BIRTH, "Nguyễn", "Văn  Hữu", "Ánh"))
                .isEqualTo(NameKey.of(PersonNameType.BIRTH, "Nguyễn", "Văn Hữu", "Ánh"));
    }

    @Test
    @DisplayName("a missing part and an empty one are the same, as the form and the import record them differently")
    void nullAndEmptyAreTheSame() {
        assertThat(NameKey.of(PersonNameType.HUY, null, null, "Đảm"))
                .isEqualTo(NameKey.of(PersonNameType.HUY, "", "  ", "Đảm"));
    }

    @Test
    @DisplayName("diacritics are kept: Ánh and Anh are two different names")
    void diacriticsAreKept() {
        assertThat(NameKey.of(PersonNameType.BIRTH, "Nguyễn", null, "Ánh"))
                .isNotEqualTo(NameKey.of(PersonNameType.BIRTH, "Nguyen", null, "Anh"));
    }

    @Test
    @DisplayName("the same spelling under another kind of name is a different name")
    void typeMatters() {
        assertThat(NameKey.of(PersonNameType.HUY, null, null, "Đảm"))
                .isNotEqualTo(NameKey.of(PersonNameType.TU, null, null, "Đảm"));
    }

    @Test
    @DisplayName("where the parts are split matters: họ Văn, tên An is not tên đệm Văn, tên An")
    void partsAreComparedSeparately() {
        // The merge used to compare the display string, where these two both read "Văn An".
        assertThat(NameKey.of(PersonNameType.BIRTH, "Văn", null, "An"))
                .isNotEqualTo(NameKey.of(PersonNameType.BIRTH, null, "Văn", "An"));
    }
}
