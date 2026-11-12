import type { MultiPolygon, Polygon } from "geojson";
import type { FlagColor, RiskLevel } from "@/shared/types/flags";

export type RiskType =
  | "RIP_CURRENT"
  | "RIVER_MOUTH"
  | "ROCKS"
  | "DEEP_WATER"
  | "WILDLIFE"
  | "POLLUTION";

export interface RiskZone {
  readonly id: string;
  readonly type: RiskType;
  readonly label: string;
  readonly description: string;
  readonly severity: RiskLevel;
  readonly guidance: readonly string[];
}

/** Tipo de corpo d'água da área de banho (ADR-021), como vem do backend. */
export type BeachKind = "BEACH" | "RIVER" | "DAM" | "LAGOON" | "OTHER";

/**
 * Área de banho pública vinda da importação do mapa.
 *
 * <p>`distanceMeters` só existe na lista por proximidade; a ficha não tem
 * posição de referência. Descrição, bandeira, risco e ocupação são camadas
 * separadas (sinalização e amostras anônimas) e não fazem parte do polígono
 * importado.
 */
export interface Beach {
  readonly id: string;
  readonly name: string;
  readonly kind: BeachKind;
  readonly municipality: string;
  readonly state: string;
  readonly sectorCount: number;
  readonly latitude: number;
  readonly longitude: number;
  readonly distanceMeters?: number;
}

export interface BeachSector {
  readonly id: string;
  readonly name: string | null;
  readonly displayOrder: number;
  readonly latitude: number;
  readonly longitude: number;
}

export interface BeachDetail extends Beach {
  readonly sectors: readonly BeachSector[];
}

/** Polígono do mapeamento: o PostGIS emite Polygon quando há uma única parte. */
export type BeachPolygon = Polygon | MultiPolygon;

/** Retângulo em WGS 84, como vem do backend. */
export interface BeachMapBounds {
  readonly minLon: number;
  readonly minLat: number;
  readonly maxLon: number;
  readonly maxLat: number;
}

/** `[minLon, minLat, maxLon, maxLat]`, o formato aceito por `fitBounds`. */
export type BoundsTuple = readonly [number, number, number, number];

export function boundsTuple(bounds: BeachMapBounds): BoundsTuple {
  return [bounds.minLon, bounds.minLat, bounds.maxLon, bounds.maxLat];
}

/** Setor com a geometria simplificada que o mapa desenha. */
export interface BeachMapSector {
  readonly id: string;
  readonly name: string | null;
  readonly displayOrder: number;
  readonly bbox: BeachMapBounds;
  readonly geometry: BeachPolygon;
}

/**
 * Geometria da área e dos setores para o mapa.
 *
 * <p>Vem de `GET /beaches/{id}/map`, não da lista: a lista é consultada a cada
 * abertura do mapa e não pode carregar dezoito polígonos.
 */
export interface BeachMap {
  readonly id: string;
  readonly name: string;
  readonly bbox: BeachMapBounds;
  readonly geometry: BeachPolygon;
  readonly sectors: readonly BeachMapSector[];
}

/** Área e setor que contêm a posição consultada. */
export interface BeachPosition {
  readonly beachId: string;
  readonly beachName: string;
  readonly sectorId: string;
  readonly sectorLabel: string;
}

/** Resposta da lista por proximidade: as praias e onde a pessoa está. */
export interface NearbyBeaches {
  readonly beaches: readonly Beach[];
  readonly position: BeachPosition | null;
}

/** O KML não batiza todo setor; a UI mostra "Setor N" pela ordem. */
export function sectorLabel(sector: {
  readonly name: string | null;
  readonly displayOrder: number;
}): string {
  return sector.name ?? `Setor ${sector.displayOrder}`;
}

export const BEACH_KIND_LABEL: Record<BeachKind, string> = {
  BEACH: "Praia",
  RIVER: "Banho de rio",
  DAM: "Barragem",
  LAGOON: "Lagoa",
  OTHER: "Área de banho",
};

/** Mantém a bandeira tipada para quando a sinalização real chegar. */
export type BeachFlag = FlagColor;
