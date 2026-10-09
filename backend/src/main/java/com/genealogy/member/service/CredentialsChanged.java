package com.genealogy.member.service;

/**
 * Published when an account's password, role or enabled state changes.
 *
 * @param memberId the account whose sessions must end
 */
// An event, not a call: `auth` depends on `member`, so `member` calling `auth` back is a bean cycle.
public record CredentialsChanged(Long memberId) {
}
