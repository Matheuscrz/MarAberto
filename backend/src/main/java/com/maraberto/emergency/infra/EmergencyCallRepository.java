package com.maraberto.emergency.infra;

import com.maraberto.emergency.domain.EmergencyCall;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyCallRepository extends JpaRepository<EmergencyCall, UUID> {

    Optional<EmergencyCall> findByClientRequestId(String clientRequestId);
}
