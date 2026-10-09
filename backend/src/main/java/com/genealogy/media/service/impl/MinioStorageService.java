package com.genealogy.media.service.impl;

import com.genealogy.common.config.StorageProperties;
import com.genealogy.common.exception.ServiceUnavailableException;
import com.genealogy.media.service.ImageSize;
import com.genealogy.media.service.StorageService;
import com.genealogy.media.service.StoredFile;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

/** {@link StorageService} for development, backed by the MinIO container in docker compose. */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "minio")
public class MinioStorageService implements StorageService {

    // MinIO's default region, set explicitly so the SDK never looks it up over HTTP (§3.9).
    private static final String REGION = "us-east-1";

    // The long edge of a thumbnail: a 320 px tile at twice the pixel density, and a printed portrait.
    private static final int THUMBNAIL_EDGE = 600;

    // The most pixels a header may declare before the thumbnail is skipped: a 600 dpi A3 scan is about 70 million.
    private static final long MAX_DECODED_PIXELS = 150_000_000L;

    private static final String THUMBNAIL_TYPE = "image/jpeg";
    private static final String UNAVAILABLE = "Kho lưu tệp đang không phản hồi. Hãy thử lại sau ít phút.";

    private final StorageProperties properties;
    private final MinioClient client;
    private final MinioClient signingClient;

    /**
     * Builds the two clients this implementation needs.
     *
     * @param properties the storage settings
     */
    public MinioStorageService(StorageProperties properties) {
        this.properties = properties;
        StorageProperties.Minio minio = properties.minio();
        this.client = MinioClient.builder()
                .endpoint(minio.endpoint())
                .region(REGION)
                .credentials(minio.accessKey(), minio.secretKey())
                .build();
        // Signing needs its own client: minio:9000 is not a host a browser resolves, and region must be set.
        this.signingClient = MinioClient.builder()
                .endpoint(minio.publicEndpoint())
                .region(REGION)
                .credentials(minio.accessKey(), minio.secretKey())
                .build();
    }

