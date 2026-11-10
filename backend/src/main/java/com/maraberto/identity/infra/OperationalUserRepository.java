package com.maraberto.identity.infra;

import com.maraberto.identity.domain.OperationalUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalUserRepository extends JpaRepository<OperationalUser, UUID> {

    Optional<OperationalUser> findByEmailBidx(String emailBidx);

    Optional<OperationalUser> findByCpfBidx(String cpfBidx);

    Optional<OperationalUser> findByBadgeNumber(String badgeNumber);

    boolean existsByCpfBidx(String cpfBidx);

    boolean existsByEmailBidx(String emailBidx);

    boolean existsByBadgeNumber(String badgeNumber);
}
