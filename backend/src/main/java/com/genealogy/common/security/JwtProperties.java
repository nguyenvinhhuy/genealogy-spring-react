package com.genealogy.common.security;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings bound from {@code app.jwt}.
 *
 * @param secret HMAC signing key, at least 32 bytes and never a published example
 * @param accessTokenTtl lifetime of an access token
 * @param refreshTokenTtl lifetime of a refresh token
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

    // Secrets that are public: the old application.yml fallback and the value .env.example ships with.
    private static final Set<String> PUBLISHED_SECRETS = Set.of(
            "dev-only-secret-change-me-at-least-32-bytes-long",
            "change-me-to-a-long-random-string-at-least-32-bytes");

    /**
     * Binds the settings and refuses a signing key or a lifetime that would not work.
     *
     * @param secret HMAC signing key
     * @param accessTokenTtl lifetime of an access token
     * @param refreshTokenTtl lifetime of a refresh token
     */
    public JwtProperties {
        // Failing at boot, not at first login: a published secret lets anyone mint an ADMIN token.
        if (secret == null || secret.isBlank() || PUBLISHED_SECRETS.contains(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set to a private value, not the example from the repository."
                            + " Generate one with: openssl rand -base64 48");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256");
        }
        // A missing lifetime booted fine and failed every login with a NullPointerException (§8.10 #21).
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            throw new IllegalStateException("app.jwt.access-token-ttl and refresh-token-ttl must both be set");
        }
    }

    /**
     * Describes the settings without the signing key.
     *
     * @return the description
     */
    @Override
    public String toString() {
        // A record prints every component, and a logged binding put the key in clear text (§8.10 #15).
        return "JwtProperties[secret=***, accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl=" + refreshTokenTtl
                + "]";
    }
}
