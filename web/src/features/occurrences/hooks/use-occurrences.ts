import { useCallback, useEffect, useRef, useState } from "react";
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

export interface UseOccurrencesOptions {
  /**
   * Só o que ainda pede ação (mapa de situação da coordenação): ficha aberta/em
   * atendimento ou pendência de CIOSP. O filtro é do servidor.
   */
  readonly active?: boolean;
  /** Só as encerradas (desfecho, cancelamento ou falso alarme). */
  readonly closed?: boolean;
  /** Só o que ainda falta transcrever no CIOSP. */
  readonly ciospPending?: boolean;
  /** Início do período do fato (ISO-8601, inclusive). */
  readonly from?: string;
  /** Fim do período do fato (ISO-8601, exclusivo). */
  readonly to?: string;
  /** Tamanho da primeira página e das seguintes; o servidor limita a 100. */
  readonly pageSize?: number;
}

/**
 * Ocorrências visíveis para o usuário logado.
 *
 * <p>O escopo é do servidor: o cliente nunca recebe o que não pode ver. Quando
 * a consulta falha depois de já haver dados, a lista continua na tela com o
 * aviso, em vez de sumir com as ocorrências do turno. A reconciliação periódica
 * recarrega a primeira página; páginas antigas só são buscadas sob demanda.
 */
export function useOccurrences(
  options: UseOccurrencesOptions = {},
): OccurrencesState {
  const active = options.active ?? false;
  const closed = options.closed ?? false;
  const ciospPending = options.ciospPending ?? false;
  const from = options.from;
  const to = options.to;
  const pageSize = options.pageSize ?? PAGE_SIZE;
  const [occurrences, setOccurrences] =
    useState<readonly Occurrence[]>(EMPTY);
  const [status, setStatus] = useState<OccurrencesStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [cursor, setCursor] = useState<OccurrenceCursor | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [attempt, setAttempt] = useState(0);
  // O polling recarrega só a primeira página; as antigas já carregadas ficam.
  const paged = useRef(false);

  useEffect(() => {
    let cancelled = false;
    let timer = 0;
    paged.current = false;

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
        const page = await fetchOccurrences({
          limit: pageSize,
          active,
          closed,
          ciospPending,
          from,
          to,
        });
        if (cancelled) return;
        if (paged.current) {
          // Mantém as páginas antigas: o cursor do servidor não pode voltar.
          setOccurrences((current) => {
            const fresh = new Set(page.items.map((occurrence) => occurrence.id));
            return [
              ...page.items,
              ...current.filter((occurrence) => !fresh.has(occurrence.id)),
            ];
          });
        } else {
          setOccurrences(page.items);
          setCursor(page.nextCursor);
          setHasMore(page.hasMore);
        }
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
  }, [attempt, active, closed, ciospPending, from, pageSize, to]);

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
          limit: pageSize,
          active,
          closed,
          ciospPending,
          from,
          to,
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
        paged.current = true;
        setMessage(null);
      } catch (error) {
        setMessage(occurrenceErrorMessage(error));
      } finally {
        setLoadingMore(false);
      }
    };
    void run();
  }, [cursor, loadingMore, active, closed, ciospPending, from, to, pageSize]);

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
