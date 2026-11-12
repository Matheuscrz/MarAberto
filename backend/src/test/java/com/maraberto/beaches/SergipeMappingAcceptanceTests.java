package com.maraberto.beaches;

import static org.assertj.core.api.Assertions.assertThat;

import com.maraberto.beaches.application.ImportedFeature;
import com.maraberto.beaches.application.KmlFeatureReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Confere o mapeamento real de Sergipe contra os critérios de aceite da Fatia 1
 * (AGENTS.md): 45 setores, 32 áreas por município/praia, 20 setores sem nome e
 * 3 barragens.
 *
 * <p>O arquivo é do órgão e fica em {@code docs/kml/}; o teste é ignorado
 * quando ele não está no checkout. Se o QGIS reexportar o mapa, os números
 * mudam junto e este teste é o lugar onde essa mudança aparece.
 */
class SergipeMappingAcceptanceTests {

    private final KmlFeatureReader reader = new KmlFeatureReader();

    @Test
    void readsTheRealSergipeBathingAreaMapping() throws Exception {
        Path path = Path.of("../docs/kml/Areas_Banho_Sergipe.kml");
        Assumptions.assumeTrue(Files.isReadable(path),
                "KML real de Sergipe não está no checkout");

        List<ImportedFeature> features;
        try (InputStream input = Files.newInputStream(path)) {
            features = reader.read(input);
        }

        assertThat(features).hasSize(45);
        assertThat(features.stream()
                .map(feature -> normalize(feature.municipio()) + "|" + normalize(feature.praia()))
                .distinct())
                .hasSize(32);
        assertThat(features).filteredOn(feature -> feature.setor() == null).hasSize(20);
        assertThat(features.stream()
                .map(ImportedFeature::praia)
                .filter(name -> normalize(name).startsWith("barragem"))
                .distinct())
                .hasSize(3);

        assertThat(features).allSatisfy(feature -> {
            assertThat(feature.id()).isNotBlank();
            assertThat(feature.geometry().getSRID()).isEqualTo(4326);
            assertThat(feature.geometry().isValid()).isTrue();
            assertThat(feature.geometry().getArea()).isPositive();
            assertThat(feature.uf()).isEqualTo("SE");
        });
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
