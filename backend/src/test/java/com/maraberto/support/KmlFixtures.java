package com.maraberto.support;

import java.util.Arrays;

/** KML mínimo para os testes de área de banho (uma feição por setor). */
public final class KmlFixtures {

    private KmlFixtures() {
    }

    public static String kml(Feature... features) {
        StringBuilder kml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                """);
        Arrays.stream(features).map(KmlFixtures::placemark).forEach(kml::append);
        return kml.append("""
                  </Document>
                </kml>
                """).toString();
    }

    public static Feature feature(
            String id, String praia, String setor, String municipio,
            String estado, double minX, double minY) {
        return new Feature(id, praia, setor, municipio, estado, minX, minY);
    }

    private static String placemark(Feature feature) {
        double minX = feature.minX();
        double minY = feature.minY();
        double maxX = minX + 0.001;
        double maxY = minY + 0.001;
        String estado = feature.estado() == null ? "" : """
                    <SimpleData name="Estado">%s</SimpleData>
                """.formatted(feature.estado());
        String setor = feature.setor() == null ? "-" : feature.setor();
        return """
                    <Placemark>
                      <ExtendedData>
                        <SchemaData schemaUrl="#areas">
                          <SimpleData name="id">%s</SimpleData>
                          <SimpleData name="Praia">%s</SimpleData>
                          <SimpleData name="Setor">%s</SimpleData>
                          <SimpleData name="Municipio">%s</SimpleData>
                %s        </SchemaData>
                      </ExtendedData>
                      <MultiGeometry>
                        <Polygon>
                          <outerBoundaryIs>
                            <LinearRing>
                              <coordinates>%s,%s %s,%s %s,%s %s,%s %s,%s</coordinates>
                            </LinearRing>
                          </outerBoundaryIs>
                        </Polygon>
                      </MultiGeometry>
                    </Placemark>
                """.formatted(
                feature.id(), feature.praia(), setor, feature.municipio(), estado,
                minX, minY, maxX, minY, maxX, maxY, minX, maxY, minX, minY);
    }

    public record Feature(
            String id,
            String praia,
            String setor,
            String municipio,
            String estado,
            double minX,
            double minY) {
    }
}
