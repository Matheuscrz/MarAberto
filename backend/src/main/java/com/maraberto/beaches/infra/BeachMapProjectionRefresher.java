package com.maraberto.beaches.infra;

import com.maraberto.beaches.api.BathingAreaMapChanged;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Atualiza a projeção materializada do mapa público depois que o lote comitou.
 *
 * <p>O refresh é {@code CONCURRENTLY} para não bloquear leituras, por isso roda
 * fora da transação de importação. Uma falha aqui não desfaz o mapa: a próxima
 * importação (ou o próximo start) atualiza a view.
 */
@Component
public class BeachMapProjectionRefresher {

    private final JdbcTemplate jdbc;

    public BeachMapProjectionRefresher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMapChanged(BathingAreaMapChanged event) {
        jdbc.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY beach_map_features");
    }
}
