package com.maraberto.identity.infra;

import com.maraberto.identity.api.dto.RegisterOperationalUserCommand;
import com.maraberto.identity.application.AuthenticationService;
import com.maraberto.identity.domain.Role;
import com.maraberto.shared.security.crypto.BlindIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Contas de desenvolvimento para exercitar o login real das três áreas.
 *
 * <p>Só roda com {@code maraberto.identity.dev-seed.enabled=true} e **falha ao
 * subir** se o perfil for {@code prod}/{@code production}: nunca crie usuários
 * com senha conhecida em ambiente compartilhado. As contas são idempotentes por
 * e-mail (índice cego), então reiniciar o backend não duplica nada.
 */
@Component
@ConditionalOnProperty(
        prefix = "maraberto.identity.dev-seed",
        name = "enabled",
        havingValue = "true")
public class DevOperationalUserSeeder implements ApplicationRunner {

    public static final String DEV_PASSWORD = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(DevOperationalUserSeeder.class);

    private final AuthenticationService authenticationService;
    private final OperationalUserRepository users;
    private final BlindIndexer blindIndexer;
    private final Environment environment;

    public DevOperationalUserSeeder(
            AuthenticationService authenticationService,
            OperationalUserRepository users,
            BlindIndexer blindIndexer,
            Environment environment) {
        this.authenticationService = authenticationService;
        this.users = users;
        this.blindIndexer = blindIndexer;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "maraberto.identity.dev-seed.enabled não pode ser usado no perfil de produção");
        }

        seed("Sgt. Silva", "BM-10492", "operador@maraberto.dev", "11144477735", Role.LIFEGUARD);
        seed("Ten. Rocha", "CBM-2041", "coordenador@maraberto.dev", "52998224725", Role.COORDINATOR);
        seed("Ana (TI)", null, "admin@maraberto.dev", "39053344705", Role.ADMIN);

        log.warn("Contas de desenvolvimento criadas/verificadas. "
                + "Desligue maraberto.identity.dev-seed.enabled fora do ambiente local.");
    }

    private void seed(
            String name,
            String badgeNumber,
            String email,
            String cpf,
            Role role) {
        String emailBidx = blindIndexer.index(email);
        if (users.existsByEmailBidx(emailBidx)) {
            return;
        }
        authenticationService.registerOperational(new RegisterOperationalUserCommand(
                name,
                badgeNumber,
                email,
                cpf,
                DEV_PASSWORD,
                role));
    }
}
