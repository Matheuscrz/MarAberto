import { z } from "zod";
import { coordinatesSchema } from "@/shared/lib/validation";

/**
 * Contrato do SOS: identificador idempotente (UUID v4 gerado no cliente),
 * coordenadas válidas e precisão opcional.
 */
export const sosSchema = coordinatesSchema.extend({
  clientRequestId: z.string().uuid(),
});

export type SosInput = z.infer<typeof sosSchema>;

export function parseSosInput(value: unknown): SosInput {
  return sosSchema.parse(value);
}
