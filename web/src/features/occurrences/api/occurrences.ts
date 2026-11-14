import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type {
  CloseOccurrenceInput,
  Occurrence,
  OccurrenceDetail,
  UpdateOccurrenceInput,
  VictimView,
} from "@/features/occurrences/types";

/**
 * Ocorrências visíveis para quem consulta.
 *
 * <p>O escopo é do servidor (RN15): o guarda-vidas recebe as que ele mesmo
 * atendeu; a coordenação recebe todas. A coordenada do banhista vem nesta rota
 * autenticada e não deve ser exibida fora do console operacional.
 */
export async function fetchOccurrences(
  limit = 50,
): Promise<readonly Occurrence[]> {
  return apiRequest<readonly Occurrence[]>(
    `/api/v1/occurrences?limit=${limit}`,
    {},
  );
}

export async function fetchOccurrence(
  occurrenceId: string,
): Promise<OccurrenceDetail> {
  return apiRequest<OccurrenceDetail>(
    `/api/v1/occurrences/${encodeURIComponent(occurrenceId)}`,
    {},
  );
}

export async function closeOccurrence(
  occurrenceId: string,
  input: CloseOccurrenceInput,
): Promise<OccurrenceDetail> {
  return apiRequest<OccurrenceDetail>(
    `/api/v1/occurrences/${encodeURIComponent(occurrenceId)}/close`,
    jsonPost(input),
  );
}

export async function updateOccurrence(
  occurrenceId: string,
  input: UpdateOccurrenceInput,
): Promise<OccurrenceDetail> {
  return apiRequest<OccurrenceDetail>(
    `/api/v1/occurrences/${encodeURIComponent(occurrenceId)}`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}

export async function revealOccurrenceVictim(
  occurrenceId: string,
  reason: string,
): Promise<VictimView> {
  return apiRequest<VictimView>(
    `/api/v1/occurrences/${encodeURIComponent(occurrenceId)}/victim/reveal`,
    jsonPost({ reason }),
  );
}

function jsonPost(body: unknown): RequestInit {
  return {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

export function occurrenceErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Sua sessão expirou. Entre novamente para ver as ocorrências.";
    }
    if (error.status === 403) {
      return "Seu perfil não tem acesso às ocorrências.";
    }
    if (error.status === 404) {
      return "Ocorrência não encontrada ou fora do seu escopo.";
    }
    if (error.status === 409) {
      return (
        error.problem?.detail ??
        "A ocorrência mudou no servidor. Atualize a ficha e tente de novo."
      );
    }
    return error.problem?.detail ?? error.message;
  }
  return "Não foi possível carregar as ocorrências. Verifique a conexão.";
}
