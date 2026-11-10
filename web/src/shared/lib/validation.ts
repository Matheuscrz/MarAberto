import { z } from "zod";
import type { Coordinates } from "@/shared/types/geo";

/**
 * Coordenadas WGS84 aceitas pelo backend. A validação no cliente melhora a
 * mensagem; o backend revalida tudo e devolve RFC 9457 em caso de erro.
 */
export const coordinatesSchema = z.object({
  latitude: z.number().finite().min(-90).max(90),
  longitude: z.number().finite().min(-180).max(180),
  accuracyMeters: z.number().positive().max(100000).optional(),
});

export function parseCoordinates(value: unknown): Coordinates {
  return coordinatesSchema.parse(value);
}
