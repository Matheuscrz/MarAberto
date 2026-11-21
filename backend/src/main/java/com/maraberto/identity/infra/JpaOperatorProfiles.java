package com.maraberto.identity.infra;

import com.maraberto.identity.api.OperatorProfile;
import com.maraberto.identity.api.OperatorProfiles;
import com.maraberto.identity.domain.OperationalUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Perfis a partir do cadastro JPA: o conversor da entidade decifra o nome uma
 * única vez, dentro do módulo que é dono do dado sensível.
 */
@Component
public class JpaOperatorProfiles implements OperatorProfiles {

    private final OperationalUserRepository users;

    public JpaOperatorProfiles(OperationalUserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<OperatorProfile> find(UUID userId) {
        return users.findById(userId).map(JpaOperatorProfiles::profile);
    }

    @Override
    public List<OperatorProfile> findAll(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return users.findAllById(userIds).stream()
                .map(JpaOperatorProfiles::profile)
                .toList();
    }

    private static OperatorProfile profile(OperationalUser user) {
        return new OperatorProfile(user.getId(), user.getName(), user.getBadgeNumber());
    }
}
