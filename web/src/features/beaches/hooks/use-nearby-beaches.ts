import { useCallback, useEffect, useState } from "react";
import {
  GeolocationError,
  getDevicePosition,
  type DevicePosition,
} from "@/shared/lib/geolocation";
import {
  beachErrorMessage,
  fetchNearbyBeaches,
} from "@/features/beaches/api/beaches";
import type { Beach, BeachPosition } from "@/features/beaches/types";

export type NearbyBeachesStatus =
  | "idle"
  | "locating"
  | "ready"
  | "empty"
  | "error";

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
  readonly locationConsent: boolean;
  readonly onRetryLocation: () => void;
}

export interface UseNearbyBeachesOptions {
  /**
   * Quando `false`, não dispara o prompt de localização no carregamento: aguarda
   * consentimento explícito via `retry()` ou `locateFromPosition()`.
   */
  readonly autoLocate?: boolean;
}

function initialState(autoLocate: boolean): NearbyBeachesState {
  return {
    status: autoLocate ? "locating" : "idle",
    beaches: [],
    position: null,
    message: null,
  };
}

/**
 * Praias num raio de até 30 km da posição atual.
 *
 * <p>A leitura é pontual e não vira rastreamento: o GPS é consultado uma vez
 * por tentativa e a coordenada só existe enquanto a requisição está em voo.
 * Negar a permissão não quebra a tela — a lista explica como liberar.
 */
export function useNearbyBeaches(options: UseNearbyBeachesOptions = {}) {
  const autoLocate = options.autoLocate ?? true;
  const [locationConsent, setLocationConsent] = useState(autoLocate);
  const [state, setState] = useState<NearbyBeachesState>(() =>
    initialState(autoLocate),
  );
  const [lastCoords, setLastCoords] = useState<DevicePosition | null>(null);
  const [attempt, setAttempt] = useState(autoLocate ? 1 : 0);

  const loadFromPosition = useCallback(async (coords: DevicePosition) => {
    setLocationConsent(true);
    setLastCoords(coords);
    setState((prev) => ({
      ...prev,
      status: "locating",
      message: null,
    }));
    try {
      const nearby = await fetchNearbyBeaches(coords);
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
  }, []);

  useEffect(() => {
    if (attempt === 0) return;
    let cancelled = false;

    void (async () => {
      try {
        const coords = await getDevicePosition({
          enableHighAccuracy: false,
          timeout: 8_000,
          maximumAge: 60_000,
        });
        if (cancelled) return;
        setLastCoords(coords);
        const nearby = await fetchNearbyBeaches(coords);
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
    setLocationConsent(true);
    setState({
      status: "locating",
      beaches: [],
      position: null,
      message: null,
    });
    setAttempt((value) => value + 1);
  }, []);

  return {
    ...state,
    locationConsent,
    lastCoords,
    retry,
    locateFromPosition: loadFromPosition,
  };
}
