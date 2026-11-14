import { useCallback, useEffect, useState } from "react";
import {
  fetchUnmappedSignals,
  unmappedSignalErrorMessage,
} from "@/features/management/api/unmapped-signals";
import type { UnmappedSignal } from "@/features/signals";

export type UnmappedSignalsFilter = "PENDING" | "ALL";

export type UnmappedSignalsStatus = "loading" | "ready" | "error";

export interface UnmappedSignalsState {
  readonly signals: readonly UnmappedSignal[];
  readonly status: UnmappedSignalsStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/** O registro chega em campo o dia inteiro: polling curto com a aba visível. */
const POLL_MS = 30_000;

const EMPTY: readonly UnmappedSignal[] = [];

/**
 * Fila de triagem da coordenação.
 *
 * <p>O filtro é do servidor e a lista carregada guarda a chave que a produziu:
 * trocar entre "pendentes" e "todos" nunca mostra, nem por um quadro, o
 * resultado do filtro anterior.
 */
export function useUnmappedSignals(
  filter: UnmappedSignalsFilter = "PENDING",
): UnmappedSignalsState {
  const [loaded, setLoaded] = useState<{
    readonly key: UnmappedSignalsFilter;
    readonly signals: readonly UnmappedSignal[];
    readonly status: Exclude<UnmappedSignalsStatus, "loading">;
    readonly message: string | null;
  } | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let timer = 0;

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
        const next = await fetchUnmappedSignals(
          filter === "ALL" ? undefined : filter,
        );
        if (cancelled) return;
        setLoaded({ key: filter, signals: next, status: "ready", message: null });
      } catch (error) {
        if (cancelled) return;
        const message = unmappedSignalErrorMessage(error);
        setLoaded((current) =>
          current?.key === filter && current.status === "ready"
            ? { ...current, message }
            : { key: filter, signals: EMPTY, status: "error", message },
        );
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
  }, [filter, attempt]);

  const reload = useCallback(() => {
    setLoaded(null);
    setAttempt((value) => value + 1);
  }, []);

  const current = loaded?.key === filter ? loaded : null;

  return {
    signals: current?.signals ?? EMPTY,
    status: current?.status ?? "loading",
    message: current?.message ?? null,
    reload,
  };
}
