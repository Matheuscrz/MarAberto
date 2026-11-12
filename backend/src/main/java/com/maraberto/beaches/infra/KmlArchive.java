package com.maraberto.beaches.infra;

import com.maraberto.media.api.ObjectStoragePort;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Arquiva o KML aplicado no armazenamento de objetos (ADR-016).
 *
 * <p>Guardar o arquivo original é o que permite auditar e reprocessar uma
 * importação depois; o hash do conteúdo já é a identidade do lote, então a
 * chave é endereçada por conteúdo e reenviar o mesmo arquivo sobrescreve o
 * mesmo objeto em vez de acumular cópias.
 *
 * <p>O arquivamento é <strong>best-effort</strong>: se o storage estiver fora,
 * o mapa do CBM ainda entra e o lote fica com a chave nula. Perder o mapa por
 * causa da auditoria seria pior para a operação.
 */
@Component
public class KmlArchive {

    private static final Logger log = LoggerFactory.getLogger(KmlArchive.class);
    private static final String CONTENT_TYPE = "application/vnd.google-earth.kml+xml";

    private final ObjectStoragePort storage;
    private final String bucket;
    private final String prefix;

    public KmlArchive(
            ObjectStoragePort storage,
            @Value("${maraberto.beaches.import.bucket:media-public}") String bucket,
            @Value("${maraberto.beaches.import.prefix:bathing-areas}") String prefix) {
        this.storage = storage;
        this.bucket = bucket;
        this.prefix = prefix;
    }

    /**
     * Envia o arquivo e devolve a chave gravada no lote.
     *
     * @return a chave do objeto, ou {@code null} quando não foi possível arquivar
     */
    public String store(String state, String sourceVersion, byte[] content) {
        String key = prefix + "/" + state.toUpperCase(Locale.ROOT) + "/" + sourceVersion + ".kml";
        try {
            storage.put(bucket, key, CONTENT_TYPE, content);
            return key;
        } catch (RuntimeException exception) {
            log.warn("KML não arquivado em {}/{}: a importação segue sem o arquivo de auditoria",
                    bucket, key, exception);
            return null;
        }
    }
}
