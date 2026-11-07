package com.maraberto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Garante que as fronteiras entre os módulos são respeitadas e que todos os
 * módulos previstos na Architecture.md são reconhecidos pelo Spring Modulith.
 */
class ModularityTests {

    private static final List<String> EXPECTED_MODULES = List.of(
            "identity",
            "region",
            "beaches",
            "operations",
            "emergency",
            "safety",
            "tides",
            "content",
            "occupancy",
            "media",
            "realtime",
            "audit",
            "analytics",
            "shared");

    @Test
    void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(MarAbertoApplication.class);

        modules.verify();

        assertThat(StreamSupport.stream(modules.spliterator(), false)
                        .map(ApplicationModule::getName)
                        .toList())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_MODULES);
    }
}
