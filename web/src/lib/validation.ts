import { z } from 'zod';

/**
 * Coordenadas WGS84 aceitas pelo backend. A validação no cliente melhora a
 * mensagem; o backend revalida tudo e devolve RFC 9457 em caso de erro.
 */
export const coordinatesSchema = z.object({
  latitude: z.number().finite().min(-90).max(90),
  longitude: z.number().finite().min(-180).max(180),
  accuracyMeters: z.number().positive().max(100000).optional(),
});

/**
 * Contrato do SOS: identificador idempotente (UUID v4 gerado no cliente),
 * coordenadas válidas e precisão opcional.
 */
export const sosSchema = coordinatesSchema.extend({
  clientRequestId: z.string().uuid(),
});

export type Coordinates = z.infer<typeof coordinatesSchema>;
export type SosInput = z.infer<typeof sosSchema>;

export function parseCoordinates(value: unknown): Coordinates {
  return coordinatesSchema.parse(value);
}

export function parseSosInput(value: unknown): SosInput {
  return sosSchema.parse(value);
}
