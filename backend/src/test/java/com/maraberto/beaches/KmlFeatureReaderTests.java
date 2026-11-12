package com.maraberto.beaches;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maraberto.beaches.application.ImportedFeature;
import com.maraberto.beaches.application.KmlFeatureReader;
import com.maraberto.beaches.application.KmlParseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class KmlFeatureReaderTests {

    private final KmlFeatureReader reader = new KmlFeatureReader();

    @Test
    void readsPlacemarksAsSectorsWith2dMultiPolygon() throws Exception {
        List<ImportedFeature> features;
        try (InputStream input = getClass().getResourceAsStream("/kml/tiny-areas.kml")) {
            features = reader.read(input);
        }

        assertThat(features).hasSize(3);
        ImportedFeature multi = features.getFirst();
        assertThat(multi.id()).isEqualTo("7");
        assertThat(multi.praia()).isEqualTo("Praia do Farol");
        assertThat(multi.setor()).isEqualTo("Setor Norte");
        assertThat(multi.municipio()).isEqualTo("Aracaju");
        assertThat(multi.uf()).isNull();
        assertThat(multi.metadata()).containsEntry("Num. de Oc", "12");
        assertThat(multi.geometry().getSRID()).isEqualTo(4326);
        assertThat(multi.geometry().getNumGeometries()).isEqualTo(2);
        assertThat(multi.geometry().isValid()).isTrue();
        assertThat(Double.isNaN(multi.geometry().getCoordinates()[0].getZ())).isTrue();

        assertThat(features.get(1).setor()).isNull();
        assertThat(features.get(2).uf()).isEqualTo("SE");
    }

    @Test
    void rejectsDoctypeAndExternalEntities() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE kml [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark><name>&xxe;</name></Placemark>
                </kml>
                """;
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(
                xxe.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(KmlParseException.class);
    }

    @Test
    void rejectsPlacemarkWithoutGeometry() {
        String kml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark>
                    <ExtendedData><SchemaData><SimpleData name="id">1</SimpleData>
                    <SimpleData name="Praia">Praia X</SimpleData>
                    <SimpleData name="Municipio">Aracaju</SimpleData></SchemaData></ExtendedData>
                  </Placemark>
                </kml>
                """;
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(
                kml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(KmlParseException.class)
                .hasMessageContaining("sem geometria");
    }
}
