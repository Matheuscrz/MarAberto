import type { FeatureCollection, Polygon } from "geojson";
import type { Beach } from "@/features/beaches/types";

/**
 * Zonas de risco do mapa (RF02/RF18).
 *
 * <p>As zonas críticas são uma camada própria, com validade e importação
 * separadas do polígono da área de banho; até essa camada existir a coleção
 * volta vazia em vez de inventar risco.
 */
export function riskZonesOf(
  beaches: readonly Beach[],
): FeatureCollection<Polygon> {
  void beaches;
  return { type: "FeatureCollection", features: [] };
}
