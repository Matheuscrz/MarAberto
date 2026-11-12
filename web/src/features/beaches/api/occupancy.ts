import { apiRequest, apiRequestVoid } from "@/shared/lib/api-client";
import type { DevicePosition } from "@/shared/lib/geolocation";

export type OccupancyLevel = "LOW" | "MODERATE" | "HIGH" | "EXTREME";
export type OccupancyStatus = "INSUFFICIENT" | "ESTIMATE";

/** Faixa de um setor. O `INSUFFICIENT` é o esperado em setor com pouca gente. */
export interface OccupancySector {
  readonly sectorId: string;
  readonly status: OccupancyStatus;
  readonly level: OccupancyLevel | null;
}

/**
 * Ocupação da área e dos setores.
 *
 * <p>A faixa da área é a união das amostras, **nunca** a soma dos setores:
 * quem está na fronteira conta nos dois setores. Nunca derivar uma da outra no
 * cliente.
 */
export interface BeachOccupancy {
  readonly beachId: string;
  readonly status: OccupancyStatus;
  readonly level: OccupancyLevel | null;
  readonly windowMinutes: number;
  readonly sectors: readonly OccupancySector[];
}

/**
 * Token efêmero da amostra de ocupação (RNF04).
 *
 * <p>Vive só na memória do módulo: recarregar o app já gera outro, e nada é
 * gravado em `localStorage`/`sessionStorage`. O servidor guarda apenas o HMAC.
 */
const ephemeralToken = newToken();

export async function recordOccupancySample(
  position: DevicePosition,
): Promise<void> {
  await apiRequestVoid("/api/v1/occupancy/samples", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      latitude: position.latitude,
      longitude: position.longitude,
      token: ephemeralToken,
    }),
  });
}

export async function fetchOccupancy(
  beachId: string,
): Promise<BeachOccupancy> {
  return apiRequest<BeachOccupancy>(
    `/api/v1/occupancy/beaches/${encodeURIComponent(beachId)}`,
    {},
  );
}

function newToken(): string {
  // `getRandomValues` está disponível em qualquer contexto seguro e não exige
  // `randomUUID`; 32 caracteres hexadecimais atendem ao contrato do backend.
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join(
    "",
  );
}
