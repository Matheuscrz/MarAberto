import { useCallback, useEffect, useState } from "react";
import {
  fetchOccurrences,
  occurrenceErrorMessage,
} from "@/features/occurrences/api/occurrences";
import type {
  Occurrence,
  OccurrenceCursor,
} from "@/features/occurrences/types";

export type OccurrencesStatus = "loading" | "ready" | "error";

export interface OccurrencesState {
  readonly occurrences: readonly Occurrence[];
  readonly status: OccurrencesStatus;
  readonly message: string | null;
  /** Há registros mais antigos além da última página carregada. */
  readonly hasMore: boolean;
  readonly loadingMore: boolean;
  readonly reload: () => void;
  /** Busca a próxima página pelo cursor devolvido pelo servidor. */
  readonly loadMore: () => void;
}

/**
 * A ficha nasce no ack do SOS e o turno muda com o tempo: polling curto
 * enquanto o console está aberto, sem consultar em aba escondida.
 */
const POLL_MS = 30_000;

/** Primeira página; o restante chega por "Carregar mais", por cursor. */
const PAGE_SIZE = 30;

const EMPTY: readonly Occurrence[] = [];

/**
 * Ocorrências visíveis para o usuário logado.
 *
 * <p>O escopo é do servidor: o cliente nunca recebe o que não pode ver. Quando
 * a consulta falha depois de já haver dados, a lista continua na tela com o
 * aviso, em vez de sumir com as ocorrências do turno. A reconciliação periódica
 * recarrega a primeira página; páginas antigas só são buscadas sob demanda.
 */
export function useOccurrences(): OccurrencesState {
  const [occurrences, setOccurrences] =
    useState<readonly Occurrence[]>(EMPTY);
  const [status, setStatus] = useState<OccurrencesStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [cursor, setCursor] = useState<OccurrenceCursor | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
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
        const page = await fetchOccurrences({ limit: PAGE_SIZE });
        if (cancelled) return;
        setOccurrences(page.items);
        setCursor(page.nextCursor);
        setHasMore(page.hasMore);
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

  const loadMore = useCallback(() => {
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    const run = async () => {
      try {
        const page = await fetchOccurrences({
          limit: PAGE_SIZE,
          beforeOccurredAt: cursor.occurredAt,
          beforeNumber: cursor.number,
        });
        setOccurrences((previous) => {
          const known = new Set(previous.map((occurrence) => occurrence.id));
          return [
            ...previous,
            ...page.items.filter((occurrence) => !known.has(occurrence.id)),
          ];
        });
        setCursor(page.nextCursor);
        setHasMore(page.hasMore);
        setMessage(null);
      } catch (error) {
        setMessage(occurrenceErrorMessage(error));
      } finally {
        setLoadingMore(false);
      }
    };
    void run();
  }, [cursor, loadingMore]);

  return {
    occurrences,
    status,
    message,
    hasMore,
    loadingMore,
    reload,
    loadMore,
  };
}
