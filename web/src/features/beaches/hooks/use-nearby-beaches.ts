import { useCallback, useEffect, useState } from "react";
import {
  GeolocationError,
  getDevicePosition,
} from "@/shared/lib/geolocation";
import {
  beachErrorMessage,
  fetchNearbyBeaches,
} from "@/features/beaches/api/beaches";
import type { Beach, BeachPosition } from "@/features/beaches/types";

export type NearbyBeachesStatus = "locating" | "ready" | "empty" | "error";

export interface NearbyBeachesState {
  readonly status: NearbyBeachesStatus;
  readonly beaches: readonly Beach[];
  /** Área e setor que contêm a posição, ou `null` fora de área mapeada. */
  readonly position: BeachPosition | null;
  readonly message: string | null;
}

/**
 * Contrato do painel flutuante para as telas de praia.
 *
 * <p>Fica na feature (e não no layout do app) para `praias` não importar de
 * `app/`: a composição é que conhece a feature, nunca o contrário.
 */
export interface BeachesOutletContext extends NearbyBeachesState {
  readonly onRetryLocation: () => void;
}

const INITIAL_STATE: NearbyBeachesState = {
  status: "locating",
  beaches: [],
  position: null,
  message: null,
};

/**
 * Praias num raio de até 30 km da posição atual.
 *
 * <p>A leitura é pontual e não vira rastreamento: o GPS é consultado uma vez
 * por tentativa e a coordenada só existe enquanto a requisição está em voo.
 * Negar a permissão não quebra a tela — a lista explica como liberar.
 */
export function useNearbyBeaches() {
  const [state, setState] = useState<NearbyBeachesState>(INITIAL_STATE);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      try {
        const position = await getDevicePosition({
          enableHighAccuracy: false,
          timeout: 8_000,
          maximumAge: 60_000,
        });
        const nearby = await fetchNearbyBeaches(position);
        if (cancelled) return;
        setState({
          status: nearby.beaches.length > 0 ? "ready" : "empty",
          beaches: nearby.beaches,
          position: nearby.position,
          message:
            nearby.beaches.length > 0
              ? null
              : "Nenhuma praia mapeada num raio de 30 km da sua posição.",
        });
      } catch (error) {
        if (cancelled) return;
        setState({
          status: "error",
          beaches: [],
          position: null,
          message:
            error instanceof GeolocationError
              ? error.message
              : beachErrorMessage(error),
        });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const retry = useCallback(() => {
    setState(INITIAL_STATE);
    setAttempt((value) => value + 1);
  }, []);

  return { ...state, retry };
}
