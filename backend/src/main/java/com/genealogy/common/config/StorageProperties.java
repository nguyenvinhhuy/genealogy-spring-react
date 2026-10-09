package com.genealogy.common.config;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage settings bound from {@code app.storage}.
 *
 * @param provider which implementation to run: {@code minio} or {@code cloudinary}
 * @param bucket the bucket or folder every uploaded file lands in
 * @param urlTtl how long a signed URL stays valid, for providers that sign them
 * @param minio MinIO settings, used only by the dev implementation
 * @param cloudinary Cloudinary settings, used only by the production implementation
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String provider,
        String bucket,
        Duration urlTtl,
        Minio minio,
        Cloudinary cloudinary) {

    // Exactly the values the two @ConditionalOnProperty beans match, which compare case-sensitively.
    private static final Set<String> PROVIDERS = Set.of("minio", "cloudinary");

    /**
     * Binds the settings and refuses a provider no implementation answers to, at boot rather than at the first upload.
     *
     * @param provider which implementation to run
     * @param bucket the bucket or folder
     * @param urlTtl how long a signed URL stays valid
     * @param minio MinIO settings
     * @param cloudinary Cloudinary settings
     */
    public StorageProperties {
        // "Cloudinary" or a typo left no StorageService bean at all, and the boot failed naming a missing bean.
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalStateException(
                    "STORAGE_PROVIDER must be minio or cloudinary (lower case), not '" + provider + "'");
        }
    }

    /**
     * MinIO settings.
     *
     * @param endpoint where the backend reaches MinIO, inside the compose network
     * @param publicEndpoint where a browser reaches it, which is a different host and port
     * @param accessKey the access key
     * @param secretKey the secret key
     */
    public record Minio(String endpoint, String publicEndpoint, String accessKey, String secretKey) {

        /**
         * Describes the settings without the secret key.
         *
         * @return the description
         */
        @Override
        public String toString() {
            // A record prints every component, and a logged binding put the key in clear text (§8.10 #15).
            return "Minio[endpoint=" + endpoint + ", publicEndpoint=" + publicEndpoint + ", accessKey=" + accessKey
                    + ", secretKey=***]";
        }
    }

    /**
     * Cloudinary settings.
     *
     * @param cloudName the account's cloud name
     * @param apiKey the API key
     * @param apiSecret the API secret
     */
    public record Cloudinary(String cloudName, String apiKey, String apiSecret) {

        /**
         * Describes the settings without the API secret.
         *
         * @return the description
         */
        @Override
        public String toString() {
            return "Cloudinary[cloudName=" + cloudName + ", apiKey=" + apiKey + ", apiSecret=***]";
        }
    }
}
