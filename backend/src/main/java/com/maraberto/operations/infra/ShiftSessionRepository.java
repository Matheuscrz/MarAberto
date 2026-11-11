package com.maraberto.operations.infra;

import com.maraberto.operations.domain.ShiftSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftSessionRepository extends JpaRepository<ShiftSession, UUID> {

    Optional<ShiftSession> findByUserIdAndEndedAtIsNull(UUID userId);

    boolean existsByUserIdAndEndedAtIsNull(UUID userId);
}
