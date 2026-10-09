package com.genealogy.gedcom.domain;

import com.genealogy.common.model.EventType;
import com.genealogy.common.model.FamilyStatus;
import com.genealogy.common.model.Gender;
import com.genealogy.common.model.GraveKind;
import com.genealogy.common.model.RelationType;
import com.genealogy.common.model.SourceType;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Maps this app's enums to the GEDCOM tags and values that mean the same thing. */
public final class GedcomTags {

    private static final String PHRASE_SEPARATOR = "/";

    private static final Map<String, EventType> PERSON_EVENTS = Map.ofEntries(
            Map.entry("BIRT", EventType.BIRTH),
            Map.entry("DEAT", EventType.DEATH),
            Map.entry("BURI", EventType.BURIAL),
            Map.entry("CREM", EventType.BURIAL),
            Map.entry("RESI", EventType.RESIDENCE),
            Map.entry("OCCU", EventType.OCCUPATION),
            Map.entry("EDUC", EventType.EDUCATION),
            Map.entry("GRAD", EventType.EDUCATION),
            Map.entry("EVEN", EventType.OTHER_PERSON));

    private static final Map<String, EventType> FAMILY_EVENTS = Map.of(
            "MARR", EventType.MARRIAGE,
            "DIV", EventType.DIVORCE,
            "ENGA", EventType.OTHER_FAMILY,
            "EVEN", EventType.OTHER_FAMILY);

    /** Not instantiable. */
    private GedcomTags() {
    }

    /**
     * Returns the GEDCOM tag that records an event type.
     *
     * @param type the event type
     * @return the tag
     */
    public static String tagOf(EventType type) {
        // An exhaustive switch, not a map with a default: a new EventType must fail the build, not export as EVEN.
        return switch (type) {
            case BIRTH -> "BIRT";
            case DEATH -> "DEAT";
            case BURIAL -> "BURI";
            // GEDCOM has no cải táng tag, so it is an EVEN whose TYPE names it, read back by personEventOf.
            case REBURIAL -> "EVEN";
            case RESIDENCE -> "RESI";
            case OCCUPATION -> "OCCU";
            case EDUCATION -> "EDUC";
            case MARRIAGE -> "MARR";
            case DIVORCE -> "DIV";
            case OTHER_PERSON, OTHER_FAMILY -> "EVEN";
        };
    }

    /**
     * Returns the event type a tag under an INDI record means, reading an EVEN's TYPE for a cải táng.
     *
     * @param tag the GEDCOM tag
     * @param type the event's TYPE payload, or null
     * @return the event type, or empty when this app does not model it
     */
    public static Optional<EventType> personEventOf(String tag, String type) {
        if ("EVEN".equals(tag) && type != null && EventType.REBURIAL.name().equalsIgnoreCase(type.strip())) {
            return Optional.of(EventType.REBURIAL);
        }
        return Optional.ofNullable(PERSON_EVENTS.get(tag));
    }

    /**
     * Reports whether an event is written as an EVEN whose TYPE line is what says which event it is.
     *
     * @param type the event type
     * @return true when the export must write a TYPE line for it
     */
    public static boolean needsTypeLine(EventType type) {
        return type == EventType.OTHER_PERSON || type == EventType.OTHER_FAMILY || type == EventType.REBURIAL;
    }

    /**
     * Reads the grave kind our own export wrote under the grave extension's TYPE.
     *
     * @param value the payload, or null
     * @return the grave kind, LIVING_PLOT when absent or unrecognised
     */
    public static GraveKind graveKindOf(String value) {
        // Fails closed: read as a mộ, an unknown kind made its owner "dead" and public; a sinh phần stays private.
        if (value == null || value.isBlank()) {
            return GraveKind.LIVING_PLOT;
        }
        try {
            return GraveKind.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GraveKind.LIVING_PLOT;
        }
    }

    /**
     * Returns the event type a tag under a FAM record means.
     *
     * @param tag the GEDCOM tag
     * @return the event type, or empty when this app does not model it
     */
    public static Optional<EventType> familyEventOf(String tag) {
        return Optional.ofNullable(FAMILY_EVENTS.get(tag));
    }

    /**
     * Returns the SEX payload for a recorded gender.
     *
     * @param gender the gender
     * @return the payload
     */
    public static String sexOf(Gender gender) {
        return switch (gender) {
            case MALE -> "M";
            case FEMALE -> "F";
            case UNKNOWN -> "U";
        };
    }

