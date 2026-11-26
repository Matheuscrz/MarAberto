package com.maraberto.identity.infra;

import com.maraberto.identity.domain.OperationalUser;
import com.maraberto.identity.domain.Role;
import com.maraberto.shared.security.CpfValidator;
import com.maraberto.shared.security.crypto.BlindIndexer;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Garante o administrador inicial na primeira subida da instalação (release v1).
 *
 * <p>Cria a conta padrão {@code admin} (senha inicial configurável, padrão
 * {@code admin}) com {@code must_change_password = true} somente quando não
 * existe nenhum usuário {@code ADMIN} ativo no banco. No primeiro login o
 * filtro de segurança bloqueia todas as demais rotas até a senha definitiva ser
 * definida.
 */
@Component
@ConditionalOnProperty(
        prefix = "maraberto.identity.bootstrap-admin",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final OperationalUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BlindIndexer blindIndexer;
    private final String username;
    private final String email;
    private final String password;
    private final String cpf;

    public AdminBootstrapRunner(
            OperationalUserRepository users,
            PasswordEncoder passwordEncoder,
            BlindIndexer blindIndexer,
            @Value("${maraberto.identity.bootstrap-admin.username:admin}") String username,
            @Value("${maraberto.identity.bootstrap-admin.email:admin@maraberto.local}") String email,
            @Value("${maraberto.identity.bootstrap-admin.password:admin}") String password,
            @Value("${maraberto.identity.bootstrap-admin.cpf:12345678909}") String cpf) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.blindIndexer = blindIndexer;
        this.username = username;
        this.email = email;
        this.password = password;
        this.cpf = cpf;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRoleAndActiveTrue(Role.ADMIN)) {
            return;
        }
        String badge = OperationalUser.normalizeBadgeNumber(username);
        String emailBidx = blindIndexer.index(email);
        String cleanCpf = CpfValidator.normalize(cpf);
        if (!CpfValidator.isValid(cleanCpf)) {
            throw new IllegalStateException("CPF configurado para o admin inicial é inválido");
        }
        String cpfBidx = blindIndexer.indexDigits(cleanCpf);
        if ((badge != null && users.existsByBadgeNumber(badge))
                || users.existsByEmailBidx(emailBidx)
                || users.existsByCpfBidx(cpfBidx)) {
            return;
        }

        Instant now = Instant.now();
        OperationalUser admin = new OperationalUser(
                UUID.randomUUID(),
                "Administrador",
                badge,
                email,
                emailBidx,
                cleanCpf,
                cpfBidx,
                passwordEncoder.encode(password),
                Role.ADMIN);
        admin.requirePasswordChangeOnFirstLogin(now);
        users.save(admin);

        log.warn("Usuário administrador inicial '{}' criado com troca obrigatória de senha no primeiro login.",
                badge);
    }
}
