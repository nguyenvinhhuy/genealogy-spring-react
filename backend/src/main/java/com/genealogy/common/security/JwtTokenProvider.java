package com.genealogy.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Issues and verifies the stateless access tokens. */
@Slf4j
@Component
public class JwtTokenProvider {

    // Bound to this app: a secret reused by the sibling charity app must not make its tokens ours (§8.10 #11).
    static final String ISSUER = "genealogy";
    static final String AUDIENCE = "genealogy-api";

    // Only an access token authenticates a request; a future invite or reset link must not pass as one.
    static final String TOKEN_TYPE = "access";

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_CREDENTIAL_VERSION = "cv";

    private final SecretKey key;
    private final JwtParser parser;
    private final JwtProperties properties;

    /**
     * Derives the signing key from the configured secret and builds the one parser every request shares.
     *
     * @param properties the JWT settings
     */
    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        // Built once: a JwtParser is immutable and thread-safe, and was rebuilt on every request.
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .require(CLAIM_TYPE, TOKEN_TYPE)
                .build();
    }

    /**
     * Issues a signed access token for an account, bound to its current credential version.
     *
     * @param access the account as it is now
     * @return the compact JWT
     */
    public String createAccessToken(MemberAccess access) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(String.valueOf(access.id()))
                .claim(CLAIM_TYPE, TOKEN_TYPE)
                .claim(CLAIM_CREDENTIAL_VERSION, access.credentialVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies a token and reads whose it is, or returns null when it is not one of ours and current.
     *
     * @param token the compact JWT
     * @return the claims, or null if the token is missing, expired, foreign or tampered with
     */
    public AccessClaims parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            // The role is not read from here any more: the filter takes it from the database (§8.10 #10).
            Integer version = claims.get(CLAIM_CREDENTIAL_VERSION, Integer.class);
            if (claims.getSubject() == null || version == null) {
                log.debug("Rejected access token: it carries no subject or no credential version");
                return null;
            }
            return new AccessClaims(Long.valueOf(claims.getSubject()), version);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected access token: {}", ex.getMessage());
            return null;
        }
    }
}
