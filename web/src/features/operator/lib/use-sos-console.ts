import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/shared/lib/api-client";
import {
  acknowledgeCall,
  fetchAssignedCalls,
  fetchOpenCalls,
} from "@/features/operator/api/dispatch";
import {
  connectSosRealtime,
  type SosRealtimeState,
} from "@/features/operator/lib/sos-realtime";
import type {
  DispatchAssignedCall,
  DispatchOpenCall,
  SosNotification,
} from "@/features/operator/types";

/** Reconciliação por HTTP: o WebSocket perde frame, a fila do banco não. */
const POLL_INTERVAL_MS = 10_000;

export type ConsoleTone = "success" | "warning" | "error";

export interface ConsoleFeedback {
  readonly tone: ConsoleTone;
  readonly message: string;
}

export interface SosConsole {
  readonly calls: readonly DispatchOpenCall[];
  /** Chamados assumidos por você e ainda em atendimento. */
  readonly assigned: readonly DispatchAssignedCall[];
  readonly realtime: SosRealtimeState;
  /** Último frame recebido; o alerta usa o `DISPATCHED` sem esperar a fila. */
  readonly incoming: SosNotification | null;
  readonly loading: boolean;
  readonly loadError: string | null;
  readonly feedback: ConsoleFeedback | null;
  /** Chamado com ack em andamento (botão bloqueado). */
  readonly acknowledgingId: string | null;
  refresh: () => void;
  acknowledge: (callId: string) => void;
  dismissFeedback: () => void;
}

/**
 * Estado do console do SOS.
 *
 * <p>Três fontes, uma verdade: a fila é sempre a do banco
 * (`GET /api/v1/dispatch/calls`); o WebSocket só antecipa a atualização e o
 * polling de 10 s reconcilia quando o frame se perde. Nenhuma coordenada passa
 * pelo socket — o detalhe vem da rota autenticada.
 */
export function useSosConsole(): SosConsole {
  const [calls, setCalls] = useState<readonly DispatchOpenCall[]>([]);
  const [assigned, setAssigned] = useState<readonly DispatchAssignedCall[]>([]);
  const [realtime, setRealtime] = useState<SosRealtimeState>("connecting");
  const [incoming, setIncoming] = useState<SosNotification | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<ConsoleFeedback | null>(null);
  const [acknowledgingId, setAcknowledgingId] = useState<string | null>(null);

  const mounted = useRef(true);

  const load = useCallback(async () => {
    try {
      // A fila e o atendimento em curso vêm juntos: o chamado assumido não
      // pode piscar fora da tela entre o ack e a próxima reconciliação.
      const [openCalls, assignedCalls] = await Promise.all([
        fetchOpenCalls(),
        fetchAssignedCalls(),
      ]);
      if (!mounted.current) return;
      setCalls(openCalls);
      setAssigned(assignedCalls);
      setLoadError(null);
    } catch (error) {
      if (!mounted.current) return;
      setLoadError(describeError(error));
    } finally {
      if (mounted.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    // O primeiro carregamento sai do corpo do efeito (setTimeout 0): o estado
    // só muda depois do fetch, nunca em cascata com a montagem.
    const initial = window.setTimeout(() => {
      void load();
    }, 0);
    const timer = window.setInterval(() => {
      void load();
    }, POLL_INTERVAL_MS);

    return () => {
      mounted.current = false;
      window.clearTimeout(initial);
      window.clearInterval(timer);
    };
  }, [load]);

  useEffect(() => {
    const handle = connectSosRealtime({
      onNotification: (notification) => {
        setIncoming(notification);
        if (notification.status === "CANCELLED") {
          // Outro operador assumiu: o frame redigido basta para tirar da fila.
          setCalls((previous) =>
            previous.filter(
              (call) => call.dispatchId !== notification.dispatchId,
            ),
          );
          return;
        }
        void load();
      },
      onStateChange: setRealtime,
    });

    return () => handle.stop();
  }, [load]);

  const acknowledge = useCallback((callId: string) => {
    setAcknowledgingId(callId);
    const run = async () => {
      try {
        await acknowledgeCall(callId);
        if (!mounted.current) return;
        setCalls((previous) =>
          previous.filter((call) => call.callId !== callId),
        );
        setFeedback({
          tone: "success",
          message:
            "Chamado assumido. Ele continua aqui em atendimento, com a rota até o banhista.",
        });
        // O atendimento em curso vem do servidor (horário do ack e status); a
        // recarga imediata evita a janela em que o chamado não está em lista
        // nenhuma.
        void load();
      } catch (error) {
        if (!mounted.current) return;
        if (error instanceof ApiError && error.status === 409) {
          // Perdeu a corrida do compare-and-set: o chamado é de outro.
          setCalls((previous) =>
            previous.filter((call) => call.callId !== callId),
          );
          void load();
          setFeedback({
            tone: "warning",
            message:
              "Outro guarda-vidas assumiu este chamado antes. Ele saiu da sua fila.",
          });
          return;
        }
        if (error instanceof ApiError && error.status === 403) {
          setFeedback({
            tone: "error",
            message: "Este chamado não está atribuído a você.",
          });
          return;
        }
        setFeedback({ tone: "error", message: describeError(error) });
      } finally {
        if (mounted.current) setAcknowledgingId(null);
      }
    };

    void run();
  }, [load]);

  const dismissFeedback = useCallback(() => setFeedback(null), []);

  return {
    calls,
    assigned,
    realtime,
    incoming,
    loading,
    loadError,
    feedback,
    acknowledgingId,
    refresh: () => {
      void load();
    },
    acknowledge,
    dismissFeedback,
  };
}

/** Mensagem segura: nunca repete corpo de resposta nem dado do chamado. */
function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.network) {
      return "Sem conexão com o servidor. O console tenta de novo em instantes.";
    }
    return (
      error.problem?.detail ??
      error.problem?.title ??
      `Falha (${error.status}) ao consultar a fila do SOS.`
    );
  }
  return "Falha inesperada ao consultar a fila do SOS.";
}
