package com.genealogy.auth.service;

import com.genealogy.auth.dto.request.LoginRequest;
import com.genealogy.auth.dto.response.LoginResponse;
import com.genealogy.member.service.CredentialsChanged;

/** Sign-in, token rotation and sign-out. */
public interface AuthService {

    /**
     * Verifies credentials and issues a new token pair.
     *
     * @param request the presented credentials
     * @param clientAddress where the attempt came from, for the failed-login throttle
     * @return the access token, the account, and the refresh token to set as a cookie
     */
    AuthResult login(LoginRequest request, String clientAddress);

    /**
     * Exchanges a refresh token for a new pair, revoking the presented one.
     *
     * @param refreshToken the token presented by the client
     * @return the new access token, the account, and the replacement refresh token
     */
    AuthResult refresh(String refreshToken);

    /**
     * Revokes the presented refresh token, ending that session.
     *
     * @param refreshToken the token presented by the client, may be null
     */
    void logout(String refreshToken);

    /**
     * Ends every session an account holds, inside the transaction that changed its credentials.
     *
     * @param changed the account whose password, role or enabled state changed
     */
    void endSessions(CredentialsChanged changed);

    /**
     * A completed authentication, carrying the refresh token separately so the controller owns the cookie.
     *
     * @param response the body to return to the client
     * @param refreshToken the raw refresh token to place in an HttpOnly cookie
     */
    record AuthResult(LoginResponse response, String refreshToken) {
    }
}
