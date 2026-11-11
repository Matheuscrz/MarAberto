import { useCallback, useEffect, useRef, useState } from "react";
import { formatDistance } from "@/shared/lib/format";
import {
  armSosAlerts,
  closeSosNotification,
  isAlertAudioReady,
  keepScreenAwake,
  readAlertPermission,
  releaseScreenAwake,
  showSosNotification,
  startSosAlarm,
  unlockAlertAudio,
  type AlarmHandle,
  type AlertPermission,
} from "@/features/operator/lib/sos-alarm";
import type {
  DispatchOpenCall,
  SosNotification,
} from "@/features/operator/types";

/**
 * Depois disso a sirene para sozinha (o popup continua na tela). Um alarme
 * tocando para sempre em uma mesa vazia não ajuda ninguém.
 */
const ALARM_MAX_MS = 5 * 60 * 1000;

export interface SosAlert {
  readonly callId: string;
  readonly dispatchId: string;
  readonly startedAt: string;
  readonly muted: boolean;
}

export interface SosAlertConsole {
  readonly alert: SosAlert | null;
  readonly permission: AlertPermission;
  readonly soundReady: boolean;
  readonly activating: boolean;
  /** Arma som + notificação no gesto do usuário; devolve a permissão final. */
  activate: () => Promise<AlertPermission>;
  /** Para a sirene mantendo o popup (o chamado continua na fila). */
  mute: () => void;
  /** Fecha o popup e a notificação; o card continua na lista. */
  dismiss: () => void;
  /** Liga a sirene de novo, quando o usuário libera o áudio depois. */
  play: () => void;
}

interface UseSosAlertInput {
  readonly calls: readonly DispatchOpenCall[];
  readonly incoming: SosNotification | null;
}

/**
 * Alerta do SOS: popup, sirene e notificação do sistema.
 *
 * <p>Duas portas de entrada, um único alarme: o frame STOMP antecipa (alerta
 * em milissegundos) e a fila HTTP cobre frame perdido ou página recarregada.
 * Cada `dispatchId` alerta uma vez; rodada nova do mesmo chamado alerta de
 * novo. Quando o chamado sai da fila (outro assumiu, cancelou ou foi
 * atendido), o alerta fecha sozinho.
 */
