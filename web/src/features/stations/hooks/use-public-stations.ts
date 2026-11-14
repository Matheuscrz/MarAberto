import { useCallback, useEffect, useState } from "react";
import {
  fetchPublicStations,
  stationErrorMessage,
} from "@/features/stations/api/stations";
import type { PublicStation } from "@/features/stations/types";

export type StationsStatus = "loading" | "ready" | "error";

export interface PublicStationsState {
  readonly stations: readonly PublicStation[];
  readonly status: StationsStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/** Desativar um posto ou encerrar um plantão reflete no mapa em até 30 s. */
const POLL_MS = 30_000;

/**
 * Estruturas visíveis no mapa público.
 *
 * <p>O servidor decide o que está visível (posto ativo, base com plantão vivo).
 * O polling é curto porque a base móvel se move e some quando o turno acaba;
 * com a aba escondida nada é consultado e ao voltar a leitura é imediata.
 */
export function usePublicStations(): PublicStationsState {
  const [stations, setStations] = useState<readonly PublicStation[]>([]);
  const [status, setStatus] = useState<StationsStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let timer = 0;
    let hasData = false;

    const schedule = () => {
      timer = window.setTimeout(() => {
        void run();
      }, POLL_MS);
    };

    const run = async () => {
      if (cancelled) return;
      if (document.hidden) {
        schedule();
        return;
      }
      try {
        const next = await fetchPublicStations();
        if (cancelled) return;
        hasData = true;
        setStations(next);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        if (!hasData) setStatus("error");
        setMessage(stationErrorMessage(error));
      }
      schedule();
    };

    void run();

    const onVisible = () => {
      if (document.hidden) return;
      window.clearTimeout(timer);
      void run();
    };

    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", onVisible);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", onVisible);
    };
  }, [attempt]);

  const reload = useCallback(() => {
    setStatus((current) => (current === "error" ? "loading" : current));
    setAttempt((value) => value + 1);
  }, []);

  return { stations, status, message, reload };
}
