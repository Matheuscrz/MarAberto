import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type { UnmappedSignal, UnmappedSignalStatus } from "@/features/signals";
import type {
  UnmappedSignalDecisionInput,
} from "@/features/management/types";

/**
 * Fila de sinalizações registradas fora do mapeamento.
 *
 * <p>O guarda-vidas registra o ponto; a coordenação decide o que ele é. Nada
 * desta fila é publicado no mapa: são registros de campo para triagem.
 */
export async function fetchUnmappedSignals(
  status?: UnmappedSignalStatus,
): Promise<readonly UnmappedSignal[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiRequest<readonly UnmappedSignal[]>(
    `/api/v1/management/unmapped-signals${query}`,
    {},
  );
}

/**
 * Registra a decisão da triagem.
 *
 * <p>`IDENTIFIED` associa uma área existente (e, opcionalmente, um setor);
 * `NEEDS_MAPPING` pede um novo mapeamento; `DISCARDED` encerra sem ação e
 * `PENDING` reabre o registro. Decidir não publica bandeira no mapa.
 */
export async function decideUnmappedSignal(
  signalId: string,
  input: UnmappedSignalDecisionInput,
): Promise<UnmappedSignal> {
  return apiRequest<UnmappedSignal>(
    `/api/v1/management/unmapped-signals/${encodeURIComponent(signalId)}/decision`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        decision: input.decision,
        beachId: input.beachId ?? null,
        sectorId: input.sectorId ?? null,
        notes: input.notes?.trim() ? input.notes.trim() : null,
      }),
    },
  );
}

export function unmappedSignalErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao falar com o servidor.`)
    );
  }
  return "Não foi possível carregar a fila de áreas não identificadas agora.";
}
