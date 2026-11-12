package com.maraberto.beaches.application;

import com.maraberto.beaches.api.BeachDetail;
import com.maraberto.beaches.api.BeachSummary;
import com.maraberto.beaches.api.BathingAreaMapChanged;
import com.maraberto.beaches.api.BeachChanged;
import com.maraberto.beaches.api.NearbyBeaches;
import com.maraberto.beaches.infra.PublicBeachRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Consulta pública de praias e setores com cache curto em memória.
 *
 * <p>A lista depende da posição de quem consulta, então o cache é por célula de
 * aproximadamente 110 m (3 casas decimais) e raio — <strong>nunca</strong> é
 * gravado, logado ou compartilhado entre instâncias. A posição continua sendo
 * dado efêmero de request (ADR-012); o que fica retido é só o resultado
 * público do mapa, invalidado a cada importação.
 *
 * <p>O teto de 30 km é regra de produto, não só de performance: a lista mostra
 * o que dá para alcançar, não o estado inteiro.
 */
@Service
public class BeachMapQueryService {

    /** Raio máximo da consulta pública, em metros. */
    public static final int MAX_RADIUS_METERS = 30_000;
    public static final int DEFAULT_RADIUS_METERS = MAX_RADIUS_METERS;

    private static final Duration CACHE_TTL = Duration.ofSeconds(120);
    private static final int CACHE_MAX_ENTRIES = 512;

    private final PublicBeachRepository repository;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public BeachMapQueryService(PublicBeachRepository repository) {
        this.repository = repository;
    }

    public NearbyBeaches nearby(Double latitude, Double longitude, Integer radiusMeters) {
        double lat = requireLatitude(latitude);
        double lon = requireLongitude(longitude);
        int radius = requireRadius(radiusMeters);

        String key = nearbyKey(lat, lon, radius);
        List<BeachSummary> beaches = cached(key, () -> repository.findNearby(lat, lon, radius));
        return new NearbyBeaches(radius, beaches);
    }

    public BeachDetail detail(UUID beachId) {
        if (beachId == null) {
            throw new InvalidLocationException("Identificador da área de banho é obrigatório");
        }
        return cached("detail:" + beachId,
                () -> repository.findDetail(beachId)
                        .orElseThrow(() -> new BeachNotFoundException(beachId)));
    }

    /** O mapa mudou: o resultado público em cache deixou de valer. */
    @EventListener({BathingAreaMapChanged.class, BeachChanged.class})
    public void invalidate() {
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, java.util.function.Supplier<T> loader) {
        Instant now = Instant.now();
        CacheEntry entry = cache.get(key);
        if (entry != null && entry.expiresAt().isAfter(now)) {
            return (T) entry.value();
        }
        T value = loader.get();
        if (cache.size() >= CACHE_MAX_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new CacheEntry(value, now.plus(CACHE_TTL)));
        return value;
    }

    private static String nearbyKey(double lat, double lon, int radius) {
        // Arredondar a chave é o que evita guardar a posição exata: a célula de
        // 0,001° (~110 m) é pública o suficiente para agrupar consultas
        // próximas e grosseira demais para reconstruir um trajeto.
        return String.format(Locale.ROOT, "nearby:%.3f,%.3f,%d", lat, lon, radius);
    }

    private static double requireLatitude(Double latitude) {
        if (latitude == null) {
            throw new InvalidLocationException(
                    "Informe a latitude e a longitude para listar as praias num raio de até 30 km");
        }
        if (latitude < -90 || latitude > 90) {
            throw new InvalidLocationException("Latitude fora do intervalo permitido (-90 a 90)");
        }
        return latitude;
    }

    private static double requireLongitude(Double longitude) {
        if (longitude == null) {
            throw new InvalidLocationException(
                    "Informe a latitude e a longitude para listar as praias num raio de até 30 km");
        }
        if (longitude < -180 || longitude > 180) {
            throw new InvalidLocationException(
                    "Longitude fora do intervalo permitido (-180 a 180)");
        }
        return longitude;
    }

    private static int requireRadius(Integer radiusMeters) {
        if (radiusMeters == null) {
            return DEFAULT_RADIUS_METERS;
        }
        if (radiusMeters <= 0 || radiusMeters > MAX_RADIUS_METERS) {
            throw new InvalidLocationException(
                    "O raio deve estar entre 1 e " + MAX_RADIUS_METERS + " metros");
        }
        return radiusMeters;
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
