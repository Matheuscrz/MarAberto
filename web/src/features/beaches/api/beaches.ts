import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type { DevicePosition } from "@/shared/lib/geolocation";
import type {
  Beach,
  BeachDetail,
  BeachKind,
  BeachSector,
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

interface ApiNearbyBeaches {
  readonly radiusMeters: number;
  readonly beaches: readonly ApiBeachSummary[];
}

/**
 * Lista as áreas de banho num raio de até 30 km da posição.
 *
 * <p>A posição viaja só neste request: não é gravada no cliente nem no
 * servidor. O backend arredonda a chave do cache em memória e devolve o mapa,
 * não a posição.
 */
export async function fetchNearbyBeaches(
  position: DevicePosition,
  radiusMeters: number = NEARBY_RADIUS_METERS,
): Promise<readonly Beach[]> {
  const params = new URLSearchParams({
    lat: position.latitude.toFixed(5),
    lon: position.longitude.toFixed(5),
    radiusM: String(radiusMeters),
  });
  const response = await apiRequest<ApiNearbyBeaches>(
    `/api/v1/beaches?${params.toString()}`,
    {},
  );
  return response.beaches.map(toBeach);
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
