import { useCallback, useEffect, useState } from "react";
import { ApiError, apiRequest } from "@/shared/lib/api-client";
import type { RiskZoneCollection } from "@/features/beaches/types";

export type RiskZonesStatus = "loading" | "ready" | "error";

export interface RiskZonesState {
  readonly zones: RiskZoneCollection;
  readonly status: RiskZonesStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/** Zona de risco muda em importação/manutenção, não a cada minuto. */
const POLL_MS = 60_000;

const EMPTY: RiskZoneCollection = { type: "FeatureCollection", features: [] };

/**
 * Zonas de risco visíveis no mapa (RF02/RF18).
 *
 * <p>A coleção chega pronta do servidor, com a cor por tipo de risco. Zona
 * permanente nunca expira; semipermanente some ao vencer — o cliente não
 * recebe a vencida, então não há filtro de validade aqui.
 */
export function useRiskZones(): RiskZonesState {
  const [zones, setZones] = useState<RiskZoneCollection>(EMPTY);
  const [status, setStatus] = useState<RiskZonesStatus>("loading");
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
        const next = await apiRequest<RiskZoneCollection>(
          "/api/v1/risk-zones",
          {},
        );
        if (cancelled) return;
        hasData = true;
        setZones(next);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        if (!hasData) setStatus("error");
        setMessage(riskZonesErrorMessage(error));
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

  return { zones, status, message, reload };
}

function riskZonesErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao falar com o servidor.`)
    );
  }
  return "Não foi possível carregar as zonas de risco.";
}
