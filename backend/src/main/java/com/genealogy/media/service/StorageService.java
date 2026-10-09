package com.genealogy.media.service;

import java.util.Optional;
import org.springframework.core.io.InputStreamSource;

/** The only door to object storage (CLAUDE.md §3.9), which Cloudinary answers. */
// Narrow on purpose: the smaller this surface, the less of a provider's behaviour leaks into the features.
public interface StorageService {

    /**
     * Stores a file, and a small copy of it where the provider cannot size one on the fly.
     *
     * @param content the file, readable more than once
     * @param size how many bytes it has
     * @param contentType the media type read from its bytes
     * @return the keys the provider will recognise the original and its small copy by
     */
    StoredFile upload(InputStreamSource content, long size, String contentType);

    /**
     * Removes a stored file.
     *
     * @param storageKey a key returned by {@link #upload}
     */
    void delete(String storageKey);

    /**
     * Returns a URL a browser can fetch the file from, at the size asked for where the provider can size it.
     *
     * @param storageKey a key returned by {@link #upload}
     * @param size which copy is wanted
     * @return the URL; short-lived for providers that sign them
     */
    String resolveUrl(String storageKey, ImageSize size);

    /**
     * Reads a stored file back, for a server-side consumer that needs the bytes rather than a link.
     *
     * @param storageKey a key returned by {@link #upload}
     * @param size which copy is wanted
     * @return the file bytes, or empty when the object is gone or unreadable
     */
    // The book embeds portraits in a PDF; fetching its own signed URL would hit the host problem in §3.9.
    Optional<byte[]> download(String storageKey, ImageSize size);
}
