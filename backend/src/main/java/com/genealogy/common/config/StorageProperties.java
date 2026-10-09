package com.genealogy.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage settings bound from {@code app.storage}.
 *
 * @param bucket the Cloudinary folder every uploaded file lands in
 * @param urlTtl how long a signed URL stays valid, for providers that sign them
 * @param cloudinary Cloudinary settings
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String bucket, Duration urlTtl, Cloudinary cloudinary) {

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
