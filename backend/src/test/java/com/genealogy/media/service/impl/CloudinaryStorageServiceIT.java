package com.genealogy.media.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.genealogy.common.config.StorageProperties;
import com.genealogy.media.service.ImageSize;
import com.genealogy.media.service.StoredFile;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;

/** The one integration test against real Cloudinary, so the production path is not never executed (§3.9). */
// Skipped without credentials rather than mocked: a mocked Cloudinary proves only that the mock works.
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_API_SECRET", matches = ".+")
class CloudinaryStorageServiceIT {

    @Test
    @DisplayName("an image survives an upload, both URLs answer with bytes, a download reads it, and a delete ends it")
    void roundTripsAgainstCloudinary() throws Exception {
        StorageProperties properties = new StorageProperties(
                "cloudinary",
                "genealogy-test",
                Duration.ofHours(1),
                null,
                new StorageProperties.Cloudinary(
                        System.getenv("CLOUDINARY_CLOUD_NAME"),
                        System.getenv("CLOUDINARY_API_KEY"),
                        System.getenv("CLOUDINARY_API_SECRET")));
        CloudinaryStorageService storage = new CloudinaryStorageService(properties);
        byte[] png = png();

        StoredFile stored = storage.upload(new ByteArrayResource(png), png.length, FileTypes.PNG);
        try {
            assertThat(stored.storageKey()).isNotBlank();
            // A URL that looks right proves nothing: until 2026-09-28 this test never fetched one (§8.9 #44).
            assertThat(fetch(storage.resolveUrl(stored.storageKey(), ImageSize.ORIGINAL))).isEqualTo(200);
            assertThat(fetch(storage.resolveUrl(stored.storageKey(), ImageSize.THUMBNAIL))).isEqualTo(200);
            Optional<byte[]> small = storage.download(stored.storageKey(), ImageSize.THUMBNAIL);
            assertThat(small).isPresent();
            assertThat(ImageIO.read(new java.io.ByteArrayInputStream(small.get()))).isNotNull();
        } finally {
            storage.delete(stored.storageKey());
        }
    }

    /**
     * Draws a small real PNG, since Cloudinary refuses bytes that are not an image it knows.
     *
     * @return the encoded image
     * @throws IOException never, for an in-memory stream
     */
    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, 40, 30);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * Fetches a URL the way a browser would.
     *
     * @param url the URL
     * @return the HTTP status
     * @throws Exception when the request cannot be sent at all
     */
    private static int fetch(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
