package com.genealogy.media.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.genealogy.common.config.StorageProperties;
import com.genealogy.common.exception.ServiceUnavailableException;
import com.genealogy.media.service.ImageSize;
import com.genealogy.media.service.StorageService;
import com.genealogy.media.service.StoredFile;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

/** {@link StorageService} for production, backed by Cloudinary and its on-the-fly image transforms. */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    // Every accepted type, PDF included, is an "image" to Cloudinary; one resource type keeps url and destroy right.
    private static final String RESOURCE_TYPE = "image";

    // The only formats Cloudinary will store, so a file that got past the byte check is still refused there.
    private static final String ALLOWED_FORMATS = "jpg,png,gif,webp,heic,pdf";

    // The long edge of a thumbnail, the same as MinIO's stored copies.
    private static final int THUMBNAIL_EDGE = 600;

    // A CDN read that stalls must fail, or it holds a book render's thread for ever (§8.9 #13).
    private static final int TIMEOUT_MS = (int) Duration.ofSeconds(20).toMillis();

    private static final String UNAVAILABLE = "Kho lưu tệp đang không phản hồi. Hãy thử lại sau ít phút.";

    // Where every uploaded file lands, so one account can host more than this app.
    private final String folder;

    private final Cloudinary cloudinary;

    /**
     * Builds the Cloudinary client from the configured credentials.
     *
     * @param properties the storage settings
     */
    public CloudinaryStorageService(StorageProperties properties) {
        StorageProperties.Cloudinary settings = properties.cloudinary();
        this.folder = properties.bucket();
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", settings.cloudName(),
                "api_key", settings.apiKey(),
                "api_secret", settings.apiSecret(),
                "secure", true));
    }

    /**
     * Stores a file and returns the public id Cloudinary will recognise it by.
     *
     * @param content the file, readable more than once
     * @param size how many bytes it has
     * @param contentType the media type read from its bytes
     * @return the public id; no stored small copy, since Cloudinary sizes on the fly
     */
    @Override
    public StoredFile upload(InputStreamSource content, long size, String contentType) {
        Map<?, ?> result;
        try (InputStream stream = content.getInputStream()) {
            result = cloudinary.uploader().upload(stream.readAllBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", RESOURCE_TYPE,
                    "allowed_formats", ALLOWED_FORMATS,
                    "unique_filename", true,
                    "overwrite", false));
        } catch (Exception failure) {
            log.warn("Could not store a {} upload in Cloudinary", contentType, failure);
            throw new ServiceUnavailableException(UNAVAILABLE, failure);
        }
        Object publicId = result.get("public_id");
        // String.valueOf would store the literal "null" as the key, and the real object is then lost.
        if (publicId == null) {
            log.warn("Cloudinary answered an upload with no public_id: {}", result);
            throw new ServiceUnavailableException(UNAVAILABLE, null);
        }
        return new StoredFile(publicId.toString(), null);
    }

    /**
     * Removes a stored file.
     *
     * @param storageKey the public id
     */
    @Override
    public void delete(String storageKey) {
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(storageKey, ObjectUtils.asMap(
                    "invalidate", true, "resource_type", RESOURCE_TYPE));
            // Cloudinary reports a miss in the body with HTTP 200, so the catch below never sees that failure.
            String outcome = String.valueOf(result.get("result"));
            if (!"ok".equals(outcome)) {
                log.warn("Cloudinary did not remove {}: result was {}", storageKey, outcome);
            }
        } catch (Exception failure) {
            log.warn("Could not remove {} from Cloudinary", storageKey, failure);
        }
    }

    /**
     * Returns the CDN URL of a stored file, scaled by a transform when a thumbnail is asked for.
     *
     * @param storageKey the public id
     * @param size which copy is wanted
     * @return the URL
     */
    @Override
    public String resolveUrl(String storageKey, ImageSize size) {
        // Unsigned and long-lived, unlike MinIO's: §3.6 is applied before this is ever handed out.
        if (size == ImageSize.ORIGINAL) {
            return cloudinary.url().secure(true).resourceType(RESOURCE_TYPE).generate(storageKey);
        }
        // JPEG, so a PDF scan's first page and a HEIC photo both come back as something a browser draws.
        return cloudinary.url().secure(true).resourceType(RESOURCE_TYPE)
                .transformation(new Transformation<>().width(THUMBNAIL_EDGE).height(THUMBNAIL_EDGE).crop("limit"))
                .format("jpg")
                .generate(storageKey);
    }

    /**
     * Reads a stored object back as bytes.
     *
     * @param storageKey the public id
     * @param size which copy is wanted
     * @return the bytes, or empty when the object is gone, unreadable or too slow to answer
     */
    @Override
    public Optional<byte[]> download(String storageKey, ImageSize size) {
        // Over HTTP, because Cloudinary is a CDN with no server-side read API — and its URL needs no signing.
        try {
            URLConnection connection = URI.create(resolveUrl(storageKey, size)).toURL().openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            try (InputStream stream = connection.getInputStream()) {
                return Optional.of(stream.readAllBytes());
            }
        } catch (Exception failure) {
            // A missing portrait must not stop the book: the rest of the gia phả is still worth printing.
            log.warn("Could not read {} from Cloudinary", storageKey, failure);
            return Optional.empty();
        }
    }
}
