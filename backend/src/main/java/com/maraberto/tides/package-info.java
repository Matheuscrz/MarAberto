/**
 * Módulo tides: ingestão, previsão, sizígia e consulta da tábua de marés.
 *
 * <p>Eventos publicados: {@code TideForecastIngested}.
 * Consome: {@code RegionConfigChanged}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Tides")
package com.maraberto.tides;
