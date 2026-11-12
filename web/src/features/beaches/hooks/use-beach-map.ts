import { useEffect, useReducer, useState } from "react";
import { fetchBeachMap } from "@/features/beaches/api/beaches";
import type { BeachMap } from "@/features/beaches/types";

/**
 * Geometria da praia selecionada.
 *
 * <p>O cache é por área e vive na memória do módulo: voltar para a mesma praia
 * não baixa o polígono de novo. A geometria só muda quando um lote de
 * importação é aplicado, então não há motivo para expirar em segundos.
 */
const cache = new Map<string, BeachMap>();

export interface BeachMapState {
  readonly map: BeachMap | null;
  readonly loading: boolean;
  readonly error: unknown;
}

export function useBeachMap(beachId: string | null): BeachMapState {
  // O cache é externo ao React: o reducer só força o render quando o download
  // termina, sem espelhar o cache em estado.
  const [, reload] = useReducer((value: number) => value + 1, 0);
  const [failure, setFailure] = useState<{ beachId: string; error: unknown } | null>(
    null,
  );

  useEffect(() => {
    if (!beachId || cache.has(beachId)) return;

    let cancelled = false;
    void (async () => {
      try {
        const map = await fetchBeachMap(beachId);
        cache.set(beachId, map);
        if (!cancelled) reload();
      } catch (caught) {
        if (!cancelled) setFailure({ beachId, error: caught });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [beachId]);

  const map = beachId ? (cache.get(beachId) ?? null) : null;
  const error = failure?.beachId === beachId ? failure.error : null;

  return {
    map,
    loading: Boolean(beachId) && map === null && error === null,
    error,
  };
}
