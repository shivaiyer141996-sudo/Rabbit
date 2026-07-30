package com.rabbit.aip.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationTokenRepository
        extends JpaRepository<InvitationToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from InvitationToken token
            where token.membershipId = :membershipId
            """)
    Optional<InvitationToken> findByMembershipIdForUpdate(
            @Param("membershipId") UUID membershipId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from InvitationToken token where token.tokenHash = :tokenHash")
    Optional<InvitationToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    Optional<InvitationToken> findByTokenHash(String tokenHash);
}
