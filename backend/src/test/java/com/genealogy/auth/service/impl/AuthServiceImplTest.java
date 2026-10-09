package com.genealogy.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.genealogy.auth.domain.RefreshToken;
import com.genealogy.auth.domain.RevokeReason;
import com.genealogy.auth.dto.request.LoginRequest;
import com.genealogy.auth.repository.RefreshTokenRepository;
import com.genealogy.auth.service.AuthService;
import com.genealogy.common.exception.TooManyRequestsException;
import com.genealogy.common.exception.UnauthorizedException;
import com.genealogy.common.model.Role;
import com.genealogy.common.security.AccessClaims;
import com.genealogy.common.security.JwtProperties;
import com.genealogy.common.security.JwtTokenProvider;
import com.genealogy.common.security.LoginThrottle;
import com.genealogy.common.security.MemberAccess;
import com.genealogy.common.security.MemberAccessLookup;
import com.genealogy.member.dto.response.MemberResponse;
import com.genealogy.member.service.CredentialsChanged;
import com.genealogy.member.service.MemberService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link AuthServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "admin@genealogy.vn";
    private static final String PASSWORD = "Admin@123";
    private static final String ADDRESS = "10.0.0.7";

    @Mock
    private MemberService memberService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private MemberAccessLookup memberAccessLookup;

    private JwtTokenProvider tokenProvider;
    private AuthServiceImpl authService;
    private MemberResponse member;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
                "test-only-secret-value-at-least-32-bytes-long", Duration.ofMinutes(15), Duration.ofDays(7));
        tokenProvider = new JwtTokenProvider(jwtProperties);

        authService = new AuthServiceImpl(
                memberService, refreshTokenRepository, tokenProvider, jwtProperties, memberAccessLookup,
                new LoginThrottle());

        member = new MemberResponse(1L, "Quan tri vien", EMAIL, Role.ADMIN, null, true, Instant.now());
        lenient().when(memberAccessLookup.find(anyLong()))
                .thenReturn(Optional.of(new MemberAccess(1L, EMAIL, Role.ADMIN, true, 3)));
    }

    @Test
    void loginReturnsTokensBoundToTheCurrentCredentialVersion() {
        when(memberService.verifyCredentials(EMAIL, PASSWORD)).thenReturn(Optional.of(member));

        AuthService.AuthResult result = authService.login(new LoginRequest(EMAIL, PASSWORD), ADDRESS);

        assertThat(result.response().accessToken()).isNotBlank();
        assertThat(result.response().member().email()).isEqualTo(EMAIL);
        // The filter compares this against the database on every request (§8.10 #10).
        assertThat(tokenProvider.parse(result.response().accessToken())).isEqualTo(new AccessClaims(1L, 3));
        assertThat(result.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsCredentialsTheMemberFeatureRefused() {
        // Wrong password, disabled account and unknown email all arrive here as the same empty answer.
        when(memberService.verifyCredentials(EMAIL, PASSWORD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), ADDRESS))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Email hoặc mật khẩu không đúng");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void loginIsRefusedAfterTooManyFailuresWithoutCheckingThePassword() {
        when(memberService.verifyCredentials(EMAIL, PASSWORD)).thenReturn(Optional.empty());
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), ADDRESS))
                    .isInstanceOf(UnauthorizedException.class);
        }

        // The sixth guess is refused before the hash is checked, so a script cannot keep guessing (§8.10 #12).
        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, PASSWORD), ADDRESS))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void refreshRevokesEverySessionWhenARotatedTokenIsReplayed() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked(RevokeReason.ROTATED)));

        assertThatThrownBy(() -> authService.refresh("some-token")).isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository).revokeAllForMember(eq(1L), any(Instant.class), eq(RevokeReason.CREDENTIALS));
    }

    @Test
    void refreshOfALoggedOutOrPreResetTokenEndsNoOtherSession() {
        // An old laptop presenting its signed-out cookie signed the whole family out, every time (§8.11 #2).
        for (RevokeReason reason : new RevokeReason[] {RevokeReason.LOGOUT, RevokeReason.CREDENTIALS}) {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revoked(reason)));

            assertThatThrownBy(() -> authService.refresh("some-token")).isInstanceOf(UnauthorizedException.class);
        }

        verify(refreshTokenRepository, never()).revokeAllForMember(anyLong(), any(Instant.class), any());
    }

    @Test
    void refreshOfAMerelyExpiredTokenEndsNoOtherSession() {
        when(refreshTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(token(Instant.now().minus(Duration.ofDays(1)))));

        assertThatThrownBy(() -> authService.refresh("some-token")).isInstanceOf(UnauthorizedException.class);

        // An old cookie on a forgotten laptop is not a theft; revoking everything signed the owner out (§8.10 #13).
        verify(refreshTokenRepository, never()).revokeAllForMember(anyLong(), any(Instant.class), any());
    }

    @Test
    void logoutRevokesThePresentedTokenAsALogout() {
        RefreshToken live = token(Instant.now().plus(Duration.ofDays(1)));
        live.setId(5L);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(live));

        authService.logout("some-token");

        verify(refreshTokenRepository).revokeIfLive(eq(5L), any(Instant.class), eq(RevokeReason.LOGOUT));
    }

    @Test
    void refreshRefusesAnAccountThatWasDisabledSinceTheSessionStarted() {
        when(refreshTokenRepository.findByTokenHash(any()))
                .thenReturn(Optional.of(token(Instant.now().plus(Duration.ofDays(1)))));
        when(refreshTokenRepository.revokeIfLive(any(), any(Instant.class), eq(RevokeReason.ROTATED))).thenReturn(1);
        when(memberService.findActive(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("some-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("đã bị khoá");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshRefusesTheLoserOfTwoConcurrentRotations() {
        RefreshToken live = token(Instant.now().plus(Duration.ofDays(1)));
        live.setId(5L);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(live));
        // A row count of 0 means another request revoked it between this one's read and its write.
        when(refreshTokenRepository.revokeIfLive(eq(5L), any(Instant.class), eq(RevokeReason.ROTATED)))
                .thenReturn(0);

        assertThatThrownBy(() -> authService.refresh("some-token")).isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void aCredentialChangeEndsEverySessionTheMemberHolds() {
        authService.endSessions(new CredentialsChanged(7L));

        verify(refreshTokenRepository).revokeAllForMember(eq(7L), any(Instant.class), eq(RevokeReason.CREDENTIALS));
    }

    /**
     * Builds a stored, unexpired token of member 1 revoked for a reason.
     *
     * @param reason why it was revoked
     * @return the token
     */
    private static RefreshToken revoked(RevokeReason reason) {
        RefreshToken token = token(Instant.now().plus(Duration.ofDays(1)));
        token.setRevokedAt(Instant.now().minus(Duration.ofMinutes(1)));
        token.setRevokeReason(reason);
        return token;
    }

    /**
     * Builds a stored refresh token of member 1.
     *
     * @param expiresAt when it expires
     * @return the token
     */
    private static RefreshToken token(Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setMemberId(1L);
        token.setTokenHash("irrelevant");
        token.setExpiresAt(expiresAt);
        return token;
    }
}
