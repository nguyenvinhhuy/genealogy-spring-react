package com.genealogy.auth.repository;

import com.genealogy.auth.domain.RefreshToken;
import com.genealogy.auth.domain.RevokeReason;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Data access for refresh tokens. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Finds a stored token by its hash.
     *
     * @param tokenHash the SHA-256 hash of the presented token
     * @return the record, or empty if unknown
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every live token belonging to a member.
     *
     * @param memberId the member whose sessions to end
     * @param now the revocation instant
     * @param reason why they end
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason"
            + " WHERE t.memberId = :memberId AND t.revokedAt IS NULL")
    void revokeAllForMember(
            @Param("memberId") Long memberId, @Param("now") Instant now, @Param("reason") RevokeReason reason);

    /**
     * Revokes one token only if nobody has revoked it already.
     *
     * @param id the token to revoke
     * @param now the revocation instant
     * @param reason why it ends
     * @return 1 when this caller won the race, 0 when another had already revoked it
     */
    // The row count is the lock: two concurrent refreshes both passed the read-then-write check and both rotated.
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now, t.revokeReason = :reason"
            + " WHERE t.id = :id AND t.revokedAt IS NULL")
    int revokeIfLive(@Param("id") Long id, @Param("now") Instant now, @Param("reason") RevokeReason reason);

    /**
     * Deletes tokens that can never be used again.
     *
     * @param before the cutoff; anything revoked or expired before this goes
     * @return how many rows were removed
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :before OR t.revokedAt < :before")
    int deleteSpent(@Param("before") Instant before);
}
