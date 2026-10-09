package com.genealogy.media.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for reading a file's type from its bytes. */
class FileTypesTest {

    /**
     * Encodes text as single bytes, so a magic number can be written as a string.
     *
     * @param text the bytes as Latin-1 text
     * @return the bytes
     */
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("every accepted format is recognised by its magic number")
    void detectsEachFormat() {
        assertThat(FileTypes.detect(bytes("ÿØÿáExif"))).contains(FileTypes.JPEG);
        assertThat(FileTypes.detect(bytes("\u0089PNG\r\n\u001A\n\0\0"))).contains(FileTypes.PNG);
        assertThat(FileTypes.detect(bytes("GIF89a\u0001\0"))).contains(FileTypes.GIF);
        assertThat(FileTypes.detect(bytes("RIFF\u0010\0\0\0WEBPVP8 "))).contains(FileTypes.WEBP);
        assertThat(FileTypes.detect(bytes("\0\0\0\u0018ftypheic\0\0\0\0"))).contains(FileTypes.HEIC);
        assertThat(FileTypes.detect(bytes("\0\0\0\u0018ftypmif1\0\0\0\0"))).contains(FileTypes.HEIC);
        assertThat(FileTypes.detect(bytes("%PDF-1.4\n"))).contains(FileTypes.PDF);
    }

    @Test
    @DisplayName("an SVG, an HTML page, a Windows program and a short file are all refused")
    void refusesEverythingElse() {
        // An SVG served from the storage host can run script there, whatever header it was uploaded with.
        assertThat(FileTypes.detect(bytes("<svg xmlns=\"http"))).isEmpty();
        assertThat(FileTypes.detect(bytes("<!DOCTYPE html>"))).isEmpty();
        assertThat(FileTypes.detect(bytes("MZ\u0090\0\u0003\0"))).isEmpty();
        assertThat(FileTypes.detect(bytes("ÿØ"))).isEmpty();
        assertThat(FileTypes.detect(new byte[0])).isEmpty();
        // An MP4 is an ISO box too, with a brand that is not an image's.
        assertThat(FileTypes.detect(bytes("\0\0\0\u0018ftypisom\0\0\0\0"))).isEmpty();
    }

    @Test
    @DisplayName("only JPEG, PNG and GIF can be a portrait, because the book cannot draw the rest")
    void portraitTypes() {
        assertThat(FileTypes.isPortraitType(FileTypes.JPEG)).isTrue();
        assertThat(FileTypes.isPortraitType(FileTypes.GIF)).isTrue();
        assertThat(FileTypes.isPortraitType(FileTypes.HEIC)).isFalse();
        assertThat(FileTypes.isPortraitType(FileTypes.WEBP)).isFalse();
        assertThat(FileTypes.isPortraitType(FileTypes.PDF)).isFalse();
    }

    @Test
    @DisplayName("a stored object's extension comes from its detected type, never from the client's filename")
    void extensionFollowsTheType() {
        assertThat(FileTypes.extensionOf(FileTypes.JPEG)).isEqualTo(".jpg");
        assertThat(FileTypes.extensionOf(FileTypes.PDF)).isEqualTo(".pdf");
        assertThat(FileTypes.extensionOf("text/html")).isEmpty();
    }
}
