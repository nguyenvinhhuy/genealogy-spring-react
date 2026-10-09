package com.genealogy.auth.service.impl;

import com.genealogy.auth.domain.RefreshToken;
import com.genealogy.auth.domain.RevokeReason;
import com.genealogy.auth.dto.request.LoginRequest;
import com.genealogy.auth.dto.response.LoginResponse;
import com.genealogy.auth.repository.RefreshTokenRepository;
import com.genealogy.auth.service.AuthService;
import com.genealogy.common.exception.UnauthorizedException;
import com.genealogy.common.security.JwtProperties;
import com.genealogy.common.security.JwtTokenProvider;
import com.genealogy.common.security.LoginThrottle;
import com.genealogy.common.security.MemberAccess;
import com.genealogy.common.security.MemberAccessLookup;
import com.genealogy.member.dto.response.MemberResponse;
import com.genealogy.member.service.CredentialsChanged;
import com.genealogy.member.service.MemberService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link AuthService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String INVALID_CREDENTIALS = "Email hoặc mật khẩu không đúng";
    private static final String SESSION_ENDED = "Phiên đăng nhập đã hết. Hãy đăng nhập lại.";

    // Cross-feature by interface only (CLAUDE.md §4): the password hash never crosses this boundary.
    private final MemberService memberService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final MemberAccessLookup memberAccessLookup;
    private final LoginThrottle loginThrottle;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Verifies credentials and issues a new token pair.
     *
     * @param request the presented credentials
     * @param clientAddress where the attempt came from, for the throttle
     * @return the access token, the account, and the refresh token to set as a cookie
     */
    @Override
    // No transaction around BCrypt: each read and the token insert open their own, so no connection waits on a hash.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthResult login(LoginRequest request, String clientAddress) {
        // Counted before the hash is checked: an unthrottled login let a script guess the trưởng tộc's password.
        LoginThrottle.Attempt attempt = loginThrottle.beginLogin(request.email(), clientAddress);
        // The hash never leaves `member`: only that feature knows whether the email exists at all.
        MemberResponse member = memberService.verifyCredentials(request.email(), request.password())
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        attempt.succeeded();
        return issueTokens(member);
    }

    /**
     * Exchanges a refresh token for a new pair, revoking the presented one.
     *
     * @param refreshToken the token presented by the client
     * @return the new access token, the account, and the replacement refresh token
     */
    @Override
    // noRollbackFor, or the revocation below is undone by the very exception that reports the replay.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException(SESSION_ENDED);
        }

        Instant now = Instant.now();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException(SESSION_ENDED));

        if (stored.getRevokedAt() != null) {
            // Only a rotated token presented again means the cookie leaked; a logged-out one is just an old cookie.
            if (stored.getRevokeReason() == RevokeReason.ROTATED) {
                refreshTokenRepository.revokeAllForMember(stored.getMemberId(), now, RevokeReason.CREDENTIALS);
                // A thief's access token must not outlive the sessions: it would work for up to 15 minutes more.
                memberService.endAccessTokens(stored.getMemberId());
            }
            throw new UnauthorizedException(SESSION_ENDED);
        }
        // Merely expired is not a replay: a stale cookie on an old laptop used to sign the owner out everywhere.
        if (!stored.isUsableAt(now)) {
            throw new UnauthorizedException(SESSION_ENDED);
        }

        // A conditional UPDATE, not a field set: two refreshes of the same token both passed isUsableAt above.
        if (refreshTokenRepository.revokeIfLive(stored.getId(), now, RevokeReason.ROTATED) == 0) {
            throw new UnauthorizedException(SESSION_ENDED);
        }
        MemberResponse member = memberService.findActive(stored.getMemberId())
                // Re-checked every rotation, or disabling an account never ends the sessions already open.
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không còn hoặc đã bị khoá"));

        return issueTokens(member);
    }

    /**
     * Revokes the presented refresh token, ending that session.
     *
     * @param refreshToken the token presented by the client, may be null
     */
    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeIfLive(
                        token.getId(), Instant.now(), RevokeReason.LOGOUT));
    }

    /**
     * Ends every session an account holds, inside the transaction that changed its credentials.
     *
     * @param changed the account whose password, role or enabled state changed
     */
    @Override
    @EventListener
    @Transactional
    public void endSessions(CredentialsChanged changed) {
        // Same transaction as the change: a password that changed while its sessions survived protected nobody.
        refreshTokenRepository.revokeAllForMember(changed.memberId(), Instant.now(), RevokeReason.CREDENTIALS);
    }

    /**
     * Issues an access token and a freshly persisted refresh token for a member.
     *
     * @param member the authenticated account
     * @return the completed authentication
     */
    private AuthResult issueTokens(MemberResponse member) {
        // Bound to the account's credential version as it is now, which the filter compares on every request.
        MemberAccess access = memberAccessLookup.find(member.id())
                .orElseThrow(() -> new UnauthorizedException("Tài khoản không còn hoặc đã bị khoá"));
        String accessToken = tokenProvider.createAccessToken(access);
        String rawRefreshToken = generateRefreshToken();

        RefreshToken record = new RefreshToken();
        record.setMemberId(member.id());
        record.setTokenHash(hash(rawRefreshToken));
        record.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(record);

        return new AuthResult(new LoginResponse(accessToken, member), rawRefreshToken);
    }

    /**
     * Generates a URL-safe random refresh token.
     *
     * @return the raw token
     */
    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hashes a raw refresh token for storage and lookup.
     *
     * @param rawToken the token as presented by the client
     * @return the lowercase hex SHA-256 digest
     */
    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
