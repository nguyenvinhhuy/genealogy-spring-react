package com.genealogy.common.security;

/**
 * What a verified access token claims: whose it is and which credential version it was issued under.
 *
 * @param memberId the member id in the subject
 * @param credentialVersion the version the token was issued at
 */
public record AccessClaims(Long memberId, int credentialVersion) {
}
