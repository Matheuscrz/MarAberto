import { useCallback, useEffect, useState } from "react";
import {
  fetchFieldStations,
  stationErrorMessage,
} from "@/features/stations/api/stations";
import type { AdminStation } from "@/features/stations/types";

export type FieldStationsStatus = "loading" | "ready" | "error";

export interface FieldStationsState {
  readonly stations: readonly AdminStation[];
  readonly status: FieldStationsStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/** Cadastro muda pouco, mas a desativação em campo precisa refletir rápido. */
const POLL_MS = 30_000;

/**
 * Estruturas do console de campo, inclusive a base móvel sem plantão vivo.
 *
 * <p>A lista pública (`/api/v1/stations`) só mostra a base com turno aberto;
 * esta mantém o cadastro visível para o guarda-vidas localizar o posto/base e
 * entender por que ele saiu do mapa. A aba escondida não consulta e ao voltar a
 * leitura é imediata.
 */
export function useFieldStations(): FieldStationsState {
  const [stations, setStations] = useState<readonly AdminStation[]>([]);
  const [status, setStatus] = useState<FieldStationsStatus>("loading");
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
        const next = await fetchFieldStations();
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
