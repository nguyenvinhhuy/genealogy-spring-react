package com.genealogy.member.repository;

import com.genealogy.common.model.Role;
import com.genealogy.common.security.MemberAccess;
import com.genealogy.member.domain.Member;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for app accounts. */
public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * Finds an account by its email address, already lower-cased by the caller.
     *
     * @param email the email to look up
     * @return the member, or empty if no account uses that email
     */
    Optional<Member> findByEmail(String email);

    /**
     * Reports whether an account already uses the given email, already lower-cased by the caller.
     *
     * @param email the email to check
     * @return true if taken
     */
    boolean existsByEmail(String email);

    /**
     * Reads only what the request filter checks about an account.
     *
     * @param id the member id
     * @return the access state, or empty when the account no longer exists
     */
    // Runs on every request, so it reads five columns instead of the whole entity.
    @Query("SELECT new com.genealogy.common.security.MemberAccess(m.id, m.email, m.role, m.active,"
            + " m.credentialVersion) FROM Member m WHERE m.id = :id")
    Optional<MemberAccess> findAccess(@Param("id") Long id);

    /**
     * Raises an account's credential version, which makes every access token issued before it stop working.
     *
     * @param id the member id
     * @return how many rows changed
     */
    @Modifying
    @Query("UPDATE Member m SET m.credentialVersion = m.credentialVersion + 1 WHERE m.id = :id")
    int bumpCredentialVersion(@Param("id") Long id);

    /**
     * Loads the enabled accounts holding a role and locks their rows until the transaction ends.
     *
     * @param role the role
     * @return the enabled accounts that hold it
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.role = :role AND m.active = true")
    List<Member> lockActiveByRole(@Param("role") Role role);
}
