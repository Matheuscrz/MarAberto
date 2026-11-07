package com.maraberto.emergency.infra;

import com.maraberto.emergency.domain.EmergencyDispatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyDispatchRepository extends JpaRepository<EmergencyDispatch, UUID> {
}
