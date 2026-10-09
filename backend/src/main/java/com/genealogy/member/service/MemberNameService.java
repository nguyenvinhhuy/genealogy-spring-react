package com.genealogy.member.service;

import java.util.Collection;
import java.util.Map;

/** Names app accounts for the features that show who did something. */
// Apart from MemberService: `audit` needs names and MemberService records into `audit`, which would be a bean cycle.
public interface MemberNameService {

    /**
     * Resolves display names for a batch of accounts in one query.
     *
     * @param ids the accounts to name
     * @return the name of each account that still exists
     */
    Map<Long, String> findNames(Collection<Long> ids);
}
