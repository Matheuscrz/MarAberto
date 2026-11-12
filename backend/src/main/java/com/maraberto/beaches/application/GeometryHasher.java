package com.maraberto.beaches.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.WKBWriter;

/** SHA-256 estável de geometria 2D normalizada e de arquivo. */
final class GeometryHasher {

    private GeometryHasher() {
    }

    static String geometryHash(Geometry geometry) {
        Geometry normalized = geometry.norm();
        byte[] wkb = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, false).write(normalized);
        return sha256(wkb);
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }
}
