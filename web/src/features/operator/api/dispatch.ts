import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type {
  DispatchAssignedCall,
  DispatchOpenCall,
  ShiftSnapshot,
  ShiftTarget,
} from "@/features/operator/types";

/**
 * Contrato do despacho do SOS (ADR-018) usado pelo console do operador.
 *
 * O banco decide quem recebe o chamado; aqui só listamos o que é do operador
 * autenticado e assumimos um chamado por vez. Coordenadas nunca vêm do
 * WebSocket — chegam por estas rotas autenticadas.
 */
export async function fetchOpenCalls(): Promise<readonly DispatchOpenCall[]> {
  return apiRequest<readonly DispatchOpenCall[]>("/api/v1/dispatch/calls", {
    method: "GET",
  });
}

/** Chamados que o operador assumiu e continuam em atendimento. */
export async function fetchAssignedCalls(): Promise<
  readonly DispatchAssignedCall[]
> {
  return apiRequest<readonly DispatchAssignedCall[]>(
    "/api/v1/dispatch/calls/assigned",
    { method: "GET" },
  );
}

/** Assunção por compare-and-set: 409 quando outro operador assumiu antes. */
export async function acknowledgeCall(callId: string): Promise<void> {
  await apiRequest<unknown>(
    `/api/v1/dispatch/calls/${encodeURIComponent(callId)}/ack`,
    { method: "POST" },
  );
}

/** Plantão aberto, ou `null` quando o operador não está de plantão. */
export async function fetchCurrentShift(): Promise<ShiftSnapshot | null> {
  try {
    return await apiRequest<ShiftSnapshot>("/api/v1/dispatch/shifts/current", {
      method: "GET",
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

/** Postos fixos e bases móveis ativos onde o operador pode abrir plantão. */
export async function fetchShiftTargets(): Promise<readonly ShiftTarget[]> {
  return apiRequest<readonly ShiftTarget[]>(
    "/api/v1/dispatch/shifts/targets",
    { method: "GET" },
  );
}

export interface OpenShiftInput {
  readonly postId?: string;
  readonly mobileBaseId?: string;
  readonly latitude?: number;
  readonly longitude?: number;
  readonly accuracyMeters?: number;
}

/** Abre o plantão; a posição informada vira a última posição conhecida. */
export async function openShift(
  input: OpenShiftInput,
): Promise<ShiftSnapshot> {
  return apiRequest<ShiftSnapshot>("/api/v1/dispatch/shifts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

/** Atualiza a última posição conhecida do plantão aberto. */
export async function updateShiftPosition(input: {
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters?: number;
}): Promise<ShiftSnapshot> {
  return apiRequest<ShiftSnapshot>(
    "/api/v1/dispatch/shifts/current/position",
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  );
}

/** Encerra o plantão; o operador deixa de ser elegível para o SOS. */
export async function closeShift(): Promise<ShiftSnapshot> {
  return apiRequest<ShiftSnapshot>("/api/v1/dispatch/shifts/current/close", {
    method: "POST",
  });
}
