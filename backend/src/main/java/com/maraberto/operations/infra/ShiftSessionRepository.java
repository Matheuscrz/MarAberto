package com.maraberto.operations.infra;

import com.maraberto.operations.domain.ShiftSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftSessionRepository extends JpaRepository<ShiftSession, UUID> {

    Optional<ShiftSession> findByUserIdAndEndedAtIsNull(UUID userId);

    /**
     * Plantão aberto cujo último sinal de vida está dentro do lease.
     *
     * <p>A condição fica na consulta (e não em código depois da leitura) para
     * que a expiração preguiçosa valha também no {@code move}/{@code heartbeat}
     * sem precisar marcar a linha: um plantão vencido simplesmente não existe
     * mais para o console.
     */
    Optional<ShiftSession> findByUserIdAndEndedAtIsNullAndLastSeenAtGreaterThanEqual(
            UUID userId, Instant aliveSince);

    boolean existsByUserIdAndEndedAtIsNull(UUID userId);
}
