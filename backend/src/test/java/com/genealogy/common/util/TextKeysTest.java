package com.genealogy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.model.PersonNameType;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the Unicode handling of the name keys and the column cut. */
class TextKeysTest {

    private static final String COMPOSED = Normalizer.normalize("Nguyễn Văn Ánh", Normalizer.Form.NFC);
    private static final String DECOMPOSED = Normalizer.normalize("Nguyễn Văn Ánh", Normalizer.Form.NFD);

    @Test
    @DisplayName("a name typed decomposed on an iPhone is the same name as one typed composed on Windows")
    void nameKeyComposesFirst() {
        assertThat(DECOMPOSED).isNotEqualTo(COMPOSED);
        assertThat(NameKey.of(PersonNameType.BIRTH, null, null, DECOMPOSED))
                .isEqualTo(NameKey.of(PersonNameType.BIRTH, null, null, COMPOSED));
    }

    @Test
    @DisplayName("the search key drops every mark, đ included, the way the database's unaccent does")
    void searchKeyMatchesUnaccent() {
        assertThat(SearchKey.of("  Đỗ  Văn Đức ")).isEqualTo("do van duc");
        assertThat(SearchKey.of(DECOMPOSED)).isEqualTo(SearchKey.of(COMPOSED));
        assertThat(SearchKey.of(null)).isEmpty();
    }

    @Test
    @DisplayName("a cut never lands between a letter and its tone mark, nor inside a surrogate pair")
    void cutKeepsCharactersWhole() {
        // "Nguyễ" decomposed is N g u y e + circumflex + tilde: cutting at 6 would keep the circumflex alone.
        String nguye = Normalizer.normalize("Nguyễ", Normalizer.Form.NFD);
        assertThat(TextCut.toLength(nguye, 6)).isEqualTo("Nguy");
        assertThat(TextCut.toLength("ab𠀀c", 3)).isEqualTo("ab");
        assertThat(TextCut.toLength("abc", 0)).isEmpty();
        assertThat(TextCut.toLength("abc", 5)).isEqualTo("abc");
    }
}
