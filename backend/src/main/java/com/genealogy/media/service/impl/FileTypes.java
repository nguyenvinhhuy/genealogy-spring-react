package com.genealogy.media.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reads what a file really is from its first bytes, instead of trusting the type the browser declared. */
// A header is the client's claim: an SVG or an HTML file declared image/png was stored and served as one (§8.9 #8).
public final class FileTypes {

    // How many leading bytes the checks below look at.
    public static final int HEAD_BYTES = 16;

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";
    public static final String HEIC = "image/heic";
    public static final String PDF = "application/pdf";

    // What a browser and iText can both draw, so a portrait shows on the page and in the book alike.
    private static final Set<String> PORTRAIT_TYPES = Set.of(JPEG, PNG, GIF);

    // What javax.imageio decodes without a plugin, so the only types a thumbnail can be made from here.
    private static final Set<String> SCALABLE_TYPES = Set.of(JPEG, PNG, GIF);

    private static final Map<String, String> EXTENSIONS = Map.of(
            JPEG, ".jpg", PNG, ".png", GIF, ".gif", WEBP, ".webp", HEIC, ".heic", PDF, ".pdf");

    // The ISO base media brands an iPhone photo carries after "ftyp".
    private static final Set<String> HEIC_BRANDS =
            Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    /** Not instantiable. */
    private FileTypes() {
    }

    /**
     * Works out a file's media type from its first bytes.
     *
     * @param head the file's leading bytes, as many as there are up to {@link #HEAD_BYTES}
     * @return the media type, or empty when the bytes are not one this app accepts
     */
    public static Optional<String> detect(byte[] head) {
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (ascii(head, 0, 6).equals("GIF87a") || ascii(head, 0, 6).equals("GIF89a")) {
            return Optional.of(GIF);
        }
        if (ascii(head, 0, 4).equals("RIFF") && ascii(head, 8, 4).equals("WEBP")) {
            return Optional.of(WEBP);
        }
        if (ascii(head, 4, 4).equals("ftyp") && HEIC_BRANDS.contains(ascii(head, 8, 4))) {
            return Optional.of(HEIC);
        }
        if (ascii(head, 0, 5).equals("%PDF-")) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    /**
     * Reports whether a file of this type can be a person's portrait.
     *
     * @param type the media type
     * @return true for the types both a browser and the book can draw
     */
    public static boolean isPortraitType(String type) {
        return PORTRAIT_TYPES.contains(type);
    }

    /**
     * Reports whether a thumbnail can be made from a file of this type without an image plugin.
     *
     * @param type the media type
     * @return true when javax.imageio can decode it
     */
    public static boolean isScalable(String type) {
        return SCALABLE_TYPES.contains(type);
    }

    /**
     * Returns the file extension a stored object of this type is given.
     *
     * @param type the media type
     * @return the extension with its dot, or an empty string for an unknown type
     */
    public static String extensionOf(String type) {
        // From the detected type, never the client's filename: "a./../x.jpg" once became part of a key (#44).
        return EXTENSIONS.getOrDefault(type, "");
    }

    /**
     * Reports whether bytes begin with the given values.
     *
     * @param head the bytes
     * @param expected the leading values, each 0-255
     * @return true when every expected byte matches
     */
    private static boolean startsWith(byte[] head, int... expected) {
        if (head.length < expected.length) {
            return false;
        }
        for (int at = 0; at < expected.length; at++) {
            if ((head[at] & 0xFF) != expected[at]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a run of bytes as ASCII text.
     *
     * @param head the bytes
     * @param from where the run starts
     * @param length how long it is
     * @return the text, or an empty string when the bytes are too short
     */
    private static String ascii(byte[] head, int from, int length) {
        if (head.length < from + length) {
            return "";
        }
        return new String(Arrays.copyOfRange(head, from, from + length), StandardCharsets.US_ASCII);
    }
}
