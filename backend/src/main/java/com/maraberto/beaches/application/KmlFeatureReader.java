package com.maraberto.beaches.application;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Leitor de KML da ferramenta de importação (ADR-016).
 *
 * <p>Não é parser do caminho de produção: roda no seed de desenvolvimento e no
 * endpoint da coordenação. O XML é namespace-aware e desliga DTD/entidades
 * externas para eliminar XXE.
 */
@Component
public class KmlFeatureReader {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    public List<ImportedFeature> read(InputStream input) {
        Document document = parse(input);
        NodeList placemarks = document.getElementsByTagNameNS("*", "Placemark");
        if (placemarks.getLength() == 0) {
            throw new KmlParseException("KML sem Placemark");
        }

        List<ImportedFeature> features = new ArrayList<>(placemarks.getLength());
        for (int index = 0; index < placemarks.getLength(); index++) {
            features.add(toFeature((Element) placemarks.item(index), index));
        }
        return features;
    }

    private Document parse(InputStream input) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(input);
        } catch (Exception exception) {
            throw new KmlParseException("KML inválido ou não pôde ser lido", exception);
        }
    }

    private ImportedFeature toFeature(Element placemark, int position) {
        Map<String, String> metadata = extendedData(placemark);
        String id = value(metadata, "id");
        if (id == null || id.isBlank()) {
            throw new KmlParseException("Placemark " + (position + 1) + " sem ExtendedData/id");
        }
        String praia = value(metadata, "Praia");
        String municipio = value(metadata, "Municipio");
        if (praia == null || praia.isBlank() || municipio == null || municipio.isBlank()) {
            throw new KmlParseException(
                    "Feição " + id + " sem Praia ou Municipio no ExtendedData");
        }

        MultiPolygon geometry = geometry(placemark, id);
        return new ImportedFeature(
                id.trim(),
                praia.trim(),
                nullableSector(value(metadata, "Setor")),
                municipio.trim(),
                trimToNull(value(metadata, "Estado")),
                geometry,
                Map.copyOf(metadata));
    }

    private static String nullableSector(String setor) {
        if (setor == null || setor.isBlank() || "-".equals(setor.trim())) {
            return null;
        }
        return setor.trim();
    }

    private static String value(Map<String, String> metadata, String key) {
        return metadata.get(key);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Map<String, String> extendedData(Element placemark) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (Element simpleData : descendants(placemark, "SimpleData")) {
            String name = simpleData.getAttribute("name");
            if (!name.isBlank()) {
                metadata.put(name, simpleData.getTextContent().trim());
            }
        }
        for (Element data : descendants(placemark, "Data")) {
            String name = data.getAttribute("name");
            if (name.isBlank() || metadata.containsKey(name)) {
                continue;
            }
            List<Element> values = descendants(data, "value");
            if (!values.isEmpty()) {
                metadata.put(name, values.getFirst().getTextContent().trim());
            }
        }
        return metadata;
    }

    private MultiPolygon geometry(Element placemark, String featureId) {
        List<Polygon> polygons = new ArrayList<>();
        for (Element polygonElement : descendants(placemark, "Polygon")) {
            Polygon polygon = toPolygon(polygonElement, featureId);
            if (polygon != null) {
                polygons.add(polygon);
            }
        }
        if (polygons.isEmpty()) {
            throw new KmlParseException("Feição " + featureId + " sem geometria Polygon");
        }
        MultiPolygon multiPolygon = GEOMETRY_FACTORY.createMultiPolygon(
                polygons.toArray(Polygon[]::new));
        multiPolygon.setSRID(4326);
        if (multiPolygon.isEmpty() || !multiPolygon.isValid()) {
            throw new KmlParseException("Feição " + featureId + " com geometria inválida");
        }
        return multiPolygon;
    }

    private Polygon toPolygon(Element polygonElement, String featureId) {
        List<Element> outer = descendants(polygonElement, "outerBoundaryIs");
        if (outer.isEmpty()) {
            return null;
        }
        LinearRing shell = ring(outer.getFirst(), featureId);
        if (shell == null) {
            return null;
        }
        List<LinearRing> holes = new ArrayList<>();
        for (Element inner : descendants(polygonElement, "innerBoundaryIs")) {
            LinearRing hole = ring(inner, featureId);
            if (hole != null) {
                holes.add(hole);
            }
        }
        return GEOMETRY_FACTORY.createPolygon(shell, holes.toArray(LinearRing[]::new));
    }

    private LinearRing ring(Element boundary, String featureId) {
        List<Element> coordinates = descendants(boundary, "coordinates");
        if (coordinates.isEmpty()) {
            return null;
        }
        List<Coordinate> points = parseCoordinates(coordinates.getFirst().getTextContent(), featureId);
        if (points.size() < 3) {
            return null;
        }
        if (!points.getFirst().equals2D(points.getLast())) {
            points.add(points.getFirst().copy());
        }
        if (points.size() < 4) {
            return null;
        }
        return GEOMETRY_FACTORY.createLinearRing(points.toArray(Coordinate[]::new));
    }

    private List<Coordinate> parseCoordinates(String text, String featureId) {
        List<Coordinate> points = new ArrayList<>();
        for (String tuple : text.trim().split("\\s+")) {
            if (tuple.isBlank()) {
                continue;
            }
            String[] parts = tuple.split(",");
            if (parts.length < 2) {
                throw new KmlParseException(
                        "Feição " + featureId + " com coordenada malformada: " + tuple);
            }
            try {
                // Z é ignorado de propósito: o modelo é 2D.
                points.add(new Coordinate(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1])));
            } catch (NumberFormatException exception) {
                throw new KmlParseException(
                        "Feição " + featureId + " com coordenada malformada: " + tuple, exception);
            }
        }
        return points;
    }

    private static List<Element> descendants(Element root, String localName) {
        List<Element> found = new ArrayList<>();
        collect(root, localName, found);
        return found;
    }

    private static void collect(Element element, String localName, List<Element> found) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            if (localName.equals(child.getLocalName()) || localName.equals(child.getNodeName())) {
                found.add(child);
            }
            collect(child, localName, found);
        }
    }
}
