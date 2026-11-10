import { apiRequest } from "@/shared/lib/api-client";
import type { SosPayload, SosResponse } from "@/features/sos/types";

/**
 * Registra um SOS. O timeout é curto de propósito: se o servidor não confirmar,
 * a interface orienta a ligar 193 em vez de fingir que o chamado foi enviado.
 */
export function createEmergencyCall(payload: SosPayload): Promise<SosResponse> {
  return apiRequest<SosResponse>(
    "/api/v1/emergency-calls",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    },
    5000,
  );
}
