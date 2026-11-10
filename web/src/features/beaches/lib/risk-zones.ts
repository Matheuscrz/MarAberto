import type { FeatureCollection, Polygon } from "geojson";
import type { Beach } from "@/features/beaches/types";

/**
 * Retorna GeoJSON vazio até que os polígonos reais do KML sejam importados.
 * Quando existirem, cada zona vira um `Feature<Polygon>` com `color`.
 */
export function riskZonesOf(
  beaches: readonly Beach[],
): FeatureCollection<Polygon> {
  void beaches;
  return { type: "FeatureCollection", features: [] };
}
