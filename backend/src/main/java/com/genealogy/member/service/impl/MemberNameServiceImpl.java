package com.genealogy.member.service.impl;

import com.genealogy.member.domain.Member;
import com.genealogy.member.repository.MemberRepository;
import com.genealogy.member.service.MemberNameService;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link MemberNameService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberNameServiceImpl implements MemberNameService {

    private final MemberRepository memberRepository;

    /**
     * Resolves display names for a batch of accounts in one query.
     *
     * @param ids the accounts to name
     * @return the name of each account that still exists
     */
    @Override
    public Map<Long, String> findNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Member::getId, Member::getFullName));
    }
}
