package com.maraberto.operations.application;

import com.maraberto.operations.api.FlagChanged;
import com.maraberto.operations.api.PostChanged;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cache curto da leitura pública de sinalizações e estruturas.
 *
 * <p>O mapa do banhista consulta {@code /api/v1/flags} e {@code /api/v1/stations}
 * a cada 30 s. Guardar o resultado por alguns segundos evita repetir a mesma
 * consulta espacial para cada visitante, e os eventos {@link FlagChanged} e
 * {@link PostChanged} limpam o cache assim que o ato é confirmado no banco —
 * o próximo ciclo já vê a bandeira ou o posto novo.
 *
 * <p>O cache é por instância e nunca é fonte da verdade. Em várias instâncias,
 * uma escrita numa delas pode deixar a outra com a cópia antiga até o TTL
 * (10 s por padrão), ainda dentro da janela de 30 s do mapa. A vigência por
 * tempo (bandeira vencida) também é coberta pelo TTL, porque não existe evento
 * para a passagem do relógio.
 *
 * <p>Os listeners usam {@code AFTER_COMMIT}: limpar antes do commit deixaria
 * uma leitura concorrente repovoar o cache com o estado antigo. A geração é
 * conferida depois do carregamento, então uma consulta que começou antes do
 * commit devolve o resultado para quem pediu, mas não o grava no cache. Com
 * {@code fallbackExecution}, uma chamada fora de transação também invalida.
 */
@Service
public class PublicMapCache {

    private static final int MAX_ENTRIES = 256;

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    /** Muda a cada invalidação; impede que uma leitura em voo repovoe o cache. */
    private final AtomicLong generation = new AtomicLong();
    private final Duration ttl;

    public PublicMapCache(
            @Value("${maraberto.public-map.cache-ttl:PT10S}") Duration ttl) {
        this.ttl = ttl == null || ttl.isNegative() ? Duration.ZERO : ttl;
    }

    /**
     * Devolve o valor em cache ou executa o carregador.
     *
     * <p>TTL zero desliga o cache (usado em teste quando a limpeza é feita por
     * SQL direto, fora dos serviços).
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        if (ttl.isZero()) {
            return loader.get();
        }
        Instant now = Instant.now();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt().isAfter(now)) {
            return (T) entry.value();
        }
        long startedAt = generation.get();
        T value = loader.get();
        // Se um evento limpou o cache enquanto a consulta rodava, o resultado
        // pode ser anterior ao commit: devolve para quem pediu, mas não guarda.
        if (generation.get() == startedAt) {
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
            cache.put(key, new CacheEntry(value, now.plus(ttl)));
        }
        return value;
    }

    /** Invalida tudo: a próxima leitura pública busca o estado atual. */
    public void clear() {
        generation.incrementAndGet();
        cache.clear();
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onFlagChanged(FlagChanged event) {
        clear();
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onPostChanged(PostChanged event) {
        clear();
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
