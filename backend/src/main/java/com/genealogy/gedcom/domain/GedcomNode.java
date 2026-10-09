package com.genealogy.gedcom.domain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;

/** One GEDCOM record line with the lines nested under it. */
@Getter
public final class GedcomNode {

    // A payload that really is a cross-reference pointer, such as @I3@.
    private static final Pattern POINTER = Pattern.compile("@[A-Za-z0-9_]+@");

    private final String xref;
    private final String tag;
    private final List<GedcomNode> children = new ArrayList<>();
    private String value;

    // Continuations accumulate here during parse; appending to `value` copied the whole payload per line.
    @Getter(AccessLevel.NONE)
    private StringBuilder folding;

    /**
     * Creates a node.
     *
     * @param xref the cross-reference identifier without at-signs, or null
     * @param tag the GEDCOM tag
     * @param value the payload, or null when the tag carries none
     */
    private GedcomNode(String xref, String tag, String value) {
        this.xref = xref;
        this.tag = tag;
        this.value = value;
    }

    /**
     * Creates a node with no cross-reference identifier.
     *
     * @param tag the GEDCOM tag
     * @param value the payload, or null when the tag carries none
     * @return the node
     */
    public static GedcomNode of(String tag, String value) {
        return new GedcomNode(null, tag, value);
    }

    /**
     * Creates a top-level node identified by a cross-reference.
     *
     * @param xref the identifier, without the surrounding at-signs
     * @param tag the GEDCOM tag
     * @return the node
     */
    public static GedcomNode record(String xref, String tag) {
        return new GedcomNode(xref, tag, null);
    }

    /**
     * Adds a child line and returns this node so calls can be chained.
     *
     * @param child the line to nest, or null to add nothing
     * @return this node
     */
    public GedcomNode with(GedcomNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    /**
     * Adds a child line built from a tag and value, skipping it when the value is absent.
     *
     * @param tag the GEDCOM tag
     * @param value the payload, or null/blank to add nothing
     * @return this node
     */
    public GedcomNode with(String tag, String value) {
        return (value == null || value.isBlank()) ? this : with(of(tag, value));
    }

    /**
     * Returns the first child carrying a tag.
     *
     * @param childTag the GEDCOM tag to look for
     * @return the child, or empty when absent
     */
    public Optional<GedcomNode> child(String childTag) {
        return children.stream().filter(node -> node.tag.equals(childTag)).findFirst();
    }

    /**
     * Returns the value of the first child carrying a tag.
     *
     * @param childTag the GEDCOM tag to look for
     * @return the value, or empty when the child or its value is absent
     */
    public Optional<String> childValue(String childTag) {
        return child(childTag).map(GedcomNode::getValue).filter(text -> !text.isBlank());
    }

    /**
     * Returns every child carrying a tag.
     *
     * @param childTag the GEDCOM tag to look for
     * @return the matching children, in file order
     */
    public List<GedcomNode> childrenNamed(String childTag) {
        return children.stream().filter(node -> node.tag.equals(childTag)).toList();
    }

    /**
     * Renders this node and its descendants as GEDCOM lines.
     *
     * @param level the level number this node sits at
     * @param out the buffer to append to
     */
    public void render(int level, StringBuilder out) {
        out.append(level);
        if (xref != null) {
            out.append(" @").append(xref).append('@');
        }
        out.append(' ').append(tag);
        if (value == null || value.isBlank()) {
            out.append('\n');
        } else {
            String[] lines = value.split("\n", -1);
            out.append(' ').append(escapePayload(lines[0])).append('\n');
            // A payload may not contain a raw newline, so every further line becomes its own CONT line.
            for (int i = 1; i < lines.length; i++) {
                out.append(level + 1).append(" CONT ").append(escapePayload(lines[i])).append('\n');
            }
        }
        for (GedcomNode child : children) {
            child.render(level + 1, out);
        }
    }

    /**
     * Escapes a payload whose first character would otherwise open a cross-reference pointer.
     *
     * @param text the payload line
     * @return the line, with a leading at-sign doubled
     */
    private static String escapePayload(String text) {
        // A note starting "@Nhà thờ họ" is a malformed pointer to every conforming reader, and gets dropped.
        return text.startsWith("@") && !POINTER.matcher(text).matches() ? "@" + text : text;
    }

    /**
     * Parses a whole GEDCOM file into its top-level records.
     *
     * @param text the file contents
     * @return the level-0 records, in file order
     */
    public static List<GedcomNode> parse(String text) {
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            return parse(reader);
        } catch (IOException impossible) {
            throw new IllegalStateException("Reading a string cannot fail", impossible);
        }
    }

