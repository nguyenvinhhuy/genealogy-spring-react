package com.genealogy.auth.dto.response;

import com.genealogy.member.dto.response.MemberResponse;

/**
 * The result of a successful sign-in or refresh.
 *
 * @param accessToken short-lived bearer token, held in memory by the client
 * @param member the signed-in account
 */
public record LoginResponse(String accessToken, MemberResponse member) {
}
