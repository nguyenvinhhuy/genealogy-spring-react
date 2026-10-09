package com.genealogy.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.media.domain.MediaKind;
import com.genealogy.suggestion.domain.SuggestionKind;
import com.genealogy.suggestion.domain.SuggestionStatus;
import com.genealogy.suggestion.domain.SuggestionTargetType;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Pins every enum stored as a string to the database CHECK that lists its values (CLAUDE.md §8.10 #24). */
// A constant added to an enum without a migration fails its first write, and was reported as "người khác vừa sửa".
class CheckConstraintsMatchEnumsTest {

    private static final Pattern CHECK_IN =
            Pattern.compile("(ck_[a-z0-9_]+)\\s+CHECK\\s*\\(\\s*\\w+\\s+IN\\s*\\(([^)]*)\\)", Pattern.DOTALL);

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__");

    /**
     * Lists each CHECK constraint with the enum whose values it must hold.
     *
     * @return the pairs
     */
    static Stream<Arguments> constraints() {
        return Stream.of(
                Arguments.of("ck_revisions_entity_type", AuditEntityType.class),
                Arguments.of("ck_media_target_type", MediaTargetType.class),
                Arguments.of("ck_media_kind", MediaKind.class),
                Arguments.of("ck_citations_target_type", CitationTargetType.class),
                Arguments.of("ck_places_type", PlaceType.class),
                Arguments.of("ck_graves_kind", GraveKind.class),
                Arguments.of("ck_sources_type", SourceType.class),
                Arguments.of("ck_events_subject_type", EventSubjectType.class),
                Arguments.of("ck_events_date_modifier", DateModifier.class),
                Arguments.of("ck_events_date_calendar", CalendarType.class),
                Arguments.of("ck_person_names_type", PersonNameType.class),
                Arguments.of("ck_persons_gender", Gender.class),
                Arguments.of("ck_families_status", FamilyStatus.class),
                Arguments.of("ck_family_children_rel1", RelationType.class),
                Arguments.of("ck_family_children_rel2", RelationType.class),
                Arguments.of("ck_suggestions_kind", SuggestionKind.class),
                Arguments.of("ck_suggestions_status", SuggestionStatus.class),
                Arguments.of("ck_suggestions_target_type", SuggestionTargetType.class));
    }

    @ParameterizedTest(name = "{0} lists exactly the values of {1}")
    @MethodSource("constraints")
    void checkMatchesEnum(String constraint, Class<? extends Enum<?>> type) throws Exception {
        Set<String> expected = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());

        assertThat(latestDefinitions().get(constraint)).as("latest definition of %s", constraint)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Reads every migration in version order and keeps each constraint's last definition.
     *
     * @return the values each constraint allows, by constraint name
     * @throws IOException when a migration cannot be read
     * @throws URISyntaxException when the migration folder cannot be located
     */
    private static Map<String, List<String>> latestDefinitions() throws IOException, URISyntaxException {
        Path folder = Path.of(Objects.requireNonNull(
                CheckConstraintsMatchEnumsTest.class.getClassLoader().getResource("db/migration")).toURI());
        Map<String, List<String>> latest = new HashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> migrations = files
                    .filter(file -> file.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparingInt(CheckConstraintsMatchEnumsTest::versionOf))
                    .toList();
            for (Path migration : migrations) {
                Matcher match = CHECK_IN.matcher(Files.readString(migration, StandardCharsets.UTF_8));
                while (match.find()) {
                    List<String> values = Arrays.stream(match.group(2).split(","))
                            .map(value -> value.strip().replace("'", ""))
                            .filter(value -> !value.isEmpty())
                            .toList();
                    latest.put(match.group(1), values);
                }
            }
        }
        return latest;
    }

    /**
     * Reads a migration file's version number.
     *
     * @param file the migration file
     * @return its version
     */
    private static int versionOf(Path file) {
        Matcher match = VERSION.matcher(file.getFileName().toString());
        return match.find() ? Integer.parseInt(match.group(1)) : Integer.MAX_VALUE;
    }
}
