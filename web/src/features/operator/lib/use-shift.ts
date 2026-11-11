import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/shared/lib/api-client";
import {
  GeolocationError,
  getDevicePosition,
} from "@/shared/lib/geolocation";
import { formatAccuracy } from "@/shared/lib/format";
import {
  closeShift as requestCloseShift,
  fetchCurrentShift,
  fetchShiftTargets,
  openShift as requestOpenShift,
  updateShiftPosition,
  type OpenShiftInput,
} from "@/features/operator/api/dispatch";
import type { ShiftSnapshot, ShiftTarget } from "@/features/operator/types";

/**
 * Enquanto o console está aberto, a posição do plantão é renovada a cada 5
 * minutos. O limiar do backend (`maraberto.dispatch.position-stale-after`) é
 * 30 min; renovar antes disso mantém a base móvel dentro da seleção espacial
 * sem transformar o painel em rastreamento contínuo.
 */
const POSITION_REFRESH_MS = 5 * 60 * 1000;

export type ShiftTone = "success" | "warning" | "error";

export interface ShiftFeedback {
  readonly tone: ShiftTone;
  readonly message: string;
}

export type ShiftBusy = "open" | "move" | "close" | null;

export interface ShiftConsole {
  readonly shift: ShiftSnapshot | null;
  readonly targets: readonly ShiftTarget[];
  readonly loading: boolean;
  readonly loadError: string | null;
  readonly busy: ShiftBusy;
  readonly feedback: ShiftFeedback | null;
  /** Abre o plantão; `true` quando o servidor confirmou. */
  open: (input: OpenShiftInput) => Promise<boolean>;
  /** Lê o aparelho e envia a posição atual do plantão. */
  refreshPosition: () => Promise<boolean>;
  close: () => Promise<boolean>;
  reload: () => void;
  dismissFeedback: () => void;
}

/**
 * Ciclo de vida do plantão no console do operador.
 *
 * <p>Abrir o plantão é o que torna o operador elegível para o SOS: a posição
 * informada (GPS do aparelho, com ajuste manual) é a última posição conhecida
 * usada pelo PostGIS. Depois disso o console oferece atualização sob demanda e
 * uma renovação periódica enquanto a aba está visível — nada é rastreado em
 * segundo plano e nenhuma coordenada é persistida no navegador.
 */
export function useShift(): ShiftConsole {
  const [shift, setShift] = useState<ShiftSnapshot | null>(null);
  const [targets, setTargets] = useState<readonly ShiftTarget[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState<ShiftBusy>(null);
  const [feedback, setFeedback] = useState<ShiftFeedback | null>(null);

  const mounted = useRef(true);
  /** Só renova sozinho se o GPS já foi autorizado em uma captura desta sessão. */
  const autoPosition = useRef(false);

  const load = useCallback(async () => {
    const [shiftResult, targetsResult] = await Promise.allSettled([
      fetchCurrentShift(),
      fetchShiftTargets(),
    ]);
    if (!mounted.current) return;

    if (shiftResult.status === "fulfilled") {
      setShift(shiftResult.value);
    }
    if (targetsResult.status === "fulfilled") {
      setTargets(targetsResult.value);
    }

    const failure =
      shiftResult.status === "rejected"
        ? shiftResult.reason
        : targetsResult.status === "rejected"
          ? targetsResult.reason
          : null;
    setLoadError(failure === null ? null : describeError(failure));
    setLoading(false);
  }, []);

  useEffect(() => {
    mounted.current = true;
    const initial = window.setTimeout(() => {
      void load();
    }, 0);

    return () => {
      mounted.current = false;
      window.clearTimeout(initial);
    };
  }, [load]);

  const shiftId = shift?.id ?? null;

  useEffect(() => {
    if (!shiftId) return;
    const timer = window.setInterval(() => {
      if (document.hidden || !autoPosition.current) return;
      void (async () => {
        try {
          const position = await getDevicePosition({ maximumAge: 60_000 });
          const updated = await updateShiftPosition(position);
          if (mounted.current) setShift(updated);
        } catch (error) {
          if (
            error instanceof GeolocationError &&
            error.reason === "denied"
          ) {
            autoPosition.current = false;
          }
        }
      })();
    }, POSITION_REFRESH_MS);

    return () => window.clearInterval(timer);
  }, [shiftId]);

  const open = useCallback(async (input: OpenShiftInput) => {
    setBusy("open");
    setFeedback(null);
    try {
      const created = await requestOpenShift(input);
      if (!mounted.current) return false;
      setShift(created);
      autoPosition.current = input.accuracyMeters !== undefined;
      setFeedback({
        tone: "success",
        message:
          "Plantão aberto. Enquanto ele estiver aberto o despacho pode escolher você para um SOS.",
      });
      return true;
    } catch (error) {
      if (mounted.current) {
        setFeedback({ tone: "error", message: describeError(error) });
      }
      return false;
    } finally {
      if (mounted.current) setBusy(null);
    }
  }, []);

  const refreshPosition = useCallback(async () => {
    setBusy("move");
    setFeedback(null);
    try {
      const position = await getDevicePosition();
      const updated = await updateShiftPosition(position);
      if (!mounted.current) return false;
      setShift(updated);
      autoPosition.current = true;
      setFeedback({
        tone: "success",
        message: `Posição atualizada (${formatAccuracy(position.accuracyMeters)}).`,
      });
      return true;
    } catch (error) {
      if (mounted.current) {
        setFeedback({ tone: "warning", message: describeError(error) });
      }
      return false;
    } finally {
      if (mounted.current) setBusy(null);
    }
  }, []);

  const close = useCallback(async () => {
    setBusy("close");
    setFeedback(null);
    try {
      await requestCloseShift();
      if (!mounted.current) return false;
      setShift(null);
      autoPosition.current = false;
      setFeedback({
        tone: "success",
        message:
          "Plantão encerrado. Você deixou de receber chamados do SOS até abrir um novo plantão.",
      });
      return true;
    } catch (error) {
      if (mounted.current) {
        setFeedback({ tone: "error", message: describeError(error) });
      }
      return false;
    } finally {
      if (mounted.current) setBusy(null);
    }
  }, []);

  const dismissFeedback = useCallback(() => setFeedback(null), []);

  return {
    shift,
    targets,
    loading,
    loadError,
    busy,
    feedback,
    open,
    refreshPosition,
    close,
    reload: () => {
      void load();
    },
    dismissFeedback,
  };
}

/** Mensagem segura: nunca repete corpo de resposta nem coordenada. */
function describeError(error: unknown): string {
  if (error instanceof GeolocationError) {
    return error.message;
  }
  if (error instanceof ApiError) {
    if (error.network) {
      return "Sem conexão com o servidor. Tente de novo em instantes.";
    }
    return (
      error.problem?.detail ??
      error.problem?.title ??
      `Falha (${error.status}) ao falar com o servidor.`
    );
  }
  return "Falha inesperada ao atualizar o plantão.";
}
