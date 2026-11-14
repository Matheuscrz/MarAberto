import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type {
  AdminStation,
  CreateStationInput,
  PublicStation,
  StationKind,
  StationPositionInput,
} from "@/features/stations/types";

/**
 * Postos fixos ativos e bases móveis com plantão vivo.
 *
 * <p>O filtro é do servidor: posto desativado e base sem turno aberto não vêm
 * na resposta. O cache HTTP é de 30 s, suficiente para o mapa acompanhar a
 * desativação sem consulta a cada segundo.
 */
export async function fetchPublicStations(): Promise<readonly PublicStation[]> {
  return apiRequest<readonly PublicStation[]>("/api/v1/stations", {});
}

/** Todas as estruturas, inclusive inativas, para o painel da TI. */
export async function fetchAdminStations(): Promise<readonly AdminStation[]> {
  return apiRequest<readonly AdminStation[]>("/api/v1/admin/stations", {});
}

/** Estrutura única no console de campo, inclusive desativada. */
export async function fetchStation(
  kind: StationKind,
  stationId: string,
): Promise<AdminStation> {
  return apiRequest<AdminStation>(
    `/api/v1/operations/stations/${kind}/${encodeURIComponent(stationId)}`,
    {},
  );
}

/**
 * Ativa ou desativa a estrutura no painel da TI.
 *
 * <p>Desativar não apaga nada: plantões e ocorrências continuam apontando para
 * a linha. O que muda é a elegibilidade — a estrutura sai do mapa público e da
 * lista de destinos do plantão.
 */
export async function updateStationStatus(
  kind: StationKind,
  stationId: string,
  active: boolean,
  statusNote: string | null,
): Promise<AdminStation> {
  return apiRequest<AdminStation>(
    `/api/v1/admin/stations/${kind}/${encodeURIComponent(stationId)}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ active, statusNote }),
    },
  );
}

/**
 * Ativa ou desativa a estrutura do próprio plantão em campo.
 *
 * <p>O servidor só aceita a estrutura do plantão aberto do operador; a
 * coordenação e a TI podem alternar qualquer uma. Desativar tira o pin do mapa
 * na hora e não apaga plantão nem ocorrência.
 */
export async function updateFieldStationStatus(
  kind: StationKind,
  stationId: string,
  active: boolean,
  statusNote: string | null,
): Promise<AdminStation> {
  return apiRequest<AdminStation>(
    `/api/v1/operations/stations/${kind}/${encodeURIComponent(stationId)}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ active, statusNote }),
    },
  );
}

/**
 * Cadastra o posto/base onde o operador está (RF11).
 *
 * <p>A coordenada é a leitura atual do aparelho: o servidor guarda a origem e a
 * precisão, e o painel da coordenação mostra de onde veio o ponto.
 */
export async function createStation(
  input: CreateStationInput,
): Promise<AdminStation> {
  return apiRequest<AdminStation>("/api/v1/operations/stations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      kind: input.kind,
      name: input.name.trim(),
      beachId: input.kind === "FIXED" ? (input.beachId ?? null) : null,
      latitude: input.latitude,
      longitude: input.longitude,
      accuracyMeters: input.accuracyMeters ?? null,
      mediaIds: input.mediaIds && input.mediaIds.length > 0 ? input.mediaIds : null,
    }),
  });
}

/** Reposiciona a estrutura com a leitura atual do aparelho (RF11). */
export async function moveStation(
  kind: StationKind,
  stationId: string,
  input: StationPositionInput,
): Promise<AdminStation> {
  return apiRequest<AdminStation>(
    `/api/v1/operations/stations/${kind}/${encodeURIComponent(stationId)}/position`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        latitude: input.latitude,
        longitude: input.longitude,
        accuracyMeters: input.accuracyMeters ?? null,
      }),
    },
  );
}

export function stationErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao falar com o servidor.`)
    );
  }
  return "Não foi possível carregar os postos agora.";
}
