package com.maraberto.operations.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.operations.api.FlagChanged;
import com.maraberto.operations.api.PostChanged;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * O cache da leitura pública precisa devolver o estado novo assim que o
 * lançamento é confirmado no banco, sem esperar o TTL.
 */
class PublicMapCacheTests {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @Test
    void aFlagChangeInvalidatesThePublicRead() {
        PublicMapCache cache = new PublicMapCache(FIVE_MINUTES);
        String[] current = {"bandeira antiga"};

        assertThat(cache.get("flags:null:false", () -> current[0]))
                .isEqualTo("bandeira antiga");

        current[0] = "bandeira nova";
        assertThat(cache.get("flags:null:false", () -> current[0]))
                .as("dentro do TTL, a leitura continua no cache")
                .isEqualTo("bandeira antiga");

        cache.onFlagChanged(new FlagChanged(
                UUID.randomUUID(), UUID.randomUUID(), null));

        assertThat(cache.get("flags:null:false", () -> current[0]))
                .isEqualTo("bandeira nova");
    }

    @Test
    void aPostChangeInvalidatesThePublicRead() {
        PublicMapCache cache = new PublicMapCache(FIVE_MINUTES);
        String[] current = {"posto antigo"};

        assertThat(cache.get("stations", () -> current[0])).isEqualTo("posto antigo");
        current[0] = "posto novo";

        cache.onPostChanged(new PostChanged(
                UUID.randomUUID(), "FIXED", false));

        assertThat(cache.get("stations", () -> current[0])).isEqualTo("posto novo");
    }

    @Test
    void zeroTtlDisablesTheCache() {
        PublicMapCache cache = new PublicMapCache(Duration.ZERO);
        String[] current = {"primeiro"};

        assertThat(cache.get("stations", () -> current[0])).isEqualTo("primeiro");
        current[0] = "segundo";

        assertThat(cache.get("stations", () -> current[0])).isEqualTo("segundo");
    }

    @Test
    void aChangeDuringTheLoadDoesNotRepopulateTheOldValue() {
        PublicMapCache cache = new PublicMapCache(FIVE_MINUTES);

        // A consulta começou antes do commit e terminou depois da invalidação:
        // quem pediu recebe o resultado da consulta, mas o cache não o guarda.
        String inFlight = cache.get("stations", () -> {
            cache.onFlagChanged(new FlagChanged(
                    UUID.randomUUID(), UUID.randomUUID(), null));
            return "posto antigo";
        });
        assertThat(inFlight).isEqualTo("posto antigo");

        assertThat(cache.get("stations", () -> "posto novo"))
                .isEqualTo("posto novo");
    }
}
