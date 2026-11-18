import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type {
  CloseOccurrenceInput,
  Occurrence,
  OccurrenceDetail,
  OccurrencePage,
  OccurrenceStatus,
  UpdateOccurrenceInput,
  VictimView,
} from "@/features/occurrences/types";

export interface OccurrenceQuery {
  /** Tamanho da página; o servidor usa 20 quando omitido e limita a 100. */
  readonly limit?: number;
  readonly status?: OccurrenceStatus;
  /** Só o que ainda pede ação: atendimento aberto/em curso ou CIOSP pendente. */
  readonly active?: boolean;
  /** Localiza a ficha aberta pelo ack de um chamado do SOS. */
  readonly emergencyCallId?: string;
  readonly beforeOccurredAt?: string;
  readonly beforeNumber?: number;
}

/**
 * Ocorrências visíveis para quem consulta.
 *
 * <p>O escopo é do servidor (RN15): o guarda-vidas recebe as que ele mesmo
 * atendeu; a coordenação recebe todas. A coordenada do banhista vem nesta rota
 * autenticada e não deve ser exibida fora do console operacional. A paginação
 * é por cursor: devolva `nextCursor` como `beforeOccurredAt`/`beforeNumber`.
 */
export async function fetchOccurrences(
  query: OccurrenceQuery = {},
): Promise<OccurrencePage> {
  return apiRequest<OccurrencePage>(
    `/api/v1/occurrences?${queryString(query)}`,
    {},
  );
}

/**
 * Ficha da ocorrência aberta pelo ack de um chamado do SOS.
 *
 * <p>É o que liga o card "Em atendimento por você" à ocorrência que precisa ser
 * encerrada na própria tela do SOS. `null` quando o chamado ainda não gerou
 * ocorrência (o ack pode ter acontecido antes do listener).
 */
export async function fetchOccurrenceByEmergencyCall(
  emergencyCallId: string,
): Promise<Occurrence | null> {
  const page = await fetchOccurrences({ emergencyCallId, limit: 1 });
  return page.items[0] ?? null;
}

function queryString(query: OccurrenceQuery): string {
  const params = new URLSearchParams();
  params.set("limit", String(query.limit ?? 20));
  if (query.status) params.set("status", query.status);
  if (query.active) params.set("active", "true");
  if (query.emergencyCallId) {
    params.set("emergencyCallId", query.emergencyCallId);
  }
  if (query.beforeOccurredAt && query.beforeNumber !== undefined) {
    params.set("beforeOccurredAt", query.beforeOccurredAt);
    params.set("beforeNumber", String(query.beforeNumber));
  }
  return params.toString();
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
