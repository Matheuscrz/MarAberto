import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type { Occurrence } from "@/features/occurrences/types";

/**
 * Ocorrências visíveis para quem consulta.
 *
 * <p>O escopo é do servidor (RN15): o guarda-vidas recebe as do seu posto,
 * praia e as que ele mesmo assumiu; a coordenação recebe todas. A coordenada
 * do banhista vem nesta rota autenticada e não deve ser exibida fora do
 * console operacional.
 */
export async function fetchOccurrences(
  limit = 50,
): Promise<readonly Occurrence[]> {
  return apiRequest<readonly Occurrence[]>(
    `/api/v1/occurrences?limit=${limit}`,
    {},
  );
}

export function occurrenceErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "Sua sessão expirou. Entre novamente para ver as ocorrências.";
    }
    if (error.status === 403) {
      return "Seu perfil não tem acesso às ocorrências.";
    }
    return error.message;
  }
  return "Não foi possível carregar as ocorrências. Verifique a conexão.";
}
