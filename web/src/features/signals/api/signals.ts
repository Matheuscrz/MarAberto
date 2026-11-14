import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type { FlagColor } from "@/shared/types/flags";
import type {
  BeachFlagSignal,
  LaunchFlagInput,
  LaunchFlagResult,
  LaunchedFlag,
} from "@/features/signals/types";

interface ApiSignal {
  readonly id: string;
  readonly beachId: string;
  readonly beachName: string;
  readonly sectorId: string | null;
  readonly sectorLabel: string | null;
  readonly flag: FlagColor;
  readonly notes: string | null;
  readonly latitude: number;
  readonly longitude: number;
  readonly stationName: string | null;
  readonly postedAt: string;
  readonly validUntil: string;
  readonly closedAt: string | null;
  readonly photoUrls: readonly string[] | null;
}

/**
 * Sinalizações vigentes de todas as áreas, para o mapa.
 *
 * <p>A vigência é decidida no servidor (`closed_at IS NULL AND valid_until >
 * now()`): o cliente não recebe bandeira vencida e não precisa filtrar.
 */
export async function fetchActiveSignals(): Promise<readonly BeachFlagSignal[]> {
  const signals = await apiRequest<readonly ApiSignal[]>("/api/v1/flags", {});
  return signals.map(toSignal);
}

/**
 * Último lançamento de cada escopo da área, inclusive vencido ou encerrado.
 *
 * <p>É o que a ficha da praia mostra: a última cor conhecida com o selo de
 * "desatualizada" em vez de esconder que existia sinalização.
 */
export async function fetchBeachSignals(
  beachId: string,
): Promise<readonly BeachFlagSignal[]> {
  const signals = await apiRequest<readonly ApiSignal[]>(
    `/api/v1/flags?beachId=${encodeURIComponent(beachId)}&includeExpired=true`,
    {},
  );
  return signals.map(toSignal);
}

/**
 * Lança a bandeira; o servidor encerra a anterior do mesmo escopo.
 *
 * <p>O desfecho é discriminado: `PLACED` publica a bandeira e `UNMAPPED` guarda
 * o registro para a coordenação triar, sem nada aparecer no mapa.
 */
export async function launchFlag(input: LaunchFlagInput): Promise<LaunchFlagResult> {
  return apiRequest<LaunchFlagResult>("/api/v1/operations/flags", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      beachId: input.beachId ?? null,
      sectorId: input.sectorId ?? null,
      flag: input.flag,
      notes: input.notes?.trim() ? input.notes.trim() : null,
      validityHours: input.validityHours ?? null,
      latitude: input.latitude ?? null,
      longitude: input.longitude ?? null,
      accuracyMeters: input.accuracyMeters ?? null,
      mediaIds: input.mediaIds && input.mediaIds.length > 0 ? input.mediaIds : null,
    }),
  });
}

/** Encerra a sinalização antes da validade; ela continua no histórico. */
export async function closeFlag(flagId: string): Promise<LaunchedFlag> {
  return apiRequest<LaunchedFlag>(
    `/api/v1/operations/flags/${encodeURIComponent(flagId)}/close`,
    { method: "POST" },
  );
}

export function signalErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao falar com o servidor.`)
    );
  }
  return "Não foi possível carregar a sinalização agora.";
}

function toSignal(signal: ApiSignal): BeachFlagSignal {
  return {
    id: signal.id,
    beachId: signal.beachId,
    beachName: signal.beachName,
    sectorId: signal.sectorId,
    sectorLabel: signal.sectorLabel,
    flag: signal.flag,
    notes: signal.notes,
    latitude: signal.latitude,
    longitude: signal.longitude,
    stationName: signal.stationName,
    postedAt: signal.postedAt,
    validUntil: signal.validUntil,
    closedAt: signal.closedAt,
    photoUrls: signal.photoUrls ?? [],
  };
}