    /**
     * Parses a GEDCOM file into its top-level records, a line at a time.
     *
     * @param reader the file contents
     * @return the level-0 records, in file order
     * @throws IOException if the reader fails
     */
    public static List<GedcomNode> parse(BufferedReader reader) throws IOException {
        // Line by line, because splitting the whole file held the text, an array of every line and a stripped copy.
        List<GedcomNode> roots = new ArrayList<>();
        // Index i holds the node currently open at level i, so a line at level n attaches to the node at n - 1.
        List<GedcomNode> open = new ArrayList<>();
        List<GedcomNode> folded = new ArrayList<>();

        boolean first = true;
        for (String rawLine = reader.readLine(); rawLine != null; rawLine = reader.readLine()) {
            // GEDCOM 7 permits a leading BOM, and String.strip() does not remove it: U+FEFF is not whitespace.
            if (first && rawLine.startsWith("﻿")) {
                rawLine = rawLine.substring(1);
            }
            first = false;

            ParsedLine parsed = ParsedLine.parse(rawLine.strip());
            if (parsed == null) {
                continue;
            }
            if (parsed.isContinuation()) {
                appendContinuation(open, parsed, folded);
                continue;
            }

            while (open.size() > parsed.level()) {
                open.removeLast();
            }
            GedcomNode node = new GedcomNode(parsed.xref(), parsed.tag(), parsed.value());
            if (open.isEmpty()) {
                roots.add(node);
            } else {
                open.getLast().children.add(node);
            }
            open.add(node);
        }
        for (GedcomNode node : folded) {
            node.value = node.folding.toString();
            node.folding = null;
        }
        return roots;
    }

    /**
     * Folds a CONT or CONC line back into the value of the line it continues.
     *
     * @param open the currently open nodes
     * @param parsed the continuation line
     * @param folded every node that has received a continuation, to be materialised once at the end
     */
    private static void appendContinuation(List<GedcomNode> open, ParsedLine parsed, List<GedcomNode> folded) {
        if (open.isEmpty()) {
            return;
        }
        // Floored at 0: a continuation at level 0 is malformed, and the raw index would be -1.
        int index = Math.max(0, Math.min(parsed.level(), open.size()) - 1);
        GedcomNode target = open.get(index);
        if (target.folding == null) {
            target.folding = new StringBuilder(target.value == null ? "" : target.value);
            folded.add(target);
        }
        // CONT restores a line break, CONC joins with nothing; 5.5.1 files use both, 7.0 dropped CONC.
        target.folding
                .append("CONT".equals(parsed.tag()) ? "\n" : "")
                .append(parsed.value() == null ? "" : parsed.value());
    }

    /**
     * One GEDCOM line split into its parts.
     *
     * @param level the level number
     * @param xref the cross-reference identifier without at-signs, or null
     * @param tag the GEDCOM tag
     * @param value the payload, or null
     */
    private record ParsedLine(int level, String xref, String tag, String value) {

        /**
         * Reports whether this line continues the value of the line above it.
         *
         * @return true for CONT and CONC
         */
        boolean isContinuation() {
            return "CONT".equals(tag) || "CONC".equals(tag);
        }

        /**
         * Splits one GEDCOM line into level, optional cross-reference, tag and payload.
         *
         * @param line the line, already stripped of surrounding whitespace
         * @return the parts, or null when the line is not valid GEDCOM
         */
        static ParsedLine parse(String line) {
            int firstSpace = line.indexOf(' ');
            if (firstSpace < 0) {
                return null;
            }
            int level;
            try {
                level = Integer.parseInt(line.substring(0, firstSpace));
            } catch (NumberFormatException ignored) {
                return null;
            }
            // A negative level parses fine and then unwinds `open` past empty; no GEDCOM line has one.
            if (level < 0) {
                return null;
            }

            String rest = line.substring(firstSpace + 1).stripLeading();
            String xref = null;
            if (rest.startsWith("@")) {
                int close = rest.indexOf('@', 1);
                if (close < 0) {
                    return null;
                }
                xref = rest.substring(1, close);
                rest = rest.substring(close + 1).stripLeading();
            }

            int tagEnd = rest.indexOf(' ');
            if (tagEnd < 0) {
                return rest.isEmpty() ? null : new ParsedLine(level, xref, rest, null);
            }
            String value = rest.substring(tagEnd + 1);
            // `@@` is GEDCOM's escape for a payload that really starts with an at-sign, not a pointer.
            return new ParsedLine(
                    level, xref, rest.substring(0, tagEnd), value.startsWith("@@") ? value.substring(1) : value);
        }
    }
}
