package com.maraberto.identity.infra;

import com.maraberto.identity.domain.AuthSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    /**
     * Lê a sessão com bloqueio pessimista: dois refresh simultâneos com o mesmo
     * token são serializados, e o segundo enxerga a rotação do primeiro em vez
     * de gerar duas famílias válidas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.refreshHash = :refreshHash")
    Optional<AuthSession> findByRefreshHashForUpdate(@Param("refreshHash") String refreshHash);

    Optional<AuthSession> findByRefreshHash(String refreshHash);

    @Query("""
            select s from AuthSession s
            where s.userId = :userId
              and s.revokedAt is null
              and s.expiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<AuthSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthSession s set s.revokedAt = :now where s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s set s.revokedAt = :now
            where s.userId = :userId
              and s.familyId = :familyId
              and s.revokedAt is null
            """)
    int revokeFamilyForUser(
            @Param("userId") UUID userId,
            @Param("familyId") UUID familyId,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s set s.revokedAt = :now
            where s.userId = :userId
              and s.familyId <> :currentFamilyId
              and s.revokedAt is null
            """)
    int revokeOtherFamiliesForUser(
            @Param("userId") UUID userId,
            @Param("currentFamilyId") UUID currentFamilyId,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuthSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
