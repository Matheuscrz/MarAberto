import type { Feature, FeatureCollection } from "geojson";
import type { BeachMap, BeachPolygon } from "@/features/beaches/types";

export type BeachShapes = FeatureCollection<BeachPolygon>;

const EMPTY: BeachShapes = { type: "FeatureCollection", features: [] };

/**
 * FeatureCollection da área para o mapa.
 *
 * <p>O `features/map` não conhece praia: ele recebe GeoJSON genérico e a
 * composição (o `app/`) decide o que desenhar.
 */
export function areaShapes(map: BeachMap | null): BeachShapes {
  if (!map) return EMPTY;
  const feature: Feature<BeachPolygon> = {
    type: "Feature",
    properties: { id: map.id },
    geometry: map.geometry,
  };
  return { type: "FeatureCollection", features: [feature] };
}

/** Setores da área, marcando o selecionado e o setor em que a pessoa está. */
export function sectorShapes(
  map: BeachMap | null,
  selectedSectorId: string | null,
  currentSectorId: string | null,
): BeachShapes {
  if (!map) return EMPTY;
  return {
    type: "FeatureCollection",
    features: map.sectors.map((sector) => ({
      type: "Feature",
      properties: {
        id: sector.id,
        selected: sector.id === selectedSectorId,
        current: sector.id === currentSectorId,
      },
      geometry: sector.geometry,
    })),
  };
}
