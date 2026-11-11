package com.maraberto.identity.infra;

import com.maraberto.identity.api.OperatorDirectory;
import com.maraberto.identity.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaOperatorDirectory implements OperatorDirectory {

    private final OperationalUserRepository users;

    public JpaOperatorDirectory(OperationalUserRepository users) {
        this.users = users;
    }

    @Override
    public List<UUID> activeCoordinationUserIds() {
        return users.findActiveIdsByRole(Role.COORDINATOR);
    }
}
