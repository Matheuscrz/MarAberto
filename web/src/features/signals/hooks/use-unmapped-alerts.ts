import { useCallback, useEffect, useState } from "react";
import {
  fetchUnmappedAlerts,
  signalErrorMessage,
} from "@/features/signals/api/signals";
import type { PublicUnmappedSignal } from "@/features/signals/types";

export type UnmappedAlertsStatus = "loading" | "ready" | "error";

export interface UnmappedAlertsState {
  readonly alerts: readonly PublicUnmappedSignal[];
  readonly status: UnmappedAlertsStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/** O alerta entra e sai sozinho: o mapa reconcilia em até 30 s (RN01). */
const POLL_MS = 30_000;

const EMPTY: readonly PublicUnmappedSignal[] = [];

/**
 * Alertas de sinalização lançada fora do mapeamento.
 *
 * <p>A leitura é pública e o servidor já filtra {@code PENDING} dentro da
 * validade — o cliente não decide vigência. O polling respeita a aba: escondida
 * não consulta; ao voltar, consulta na hora.
 */
export function useUnmappedAlerts(): UnmappedAlertsState {
  const [alerts, setAlerts] = useState<readonly PublicUnmappedSignal[]>(EMPTY);
  const [status, setStatus] = useState<UnmappedAlertsStatus>("loading");
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
        const next = await fetchUnmappedAlerts();
        if (cancelled) return;
        hasData = true;
        setAlerts(next);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        if (!hasData) setStatus("error");
        setMessage(signalErrorMessage(error));
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

  return { alerts, status, message, reload };
}
