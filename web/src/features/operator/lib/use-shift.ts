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
  heartbeatShift,
  openShift as requestOpenShift,
  updateShiftPosition,
  type OpenShiftInput,
} from "@/features/operator/api/dispatch";
import type { ShiftSnapshot, ShiftTarget } from "@/features/operator/types";

/**
 * Sinal de vida do plantão: a cada 5 minutos o console faz **uma** chamada.
 *
 * <p>Se o GPS já está autorizado, essa chamada é a atualização de posição (que
 * também renova o lease e mantém o limiar de 30 min de `position-stale-after`);
 * sem GPS, é o batimento vazio de `204`. Assim o plantão não expira na web nem
 * no app sem transformar o painel em rastreamento contínuo — 12 requisições por
 * hora, no máximo.
 */
const SIGNAL_INTERVAL_MS = 5 * 60 * 1000;

/** Jitter para 50 consoles abertos não baterem no servidor no mesmo segundo. */
const SIGNAL_JITTER_MS = 45 * 1000;

const IDLE_EXPIRED_MESSAGE =
  "O plantão foi encerrado por inatividade: o console ficou sem contato com o servidor. Abra um novo plantão para voltar a receber chamados.";

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
 * um sinal de vida a cada 5 minutos enquanto a aba está visível — nada é
 * rastreado em segundo plano e nenhuma coordenada é persistida no navegador.
 *
 * <p>O sinal de vida é o que sustenta o plantão: se o app é fechado à força, o
 * aparelho desliga ou a rede cai, o servidor encerra o turno por inatividade
 * (ADR-018). Ao voltar, o console revalida o plantão e avisa quando ele caiu.
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
  /** Último plantão conhecido, para detectar que ele caiu entre duas leituras. */
  const shiftRef = useRef<ShiftSnapshot | null>(null);

  /**
   * O plantão deixou de existir no servidor (expirou por inatividade ou foi
   * encerrado em outro aparelho). O console precisa dizer isso em vez de
   * continuar mostrando um turno que não recebe mais chamados.
   */
  const markExpired = useCallback(() => {
    if (!mounted.current) return;
    autoPosition.current = false;
    shiftRef.current = null;
    setShift(null);
    setFeedback({ tone: "warning", message: IDLE_EXPIRED_MESSAGE });
  }, []);

  const load = useCallback(async () => {
    const [shiftResult, targetsResult] = await Promise.allSettled([
      fetchCurrentShift(),
      fetchShiftTargets(),
    ]);
    if (!mounted.current) return;

    if (shiftResult.status === "fulfilled") {
      const next = shiftResult.value;
      if (next === null && shiftRef.current !== null) {
        // O plantão caiu enquanto o console estava aberto.
        markExpired();
      } else {
        shiftRef.current = next;
        setShift(next);
      }
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
  }, [markExpired]);

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

  // Espelho do plantão atual, usado para detectar que ele caiu entre leituras.
  useEffect(() => {
    shiftRef.current = shift;
  }, [shift]);

  // Um único laço de sinal: posição quando o GPS está autorizado, batimento
  // quando não está. Aba escondida não envia nada — é justamente o caso que o
  // lease do servidor resolve (o turno expira sozinho).
  useEffect(() => {
    if (!shiftId) return;
    let timer = 0;
    let cancelled = false;

    const schedule = () => {
      const delay = SIGNAL_INTERVAL_MS + Math.round(Math.random() * SIGNAL_JITTER_MS);
      timer = window.setTimeout(() => {
        void run();
      }, delay);
    };

    const run = async () => {
      if (cancelled) return;
      if (document.hidden) {
        schedule();
        return;
      }
      try {
        if (autoPosition.current) {
          const position = await getDevicePosition({ maximumAge: 60_000 });
          const updated = await updateShiftPosition(position);
          if (!cancelled && mounted.current) setShift(updated);
        } else {
          await heartbeatShift();
        }
      } catch (error) {
        if (error instanceof GeolocationError && error.reason === "denied") {
          // Sem GPS o plantão continua: o próximo ciclo só bate o ponto.
          autoPosition.current = false;
        } else if (error instanceof ApiError && error.status === 404) {
          markExpired();
          return;
        }
      }
      if (!cancelled) schedule();
    };

    schedule();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [shiftId, markExpired]);

  // Voltou do segundo plano: revalida o plantão na hora. A leitura também é
  // sinal de vida, então quem voltou dentro do prazo continua de plantão sem
  // esperar o próximo ciclo; quem passou do prazo recebe o aviso.
  useEffect(() => {
    if (!shiftId) return;

    const resync = async () => {
      if (document.hidden) return;
      try {
        const current = await fetchCurrentShift();
        if (!mounted.current) return;
        if (current === null) {
          markExpired();
          return;
        }
        setShift(current);
      } catch {
        // Falha de rede: o lease do servidor decide no próximo contato.
      }
    };

    const onVisibilityChange = () => {
      if (!document.hidden) void resync();
    };

    document.addEventListener("visibilitychange", onVisibilityChange);
    window.addEventListener("online", onVisibilityChange);
    return () => {
      document.removeEventListener("visibilitychange", onVisibilityChange);
      window.removeEventListener("online", onVisibilityChange);
    };
  }, [shiftId, markExpired]);

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
