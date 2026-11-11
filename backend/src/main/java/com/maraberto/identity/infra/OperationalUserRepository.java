package com.maraberto.identity.infra;

import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationalUserRepository extends JpaRepository<OperationalUser, UUID> {

    Optional<OperationalUser> findByEmailBidx(String emailBidx);

    Optional<OperationalUser> findByCpfBidx(String cpfBidx);

    Optional<OperationalUser> findByBadgeNumber(String badgeNumber);

    boolean existsByCpfBidx(String cpfBidx);

    boolean existsByEmailBidx(String emailBidx);

    boolean existsByBadgeNumber(String badgeNumber);

    @Query("SELECT u.id FROM OperationalUser u WHERE u.role = :role AND u.active = true")
    List<UUID> findActiveIdsByRole(@Param("role") Role role);
}