export function useSosAlert({
  calls,
  incoming,
}: UseSosAlertInput): SosAlertConsole {
  const [alert, setAlert] = useState<SosAlert | null>(null);
  const [permission, setPermission] = useState<AlertPermission>(() =>
    readAlertPermission(),
  );
  const [soundReady, setSoundReady] = useState(() => isAlertAudioReady());
  const [activating, setActivating] = useState(false);

  const alarm = useRef<AlarmHandle | null>(null);
  /** Chaves `callId:dispatchId` já alertadas nesta sessão do console. */
  const alerted = useRef(new Set<string>());
  /** Chamados que já apareceram na fila HTTP (para saber quando saíram). */
  const seenInQueue = useRef(new Set<string>());
  const callsRef = useRef(calls);
  useEffect(() => {
    callsRef.current = calls;
  }, [calls]);

  const raise = useCallback((callId: string, dispatchId: string) => {
    const key = `${callId}:${dispatchId}`;
    if (alerted.current.has(key)) {
      return;
    }
    alerted.current.add(key);
    setAlert({
      callId,
      dispatchId,
      startedAt: new Date().toISOString(),
      muted: false,
    });
  }, []);

  // Frame STOMP: alerta imediato, antes de a fila HTTP responder.
  useEffect(() => {
    if (!incoming || incoming.status !== "DISPATCHED") {
      return;
    }
    raise(incoming.callId, incoming.dispatchId);
  }, [incoming, raise]);

  // Fila HTTP: cobre frame perdido, reconexão e recarga da página.
  useEffect(() => {
    if (alert !== null || calls.length === 0) {
      return;
    }
    const candidate = calls
      .filter((call) => call.status === "DISPATCHED" || call.status === "PENDING")
      .filter((call) => !alerted.current.has(`${call.callId}:${call.dispatchId}`))
      .sort(byProximity)[0];
    if (candidate) {
      raise(candidate.callId, candidate.dispatchId);
    }
  }, [calls, alert, raise]);

  // Saiu da fila (assumido por outro, cancelado, atendido) → fecha o alerta.
  useEffect(() => {
    if (
      alert &&
      incoming?.status === "CANCELLED" &&
      incoming.dispatchId === alert.dispatchId
    ) {
      // Outro operador assumiu antes do primeiro refresh: o frame cancelado é a
      // única notícia que este console recebe, então ele fecha o popup. O
      // fechamento sai do corpo do efeito para não encadear render.
      const close = window.setTimeout(() => setAlert(null), 0);
      return () => window.clearTimeout(close);
    }
    for (const call of calls) {
      seenInQueue.current.add(call.callId);
    }
    if (!alert) {
      return;
    }
    const wasInQueue = seenInQueue.current.has(alert.callId);
    const stillInQueue = calls.some((call) => call.callId === alert.callId);
    if (wasInQueue && !stillInQueue) {
      setAlert(null);
    }
  }, [calls, alert, incoming]);

  const activeCallId = alert?.callId ?? null;
  const muted = alert?.muted ?? false;

  // Sirene + notificação do alerta ativo.
  useEffect(() => {
    if (!activeCallId) {
      return;
    }
    const call = callsRef.current.find((item) => item.callId === activeCallId);
    if (!muted) {
      alarm.current = startSosAlarm();
    }
    const cap = muted
      ? undefined
      : window.setTimeout(() => {
          alarm.current?.stop();
          alarm.current = null;
        }, ALARM_MAX_MS);

    void showSosNotification({
      callId: activeCallId,
      ...(call
        ? { distanceLabel: formatDistance(call.distanceMeters) }
        : {}),
    });
    // O `resume()` do áudio é assíncrono: o estado real só aparece depois.
    const readyCheck = window.setTimeout(
      () => setSoundReady(isAlertAudioReady()),
      150,
    );

    return () => {
      window.clearTimeout(readyCheck);
      if (cap !== undefined) {
        window.clearTimeout(cap);
      }
      alarm.current?.stop();
      alarm.current = null;
      void closeSosNotification(activeCallId);
    };
  }, [activeCallId, muted]);

  // O chamado pode chegar pelo frame antes da fila; quando a distância
  // aparece, a notificação do sistema ainda está sem ela. Não reexibe para não
  // duplicar alerta sonoro — o popup já mostra o número atualizado.

  // Tela ligada enquanto o alerta está na frente. O navegador solta a
  // sentinela ao ocultar a aba; por isso readquire quando ela volta.
  useEffect(() => {
    if (!activeCallId) {
      return;
    }
    void keepScreenAwake();
    const handleVisibility = () => {
      if (document.visibilityState === "visible") {
        void keepScreenAwake();
      }
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      document.removeEventListener("visibilitychange", handleVisibility);
      void releaseScreenAwake();
    };
  }, [activeCallId]);

  useEffect(() => {
    const updatePermission = () => setPermission(readAlertPermission());
    document.addEventListener("visibilitychange", updatePermission);
    return () =>
      document.removeEventListener("visibilitychange", updatePermission);
  }, []);

  // Primeiro toque no console também libera o áudio (sem pedir notificação).
  useEffect(() => {
    const unlock = () => {
      unlockAlertAudio();
      window.setTimeout(() => setSoundReady(isAlertAudioReady()), 150);
      window.removeEventListener("pointerdown", unlock);
    };
    window.addEventListener("pointerdown", unlock);
    return () => window.removeEventListener("pointerdown", unlock);
  }, []);

  const activate = useCallback(async () => {
    setActivating(true);
    try {
      const result = await armSosAlerts();
      setPermission(result.permission);
      setSoundReady(result.sound || isAlertAudioReady());
      return result.permission;
    } finally {
      setActivating(false);
    }
  }, []);

  const mute = useCallback(() => {
    setAlert((current) => (current ? { ...current, muted: true } : current));
  }, []);

  const dismiss = useCallback(() => setAlert(null), []);

  const play = useCallback(() => {
    unlockAlertAudio();
    setAlert((current) => (current ? { ...current, muted: false } : current));
    window.setTimeout(() => setSoundReady(isAlertAudioReady()), 150);
  }, []);

  return {
    alert,
    permission,
    soundReady,
    activating,
    activate,
    mute,
    dismiss,
    play,
  };
}

/** Mais próximo primeiro; sem distância vai para o fim. */
function byProximity(a: DispatchOpenCall, b: DispatchOpenCall): number {
  const first = a.distanceMeters ?? Number.POSITIVE_INFINITY;
  const second = b.distanceMeters ?? Number.POSITIVE_INFINITY;
  if (first !== second) {
    return first - second;
  }
  return a.createdAt.localeCompare(b.createdAt);
}