    /**
     * Reads a SEX payload back into a gender.
     *
     * @param sex the payload, or null
     * @return the gender, UNKNOWN for anything unrecognised
     */
    public static Gender genderOf(String sex) {
        if (sex == null) {
            return Gender.UNKNOWN;
        }
        return switch (sex.strip().toUpperCase(Locale.ROOT)) {
            case "M" -> Gender.MALE;
            case "F" -> Gender.FEMALE;
            default -> Gender.UNKNOWN;
        };
    }

    /**
     * Returns the PEDI payload describing how a child joined a family.
     *
     * @param relation how the child relates to the partner
     * @return the payload, or null when GEDCOM's default of birth applies
     */
    public static String pedigreeOf(RelationType relation) {
        return switch (relation) {
            case BIRTH -> null;
            case ADOPTED -> "ADOPTED";
            // STEP is not in GEDCOM 7's PEDI enumset, so it rides in the PHRASE beside the nearest legal value.
            case FOSTER, STEP -> "FOSTER";
        };
    }

    /**
     * Returns the PEDI PHRASE naming what the GEDCOM enumeration cannot say about a child link.
     *
     * @param toPartner1 how the child relates to the union's first partner
     * @param toPartner2 how the child relates to the union's second partner
     * @return the phrase, or null when PEDI alone already says it exactly
     */
    public static String pedigreePhraseOf(RelationType toPartner1, RelationType toPartner2) {
        // PEDI is one value per FAMC link, so both an asymmetric pair (§3.1) and STEP have to ride here.
        return toPartner1 == toPartner2 && toPartner1 != RelationType.STEP
                ? null
                : toPartner1.name() + PHRASE_SEPARATOR + toPartner2.name();
    }

    /**
     * Reads a PEDI payload and its phrase back into the child's relation to each partner.
     *
     * @param pedigree the PEDI payload, or null
     * @param phrase the PEDI PHRASE payload, or null
     * @return the relation to each partner, BIRTH for both when nothing is recorded
     */
    public static ChildRelations relationsOf(String pedigree, String phrase) {
        // The phrase is read first: it is the only place STEP and an asymmetric pair survive a round trip.
        if (phrase != null) {
            String[] halves = phrase.strip().split(PHRASE_SEPARATOR, 2);
            RelationType first = named(halves[0]);
            RelationType second = halves.length > 1 ? named(halves[1]) : first;
            if (first != null && second != null) {
                return new ChildRelations(first, second);
            }
        }
        RelationType both = pedigree == null ? RelationType.BIRTH : switch (pedigree.strip().toUpperCase(Locale.ROOT)) {
            case "ADOPTED" -> RelationType.ADOPTED;
            case "FOSTER" -> RelationType.FOSTER;
            case "STEP" -> RelationType.STEP;
            default -> RelationType.BIRTH;
        };
        return new ChildRelations(both, both);
    }

    /**
     * How a child relates to each partner of the union they are linked into.
     *
     * @param toPartner1 relation to the union's first partner
     * @param toPartner2 relation to the union's second partner
     */
    public record ChildRelations(RelationType toPartner1, RelationType toPartner2) {
    }

    /**
     * Reads a relation named verbatim, for a payload our own export wrote.
     *
     * @param text the payload to read
     * @return the relation, or null when the text names none
     */
    private static RelationType named(String text) {
        try {
            return RelationType.valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Reads the source kind our own export wrote under its extension tag.
     *
     * @param value the extension payload, or null
     * @return the source kind, OTHER when absent or unrecognised
     */
    public static SourceType sourceTypeOf(String value) {
        if (value == null || value.isBlank()) {
            return SourceType.OTHER;
        }
        try {
            return SourceType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SourceType.OTHER;
        }
    }

    /**
     * Works out what state a union is in from the events a FAM record carries.
     *
     * @param hasDivorce whether the record carries a DIV event
     * @param hasMarriage whether the record carries a MARR event
     * @return the union status
     */
    public static FamilyStatus statusOf(boolean hasDivorce, boolean hasMarriage) {
        if (hasDivorce) {
            return FamilyStatus.DIVORCED;
        }
        return hasMarriage ? FamilyStatus.MARRIED : FamilyStatus.UNKNOWN;
    }
}
