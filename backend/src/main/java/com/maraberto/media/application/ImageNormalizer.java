package com.maraberto.media.application;

import com.maraberto.media.domain.InvalidImageException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Normaliza a foto enviada pelo aparelho antes de guardar (ADR-012).
 *
 * <p>A imagem é **re-codificada**, não copiada: isso descarta EXIF, GPS do
 * aparelho e qualquer payload embutido no arquivo, e garante que o que é
 * servido ao público é exatamente o que o servidor produziu. O limite de
 * dimensão evita guardar 12 MP e serve uma foto util o suficiente para o mapa.
 */
@Component
public class ImageNormalizer {

    /** Maior lado da imagem guardada. */
    static final int MAX_DIMENSION = 1600;

    /** Teto de pixels de origem: barra "bomba de descompressão". */
    static final long MAX_SOURCE_PIXELS = 40_000_000L;

    private static final float JPEG_QUALITY = 0.85f;

    public NormalizedImage normalize(byte[] original) {
        BufferedImage source = read(original);

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if ((long) sourceWidth * sourceHeight > MAX_SOURCE_PIXELS) {
            throw new InvalidImageException("A imagem enviada é grande demais.");
        }

        double scale = Math.min(
                1.0, (double) MAX_DIMENSION / Math.max(sourceWidth, sourceHeight));
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));

        // JPEG não tem canal alfa: o fundo branco evita faixas pretas em PNG
        // transparente.
        BufferedImage normalized =
                new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        return new NormalizedImage(writeJpeg(normalized), "image/jpeg", width, height);
    }

    private static BufferedImage read(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException();
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new InvalidImageException();
            }
            return image;
        } catch (IOException exception) {
            throw new InvalidImageException();
        }
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
            output.flush();
            writer.dispose();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new InvalidImageException("Não foi possível processar a imagem.");
        } finally {
            writer.dispose();
        }
    }
}
