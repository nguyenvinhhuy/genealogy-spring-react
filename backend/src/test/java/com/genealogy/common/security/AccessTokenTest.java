package com.genealogy.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.genealogy.common.model.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Unit tests for how an access token is issued, verified and turned into a request's identity (§8.10 D3). */
class AccessTokenTest {

    private static final String SECRET = "test-only-secret-value-at-least-32-bytes-long";
    private static final MemberAccess EDITOR = new MemberAccess(7L, "chi@genealogy.vn", Role.EDITOR, true, 2);

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7)));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a token signed with the same secret but not issued by this app is refused")
    void foreignTokenIsRefused() {
        // The sibling charity app, sharing a JWT_SECRET, issues exactly this shape: subject and role, no iss/aud.
        String charity = Jwts.builder()
                .subject("7")
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.parse(charity)).isNull();
        assertThat(provider.parse(provider.createAccessToken(EDITOR))).isEqualTo(new AccessClaims(7L, 2));
    }

    @Test
    @DisplayName("the role a request gets is the account's role now, not the one in the token")
    void roleComesFromTheDatabase() throws Exception {
        String token = provider.createAccessToken(EDITOR);
        MemberAccess demoted = new MemberAccess(7L, "chi@genealogy.vn", Role.MEMBER, true, 2);

        assertThat(authenticate(token, demoted)).isNotNull()
                .extracting(auth -> ((AuthPrincipal) auth.getPrincipal()).role()).isEqualTo(Role.MEMBER);
    }

    @Test
    @DisplayName("a disabled account, or a token from before a password reset, authenticates nothing")
    void disabledOrResetAccountsAreRefused() throws Exception {
        String token = provider.createAccessToken(EDITOR);

        assertThat(authenticate(token, new MemberAccess(7L, "chi@genealogy.vn", Role.EDITOR, false, 2))).isNull();
        assertThat(authenticate(token, new MemberAccess(7L, "chi@genealogy.vn", Role.EDITOR, true, 3))).isNull();
        assertThat(authenticate(token, null)).isNull();
    }

    /**
     * Runs the filter over a request carrying a token, with the account in a given state.
     *
     * @param token the access token
     * @param access the account as the database has it, or null when it no longer exists
     * @return the authentication the filter set, or null
     * @throws Exception when the filter fails
     */
    private Authentication authenticate(String token, MemberAccess access) throws Exception {
        MemberAccessLookup lookup = mock(MemberAccessLookup.class);
        when(lookup.find(7L)).thenReturn(Optional.ofNullable(access));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, lookup);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        SecurityContextHolder.clearContext();

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
