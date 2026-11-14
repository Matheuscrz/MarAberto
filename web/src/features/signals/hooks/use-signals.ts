import { useCallback, useEffect, useState } from "react";
import {
  fetchActiveSignals,
  fetchBeachSignals,
  signalErrorMessage,
} from "@/features/signals/api/signals";
import type { BeachFlagSignal } from "@/features/signals/types";

export type SignalsStatus = "loading" | "ready" | "error";

export interface SignalsState {
  readonly signals: readonly BeachFlagSignal[];
  readonly status: SignalsStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/** O mapa reflete lançamento e vencimento em até 30 s (RN01). */
const MAP_POLL_MS = 30_000;
/** A ficha da praia muda menos: o polling é só para acompanhar o turno. */
const BEACH_POLL_MS = 60_000;

const EMPTY: readonly BeachFlagSignal[] = [];
const MAP_KEY = "__map__";

interface LoadedSignals {
  readonly key: string;
  readonly signals: readonly BeachFlagSignal[];
  readonly status: Exclude<SignalsStatus, "loading">;
  readonly message: string | null;
}

/**
 * Sinalizações vigentes de todas as áreas, para os marcadores do mapa.
 *
 * <p>A leitura é pública e o polling respeita a aba: escondida, o cliente não
 * consulta; ao voltar, consulta na hora. A vigência continua sendo decisão do
 * servidor — o cliente nunca recebe bandeira vencida nesta rota.
 */
export function useSignals(): SignalsState {
  return useSignalSource(null, MAP_POLL_MS, true);
}

/**
 * Último lançamento de cada escopo da praia, para a ficha.
 *
 * <p>Inclui vencido e encerrado: a ficha mostra a última cor com o selo de
 * "desatualizada" em vez de apagar a sinalização da tela.
 */
export function useBeachSignals(beachId: string | null): SignalsState {
  return useSignalSource(beachId, BEACH_POLL_MS, beachId !== null);
}

function useSignalSource(
  beachId: string | null,
  intervalMs: number,
  enabled: boolean,
): SignalsState {
  // Os dados guardam a chave da consulta que os produziu: trocar de praia nunca
  // mostra, nem por um quadro, a bandeira da praia anterior.
  const key = beachId ?? MAP_KEY;
  const [loaded, setLoaded] = useState<LoadedSignals | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!enabled) return;

    let cancelled = false;
    let timer = 0;

    const schedule = () => {
      timer = window.setTimeout(() => {
        void run();
      }, intervalMs);
    };

    const run = async () => {
      if (cancelled) return;
      // Aba escondida não consulta: o próximo contato acontece ao voltar.
      if (document.hidden) {
        schedule();
        return;
      }
      try {
        const next =
          beachId === null
            ? await fetchActiveSignals()
            : await fetchBeachSignals(beachId);
        if (cancelled) return;
        setLoaded({ key, signals: next, status: "ready", message: null });
      } catch (error) {
        if (cancelled) return;
        const message = signalErrorMessage(error);
        setLoaded((current) =>
          current?.key === key && current.status === "ready"
            ? { ...current, message }
            : { key, signals: EMPTY, status: "error", message },
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
  }, [beachId, key, intervalMs, enabled, attempt]);

  const reload = useCallback(() => setAttempt((value) => value + 1), []);

  const current = loaded?.key === key ? loaded : null;

  return {
    signals: enabled ? (current?.signals ?? EMPTY) : EMPTY,
    status: enabled ? (current?.status ?? "loading") : "ready",
    message: enabled ? (current?.message ?? null) : null,
    reload,
  };
}
