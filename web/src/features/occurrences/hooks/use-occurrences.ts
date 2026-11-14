import { useCallback, useEffect, useState } from "react";
import {
  fetchOccurrences,
  occurrenceErrorMessage,
} from "@/features/occurrences/api/occurrences";
import type { Occurrence } from "@/features/occurrences/types";

export type OccurrencesStatus = "loading" | "ready" | "error";

export interface OccurrencesState {
  readonly occurrences: readonly Occurrence[];
  readonly status: OccurrencesStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/**
 * A ficha nasce no ack do SOS e o turno muda com o tempo: polling curto
 * enquanto o console está aberto, sem consultar em aba escondida.
 */
const POLL_MS = 30_000;

const EMPTY: readonly Occurrence[] = [];

/**
 * Ocorrências visíveis para o usuário logado.
 *
 * <p>O escopo é do servidor: o cliente nunca recebe o que não pode ver. Quando
 * a consulta falha depois de já haver dados, a lista continua na tela com o
 * aviso, em vez de sumir com as ocorrências do turno.
 */
export function useOccurrences(): OccurrencesState {
  const [occurrences, setOccurrences] =
    useState<readonly Occurrence[]>(EMPTY);
  const [status, setStatus] = useState<OccurrencesStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
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
        const next = await fetchOccurrences();
        if (cancelled) return;
        setOccurrences(next);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        setMessage(occurrenceErrorMessage(error));
        // Já carregado: mantém a lista e só sinaliza a falha da atualização.
        setStatus((current) => (current === "ready" ? "ready" : "error"));
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
    setStatus("loading");
    setMessage(null);
    setAttempt((value) => value + 1);
  }, []);

  return { occurrences, status, message, reload };
}
