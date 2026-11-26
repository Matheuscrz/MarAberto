package com.maraberto.identity.infra;

import com.maraberto.identity.domain.UserRecoveryCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRecoveryCodeRepository extends JpaRepository<UserRecoveryCode, UUID> {

    Optional<UserRecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    List<UserRecoveryCode> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
