package com.maraberto.media.infra;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexão com o armazenamento de objetos S3-compatível (Garage, ADR-006).
 *
 * <p>O endpoint, as credenciais e o estilo de endereçamento vêm daqui; o bucket
 * é escolhido por quem chama a porta, porque cada tipo de objeto tem o seu
 * destino e a sua política de retenção.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "maraberto.storage.s3")
public class S3StorageProperties {

    /** URL do endpoint S3, com esquema e porta (ex.: {@code http://garage:3900}). */
    private String endpoint = "http://localhost:9000";

    /**
     * Região exigida pela assinatura. O Garage aceita qualquer valor; use o
     * mesmo configurado no {@code garage.toml}.
     */
    private String region = "garage";

    private String accessKey;

    private String secretKey;

    /** {@code true} para {@code endpoint/bucket/key} (Garage e MinIO não usam DNS de bucket). */
    private boolean pathStyle = true;
}
