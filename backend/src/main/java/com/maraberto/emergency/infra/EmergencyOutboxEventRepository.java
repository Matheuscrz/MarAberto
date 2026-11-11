package com.maraberto.emergency.infra;

import com.maraberto.emergency.domain.EmergencyOutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyOutboxEventRepository extends JpaRepository<EmergencyOutboxEvent, UUID> {
}