    /** Creates the bucket on startup when it is missing. */
    @PostConstruct
    void ensureBucket() {
        // §3.9 wanted a `minio-init` container, but MinIO's `mc` image is broken at every tag we can pull.
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                log.info("Created MinIO bucket {}", properties.bucket());
            }
        } catch (Exception failure) {
            // Not fatal: the gia phả works without photos, and refusing to boot over slow storage is worse.
            log.warn("Could not verify the MinIO bucket {}", properties.bucket(), failure);
        }
    }

    /**
     * Stores a file, and a small JPEG copy of it when it is an image javax.imageio can decode.
     *
     * @param content the file, readable more than once
     * @param size how many bytes it has
     * @param contentType the media type read from its bytes
     * @return the keys of the original and of its small copy
     */
    @Override
    public StoredFile upload(InputStreamSource content, long size, String contentType) {
        // Two people will upload "scan.jpg" on the same afternoon, so the name is never the key.
        String key = UUID.randomUUID() + FileTypes.extensionOf(contentType);
        try (InputStream stream = content.getInputStream()) {
            put(key, stream, size, contentType);
        } catch (Exception failure) {
            log.warn("Could not store {} in MinIO", key, failure);
            throw new ServiceUnavailableException(UNAVAILABLE, failure);
        }
        // MinIO sizes nothing on the fly, so the small copy is made here once rather than on every view (§8.9 D3).
        String thumbnailKey = thumbnail(content, contentType)
                .map(bytes -> storeThumbnail(key, bytes))
                .orElse(null);
        return new StoredFile(key, thumbnailKey);
    }

    /**
     * Removes a stored file.
     *
     * @param storageKey the object key
     */
    @Override
    public void delete(String storageKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .build());
        } catch (Exception failure) {
            // The row is already going: an orphaned object costs disk, a raised failure costs the action.
            log.warn("Could not remove {} from MinIO", storageKey, failure);
        }
    }

    /**
     * Returns a short-lived signed URL a browser can fetch the file from.
     *
     * @param storageKey the object key
     * @param size ignored: MinIO serves what was stored, and the small copy has a key of its own
     * @return the signed URL
     */
    @Override
    public String resolveUrl(String storageKey, ImageSize size) {
        try {
            return signingClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(properties.bucket())
                    .object(storageKey)
                    .expiry((int) properties.urlTtl().toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception failure) {
            log.warn("Could not sign a URL for {}", storageKey, failure);
            throw new ServiceUnavailableException(UNAVAILABLE, failure);
        }
    }

    /**
     * Reads a stored object back as bytes.
     *
     * @param storageKey the object key
     * @param size ignored, as for {@link #resolveUrl}
     * @return the bytes, or empty when the object is gone or unreadable
     */
    @Override
    public Optional<byte[]> download(String storageKey, ImageSize size) {
        // Through the internal client, never the signed URL: that host is signed for a browser, not for us.
        try (InputStream stream = client.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(storageKey)
                .build())) {
            return Optional.of(stream.readAllBytes());
        } catch (Exception failure) {
            // A missing portrait must not stop the book: the rest of the gia phả is still worth printing.
            log.warn("Could not read {} from MinIO", storageKey, failure);
            return Optional.empty();
        }
    }

    /**
     * Writes one object.
     *
     * @param key the object key
     * @param stream its bytes
     * @param size how many there are
     * @param contentType its media type
     * @throws Exception whatever the SDK throws
     */
    private void put(String key, InputStream stream, long size, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(key)
                .stream(stream, size, -1L)
                .contentType(contentType)
                .build());
    }

    /**
     * Stores a thumbnail next to its original.
     *
     * @param key the original's key
     * @param bytes the thumbnail's JPEG bytes
     * @return the thumbnail's key, or null when it could not be stored
     */
    private String storeThumbnail(String key, byte[] bytes) {
        String thumbnailKey = key + "-thumb.jpg";
        try (InputStream stream = new ByteArrayInputStream(bytes)) {
            put(thumbnailKey, stream, bytes.length, THUMBNAIL_TYPE);
            return thumbnailKey;
        } catch (Exception failure) {
            // A gallery can fall back to the original; losing the upload over its thumbnail would be worse.
            log.warn("Could not store the thumbnail of {}", key, failure);
            return null;
        }
    }

    /**
     * Scales an image down to a JPEG that fits {@link #THUMBNAIL_EDGE}.
     *
     * @param content the original
     * @param contentType its media type
     * @return the JPEG bytes, or empty when the type cannot be decoded here
     */
    private static Optional<byte[]> thumbnail(InputStreamSource content, String contentType) {
        if (!FileTypes.isScalable(contentType)) {
            return Optional.empty();
        }
        try (InputStream stream = content.getInputStream();
                ImageInputStream imageStream = ImageIO.createImageInputStream(stream)) {
            BufferedImage original = decodeSubsampled(imageStream);
            if (original == null) {
                return Optional.empty();
            }
            double scale = Math.min(1.0, (double) THUMBNAIL_EDGE / Math.max(original.getWidth(), original.getHeight()));
            int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
            // RGB, not the source's model: JPEG has no alpha, and a transparent PNG would come out black.
            BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = small.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(original, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(small, "jpg", out);
            return Optional.of(out.toByteArray());
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not make a thumbnail of a {} upload", contentType, failure);
            return Optional.empty();
        }
    }

    /**
     * Decodes an image at a fraction of its size.
     *
     * @param imageStream the image bytes
     * @return the decoded image, or null when no reader takes it or it is too large to decode here
     * @throws IOException when the bytes cannot be read
     */
    private static BufferedImage decodeSubsampled(ImageInputStream imageStream) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        try {
            reader.setInput(imageStream, true, true);
            long width = reader.getWidth(0);
            long height = reader.getHeight(0);
            // The declared size is read from the header before any pixel: a tiny PNG can claim 50,000 square.
            if (width * height > MAX_DECODED_PIXELS) {
                log.warn("Skipped the thumbnail of a {}x{} image: too large to decode", width, height);
                return null;
            }
            int step = (int) Math.max(1, Math.max(width, height) / THUMBNAIL_EDGE);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(step, step, 0, 0);
            return reader.read(0, param);
        } finally {
            reader.dispose();
        }
    }
}
