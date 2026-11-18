import { apiRequest } from "@/shared/lib/api-client";
import type { ManagementTeam } from "@/features/management/types";

/**
 * Equipe em plantão na visão da coordenação (RF40/ADR-026).
 *
 * <p>A rota é autenticada e `Cache-Control: no-store` no servidor; o cliente
 * também nunca persiste a posição — ela vive apenas no estado desta sessão.
 */
export async function fetchManagementTeam(): Promise<ManagementTeam> {
  return apiRequest<ManagementTeam>("/api/v1/management/team", {});
}
