/**
 * Módulo beaches: áreas de banho (praia, rio e barragem), setores operacionais, zonas
 * críticas permanentes e semipermanentes, fichas e GeoJSON público.
 *
 * <p>O nome físico da tabela continua {@code beaches}, mas o conceito é área de banho
 * (ADR-021). Eventos publicados: {@code BeachChanged}, {@code SectorChanged},
 * {@code RiskZoneChanged}. Consome: {@code RegionConfigChanged}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Beaches")
package com.maraberto.beaches;
