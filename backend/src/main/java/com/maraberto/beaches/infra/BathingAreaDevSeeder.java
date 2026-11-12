package com.maraberto.beaches.infra;

import com.maraberto.beaches.application.BathingAreaImportService;
import com.maraberto.beaches.application.ImportMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Importa o KML real de Sergipe no ambiente de desenvolvimento.
 *
 * <p>Só roda com {@code maraberto.beaches.dev-seed.enabled=true}, apenas com a
 * tabela vazia e <strong>falha ao subir</strong> no perfil {@code prod}. Usa o
 * mesmo serviço do endpoint da coordenação, então a carga inicial e a
 * reimportação têm exatamente o mesmo comportamento.
 */
@Component
@ConditionalOnProperty(
        prefix = "maraberto.beaches.dev-seed",
        name = "enabled",
        havingValue = "true")
public class BathingAreaDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BathingAreaDevSeeder.class);

    private final BathingAreaImportService importService;
    private final BathingAreaRepository repository;
    private final Environment environment;
    private final String file;
    private final String state;

    public BathingAreaDevSeeder(
            BathingAreaImportService importService,
            BathingAreaRepository repository,
            Environment environment,
            @Value("${maraberto.beaches.dev-seed.file}") String file,
            @Value("${maraberto.beaches.dev-seed.state:SE}") String state) {
        this.importService = importService;
        this.repository = repository;
        this.environment = environment;
        this.file = file;
        this.state = state;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "maraberto.beaches.dev-seed.enabled não pode ser usado no perfil de produção");
        }
        if (!repository.isEmpty()) {
            return;
        }
        Path path = Path.of(file);
        if (!Files.isReadable(path)) {
            log.warn("Seed de áreas de banho ligado, mas o KML não existe: {}", path);
            return;
        }
        try {
            var report = importService.importKml(new BathingAreaImportService.ImportCommand(
                    Files.readAllBytes(path),
                    path.getFileName().toString(),
                    state,
                    false,
                    ImportMode.UPSERT,
                    null));
            log.warn("KML de desenvolvimento importado: {} setores criados, {} inalterados, "
                            + "{} rejeitados. Desligue maraberto.beaches.dev-seed.enabled fora do ambiente local.",
                    report.created(), report.unchanged(), report.rejected());
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler o KML de seed: " + path, exception);
        }
    }
}
