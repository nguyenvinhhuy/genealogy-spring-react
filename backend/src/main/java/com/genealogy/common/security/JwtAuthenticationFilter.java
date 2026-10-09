package com.genealogy.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Populates the security context from a Bearer access token, checked against the account as it is now. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final MemberAccessLookup memberAccessLookup;

    /**
     * Authenticates the request from its Bearer token, then continues the chain.
     *
     * @param request the inbound request
     * @param response the outbound response
     * @param chain the remaining filter chain
     * @throws ServletException if the chain fails
     * @throws IOException if the chain fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            AccessClaims claims = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
            if (claims != null) {
                // One primary-key read per request, so a disable, demotion or password reset applies at once.
                memberAccessLookup.find(claims.memberId())
                        .filter(MemberAccess::active)
                        .filter(access -> access.credentialVersion() == claims.credentialVersion())
                        .ifPresent(JwtAuthenticationFilter::authenticate);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Puts an account into the security context with the role it has now.
     *
     * @param access the account's current access state
     */
    private static void authenticate(MemberAccess access) {
        AuthPrincipal principal = new AuthPrincipal(access.id(), access.email(), access.role());
        var authority = new SimpleGrantedAuthority("ROLE_" + access.role().name());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
