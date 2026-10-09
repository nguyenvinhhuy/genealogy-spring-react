package com.genealogy.media.service;

/**
 * What storing an uploaded file left behind.
 *
 * @param storageKey the key of the original, opaque outside the provider
 * @param thumbnailKey the key of a stored small copy, or null when the provider sizes on the fly or cannot scale it
 */
public record StoredFile(String storageKey, String thumbnailKey) {
}
