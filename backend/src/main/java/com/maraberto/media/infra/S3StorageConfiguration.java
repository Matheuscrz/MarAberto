package com.maraberto.media.infra;

import java.net.URI;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cliente S3 real, ativo quando {@code maraberto.storage.provider=s3}.
 *
 * <p>Sem credencial o boot falha: um cliente anônimo só descobriria o problema
 * no primeiro upload, quando o mapa já deveria estar arquivado.
 */
@Configuration
@ConditionalOnProperty(name = "maraberto.storage.provider", havingValue = "s3")
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(S3StorageConfiguration.class);

    /** Credenciais de exemplo do `docker-compose.yml`; proibidas no perfil `prod`. */
    static final String DEV_ACCESS_KEY = "maraberto-access-key";

    private final Environment environment;

    public S3StorageConfiguration(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateCredentials() {
        if (DEV_ACCESS_KEY.equals(propertiesAccessKey())) {
            if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
                throw new IllegalStateException(
                        "a credencial de desenvolvimento do Garage não pode ser usada no perfil "
                                + "de produção; defina STORAGE_S3_ACCESS_KEY e STORAGE_S3_SECRET_KEY");
            }
            log.warn("Usando as credenciais de desenvolvimento do Garage. "
                    + "Defina STORAGE_S3_ACCESS_KEY/SECRET_KEY em qualquer ambiente compartilhado.");
        }
    }

    private String propertiesAccessKey() {
        return environment.getProperty("maraberto.storage.s3.access-key");
    }

    @Bean
    S3Client s3Client(S3StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(endpoint(properties))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration(properties))
                // O Garage recusa o `aws-chunked` do SDK (payload assinado em
                // blocos) com "Invalid payload signature"; o checksum só entra
                // quando o serviço exige.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    S3Presigner s3Presigner(S3StorageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(endpoint(properties))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(serviceConfiguration(properties))
                .build();
    }

    private static URI endpoint(S3StorageProperties properties) {
        if (isBlank(properties.getEndpoint())) {
            throw new IllegalStateException(
                    "maraberto.storage.s3.endpoint é obrigatório com STORAGE_PROVIDER=s3");
        }
        return URI.create(properties.getEndpoint());
    }

    private static StaticCredentialsProvider credentials(S3StorageProperties properties) {
        if (isBlank(properties.getAccessKey()) || isBlank(properties.getSecretKey())) {
            throw new IllegalStateException(
                    "maraberto.storage.s3.access-key e secret-key são obrigatórios com "
                            + "STORAGE_PROVIDER=s3; defina GARAGE_ACCESS_KEY e GARAGE_SECRET_KEY");
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.getAccessKey(), properties.getSecretKey()));
    }

    private static S3Configuration serviceConfiguration(S3StorageProperties properties) {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyle())
                // Mesmo motivo do cliente acima, e também para as URLs
                // pré-assinadas: o PUT precisa assinar os bytes reais.
                .chunkedEncodingEnabled(false)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
