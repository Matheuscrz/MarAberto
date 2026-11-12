import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type { DevicePosition } from "@/shared/lib/geolocation";
import type {
  Beach,
  BeachDetail,
  BeachKind,
  BeachMap,
  BeachMapBounds,
  BeachMapSector,
  BeachPolygon,
  BeachPosition,
  BeachSector,
  NearbyBeaches,
} from "@/features/beaches/types";

/** Raio da lista pública: até 30 km, conforme a regra da tela de praias. */
export const NEARBY_RADIUS_METERS = 30_000;

interface ApiBeachSummary {
  readonly id: string;
  readonly name: string;
  readonly kind: BeachKind;
  readonly municipality: string | null;
  readonly stateCode: string;
  readonly sectorCount: number;
  readonly latitude: number;
  readonly longitude: number;
  readonly distanceMeters: number;
}

interface ApiBeachSector {
  readonly id: string;
  readonly name: string | null;
  readonly displayOrder: number;
  readonly latitude: number;
  readonly longitude: number;
}

interface ApiBeachDetail {
  readonly id: string;
  readonly name: string;
  readonly kind: BeachKind;
  readonly municipality: string | null;
  readonly stateCode: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly sectors: readonly ApiBeachSector[];
}

interface ApiBeachMapSector {
  readonly id: string;
  readonly name: string | null;
  readonly displayOrder: number;
  readonly bbox: BeachMapBounds;
  readonly geometry: BeachPolygon;
}

interface ApiBeachMap {
  readonly id: string;
  readonly name: string;
  readonly kind: BeachKind;
  readonly bbox: BeachMapBounds;
  readonly geometry: BeachPolygon;
  readonly sectors: readonly ApiBeachMapSector[];
}

interface ApiNearbyBeaches {
  readonly radiusMeters: number;
  readonly position: BeachPosition | null;
  readonly beaches: readonly ApiBeachSummary[];
}

/**
 * Lista as áreas de banho num raio de até 30 km da posição.
 *
 * <p>A posição viaja só neste request: não é gravada no cliente nem no
 * servidor. O backend arredonda a chave do cache em memória e devolve o mapa,
 * não a posição — além da área e do setor em que ela caiu, quando houver.
 */
export async function fetchNearbyBeaches(
  position: DevicePosition,
  radiusMeters: number = NEARBY_RADIUS_METERS,
): Promise<NearbyBeaches> {
  const params = new URLSearchParams({
    lat: position.latitude.toFixed(5),
    lon: position.longitude.toFixed(5),
    radiusM: String(radiusMeters),
  });
  const response = await apiRequest<ApiNearbyBeaches>(
    `/api/v1/beaches?${params.toString()}`,
    {},
  );
  return {
    beaches: response.beaches.map(toBeach),
    position: response.position,
  };
}

export async function fetchBeachDetail(beachId: string): Promise<BeachDetail> {
  const response = await apiRequest<ApiBeachDetail>(
    `/api/v1/beaches/${encodeURIComponent(beachId)}`,
    {},
  );
  return {
    id: response.id,
    name: response.name,
    kind: response.kind,
    municipality: response.municipality ?? "",
    state: response.stateCode,
    sectorCount: response.sectors.length,
    latitude: response.latitude,
    longitude: response.longitude,
    sectors: response.sectors.map(toSector),
  };
}

/**
 * Geometria da área e dos setores, simplificada para o zoom de bairro.
 *
 * <p>É um request separado da lista de propósito: a lista é chamada a cada
 * abertura do mapa e não pode carregar os polígonos de todas as praias.
 */
export async function fetchBeachMap(beachId: string): Promise<BeachMap> {
  const response = await apiRequest<ApiBeachMap>(
    `/api/v1/beaches/${encodeURIComponent(beachId)}/map`,
    {},
  );
  return {
    id: response.id,
    name: response.name,
    bbox: response.bbox,
    geometry: response.geometry,
    sectors: response.sectors.map(toMapSector),
  };
}

export function beachErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem?.title ?? error.message;
  }
  return "Não foi possível carregar as praias agora.";
}

function toBeach(beach: ApiBeachSummary): Beach {
  return {
    id: beach.id,
    name: beach.name,
    kind: beach.kind,
    municipality: beach.municipality ?? "",
    state: beach.stateCode,
    sectorCount: beach.sectorCount,
    latitude: beach.latitude,
    longitude: beach.longitude,
    distanceMeters: beach.distanceMeters,
  };
}

function toSector(sector: ApiBeachSector): BeachSector {
  return {
    id: sector.id,
    name: sector.name,
    displayOrder: sector.displayOrder,
    latitude: sector.latitude,
    longitude: sector.longitude,
  };
}

function toMapSector(sector: ApiBeachMapSector): BeachMapSector {
  return {
    id: sector.id,
    name: sector.name,
    displayOrder: sector.displayOrder,
    bbox: sector.bbox,
    geometry: sector.geometry,
  };
}
