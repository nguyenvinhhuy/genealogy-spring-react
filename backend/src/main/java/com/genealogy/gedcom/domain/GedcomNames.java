package com.genealogy.gedcom.domain;

import com.genealogy.common.model.PersonNameType;
import com.genealogy.common.util.Blank;
import com.genealogy.person.dto.request.PersonNameRequest;
import com.genealogy.person.dto.response.PersonNameResponse;
import java.util.Arrays;
import java.util.Locale;

/** Converts between a Vietnamese {@code person_name} and a GEDCOM {@code NAME} payload. */
public final class GedcomNames {

    /** Not instantiable. */
    private GedcomNames() {
    }

    /**
     * Renders a name in the given-first, surname-in-slashes form GEDCOM requires.
     *
     * @param name the name to render
     * @return the NAME payload
     */
    public static String format(PersonNameResponse name) {
        // Given-first is GEDCOM's wire format only; the app never reorders a name for display (§5.2).
        StringBuilder builder = new StringBuilder();
        if (name.middleName() != null && !name.middleName().isBlank()) {
            builder.append(name.middleName()).append(' ');
        }
        builder.append(name.givenName());
        // Slashes written even when empty: without them a re-import reads "Văn An" as họ Văn, tên An.
        String surname = name.surname() == null ? "" : name.surname().strip();
        return builder.append(" /").append(surname).append('/').toString();
    }

    /**
     * Reads a GEDCOM NAME payload back into Vietnamese name parts.
     *
     * @param payload the NAME payload, or null
     * @param type which kind of name this is
     * @param primary whether this is the person's main name
     * @return the parsed name, or null when it carries no given name at all
     */
    public static PersonNameRequest parse(String payload, PersonNameType type, boolean primary) {
        return parse(payload, null, null, type, primary);
    }

    /**
     * Reads a GEDCOM NAME payload and its GIVN/SURN subtags back into Vietnamese name parts.
     *
     * @param payload the NAME payload, or null
     * @param givn the GIVN subtag, or null
     * @param surn the SURN subtag, or null
     * @param type which kind of name this is
     * @param primary whether this is the person's main name
     * @return the parsed name, or null when nothing names a given name
     */
    public static PersonNameRequest parse(
            String payload, String givn, String surn, PersonNameType type, boolean primary) {
        PersonNameRequest fromPayload = fromPayload(payload, type, primary);
        String given = Blank.toNull(givn);
        String surname = Blank.toNull(surn);
        if (given == null && surname == null) {
            return fromPayload;
        }
        // Subtags win: a structured exporter writes `1 NAME //` and puts the real parts only in GIVN and SURN.
        if (fromPayload == null) {
            return given == null
                    ? new PersonNameRequest(type, null, null, surname, primary)
                    : new PersonNameRequest(type, surname, null, given, primary);
        }
        return new PersonNameRequest(
                type,
                surname != null ? surname : fromPayload.surname(),
                fromPayload.middleName(),
                given != null ? given : fromPayload.givenName(),
                primary);
    }

    /**
     * Reads the NAME payload alone into Vietnamese name parts.
     *
     * @param payload the NAME payload, or null
     * @param type which kind of name this is
     * @param primary whether this is the person's main name
     * @return the parsed name, or null when it carries no given name at all
     */
    private static PersonNameRequest fromPayload(String payload, PersonNameType type, boolean primary) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String text = payload.strip();
        String surname = null;
        int open = text.indexOf('/');
        boolean slashed = open >= 0;
        if (open >= 0) {
            int close = text.indexOf('/', open + 1);
            surname = Blank.toNull(close < 0 ? text.substring(open + 1) : text.substring(open + 1, close));
            String before = text.substring(0, open);
            String after = close < 0 ? "" : text.substring(close + 1);
            text = (before + " " + after).strip();
        }

        String[] tokens = text.isBlank() ? new String[0] : text.split("\\s+");
        if (tokens.length == 0) {
            // Slashes but nothing outside them: the file knows only a surname, which has to serve as the name.
            return surname == null ? null : new PersonNameRequest(type, null, null, surname, primary);
        }
        // Guarded on `slashed`: empty slashes say "this person has no họ", which is not the same as saying nothing.
        if (surname == null && !slashed && tokens.length > 1) {
            // No slashes means a Vietnamese name typed surname-first; Western order would swap họ and tên.
            surname = tokens[0];
            tokens = Arrays.copyOfRange(tokens, 1, tokens.length);
        }

        String given = tokens[tokens.length - 1];
        String middle = tokens.length > 1 ? String.join(" ", Arrays.copyOf(tokens, tokens.length - 1)) : null;
        return new PersonNameRequest(type, surname, Blank.toNull(middle), given, primary);
    }

    /**
     * Returns the GEDCOM 7 NAME.TYPE value that comes closest to a Vietnamese name kind.
     *
     * @param type the Vietnamese name kind
     * @return a value from GEDCOM 7's enumeration
     */
    public static String gedcomTypeOf(PersonNameType type) {
        // OTHER for everything the enumset cannot name; the exact kind is written beside it as a PHRASE.
        return switch (type) {
            case BIRTH -> "BIRTH";
            case ALIAS -> "AKA";
            default -> "OTHER";
        };
    }

    /**
     * Maps a GEDCOM name type and its phrase to the Vietnamese name kind they match.
     *
     * @param gedcomType the TYPE payload, or null
     * @param phrase the TYPE PHRASE payload, or null
     * @return the matching kind, BIRTH when nothing better fits
     */
    public static PersonNameType typeOf(String gedcomType, String phrase) {
        // The phrase is read first: it is the only place tên húy survives a conformant GEDCOM 7 round trip.
        if (phrase != null && !phrase.isBlank()) {
            return vietnameseType(phrase.strip());
        }
        return typeOf(gedcomType);
    }

    /**
     * Maps a GEDCOM name type to the Vietnamese name kind it matches.
     *
     * @param gedcomType the TYPE payload, or null
     * @return the matching kind, BIRTH when nothing better fits
     */
    public static PersonNameType typeOf(String gedcomType) {
        if (gedcomType == null || gedcomType.isBlank()) {
            return PersonNameType.BIRTH;
        }
        return switch (gedcomType.strip().toUpperCase(Locale.ROOT)) {
            // RELIGIOUS is not a PersonNameType, so without this arm the default would demote tên thánh to ALIAS.
            case "RELIGIOUS" -> PersonNameType.SAINT;
            // Our own export writes the Vietnamese kind verbatim, so a round trip keeps tên húy as tên húy.
            default -> vietnameseType(gedcomType.strip());
        };
    }

    /**
     * Reads a name kind our own export wrote, falling back to an alias.
     *
     * @param gedcomType the TYPE payload
     * @return the matching kind, ALIAS when unrecognised
     */
    private static PersonNameType vietnameseType(String gedcomType) {
        try {
            return PersonNameType.valueOf(gedcomType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PersonNameType.ALIAS;
        }
    }
}
