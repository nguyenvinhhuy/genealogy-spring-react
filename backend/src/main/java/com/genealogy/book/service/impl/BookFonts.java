package com.genealogy.book.service.impl;

import com.genealogy.common.exception.InternalException;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads the embedded Unicode fonts the book is set in. */
@Component
class BookFonts {

    private static final String REGULAR = "fonts/DejaVuSans.ttf";
    private static final String BOLD = "fonts/DejaVuSans-Bold.ttf";

    private final byte[] regularBytes = read(REGULAR);
    private final byte[] boldBytes = read(BOLD);

    /**
     * Creates the body font for one document.
     *
     * @return the regular font
     */
    PdfFont regular() {
        return create(regularBytes);
    }

    /**
     * Creates the heading font for one document.
     *
     * @return the bold font
     */
    PdfFont bold() {
        return create(boldBytes);
    }

    /**
     * Builds a font embedded with full Unicode encoding.
     *
     * @param bytes the TrueType file
     * @return the font
     */
    private static PdfFont create(byte[] bytes) {
        try {
            // IDENTITY_H plus an embedded TTF: the built-in Helvetica is WinAnsi and drops ế, ộ and ữ.
            return PdfFontFactory.createFont(
                    bytes, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
        } catch (IOException failure) {
            // An ApiException, or GlobalExceptionHandler replaces this message with "Unexpected server error".
            throw new InternalException("Không dựng được font cho sách gia phả", failure);
        }
    }

    /**
     * Reads a font file out of the packaged resources.
     *
     * @param path the classpath location
     * @return the file contents
     */
    private static byte[] read(String path) {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return stream.readAllBytes();
        } catch (IOException failure) {
            // The font ships inside the jar; missing it means a broken build, not a runtime condition.
            throw new UncheckedIOException("Thiếu font " + path + " trong gói ứng dụng", failure);
        }
    }
}
