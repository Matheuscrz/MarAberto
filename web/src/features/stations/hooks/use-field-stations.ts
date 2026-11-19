import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchFieldStations,
  stationErrorMessage,
} from "@/features/stations/api/stations";
import type { AdminStation, StationKind } from "@/features/stations/types";

export type FieldStationsStatus = "loading" | "ready" | "error";

export interface FieldStationsOptions {
  /** Filtra por estado; `undefined` devolve ativos e inativos. */
  readonly active?: boolean;
  readonly kind?: StationKind;
  /** Trecho do nome; o servidor aplica `ILIKE`. */
  readonly q?: string;
  readonly limit?: number;
}

export interface FieldStationsState {
  readonly stations: readonly AdminStation[];
  readonly total: number;
  readonly hasMore: boolean;
  readonly loadingMore: boolean;
  readonly status: FieldStationsStatus;
  readonly message: string | null;
  readonly reload: () => void;
  readonly loadMore: () => void;
}

/** Cadastro muda pouco, mas a desativação em campo precisa refletir rápido. */
const POLL_MS = 30_000;

const DEFAULT_LIMIT = 50;

/**
 * Estruturas do console de campo, inclusive a base móvel sem plantão vivo.
 *
 * <p>A lista pública (`/api/v1/stations`) só mostra a base com turno aberto;
 * esta mantém o cadastro visível para o guarda-vidas localizar o posto/base e
 * entender por que ele saiu do mapa. O filtro é do servidor: a aba abre nos
 * ativos e o histórico de bases encerradas fica em "Inativos"/"Todos", sem
 * poluir a lista do dia a dia. A aba escondida não consulta e ao voltar a
 * leitura é imediata.
 */
export function useFieldStations(
  options: FieldStationsOptions = {},
): FieldStationsState {
  const active = options.active;
  const kind = options.kind;
  const q = options.q;
  const limit = options.limit ?? DEFAULT_LIMIT;

  const [stations, setStations] = useState<readonly AdminStation[]>([]);
  const [total, setTotal] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [status, setStatus] = useState<FieldStationsStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  // Uma página extra já carregada não pode ser descartada pelo polling.
  const paged = useRef(false);

  useEffect(() => {
    let cancelled = false;
    let timer = 0;
    let hasData = false;
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
        const next = await fetchFieldStations({ active, kind, q, limit });
        if (cancelled) return;
        hasData = true;
        if (paged.current) {
          // Atualiza as linhas já visíveis e mantém as páginas antigas; o
          // cursor da primeira página não pode retroceder.
          setStations((current) => mergeStations(next.items, current));
        } else {
          setStations(next.items);
          setCursor(next.nextCursor);
          setHasMore(next.hasMore);
        }
        setTotal(next.total);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        if (!hasData) setStatus("error");
        setMessage(stationErrorMessage(error));
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
  }, [attempt, active, kind, limit, q]);

  const reload = useCallback(() => {
    setStatus((current) => (current === "error" ? "loading" : current));
    setAttempt((value) => value + 1);
  }, []);

  const loadMore = useCallback(() => {
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    void (async () => {
      try {
        const page = await fetchFieldStations({
          active,
          kind,
          q,
          limit,
          cursor,
        });
        setStations((current) => {
          const known = new Set(current.map((station) => station.id));
          return [
            ...current,
            ...page.items.filter((station) => !known.has(station.id)),
          ];
        });
        setTotal(page.total);
        setCursor(page.nextCursor);
        setHasMore(page.hasMore);
        paged.current = true;
        setMessage(null);
      } catch (error) {
        setMessage(stationErrorMessage(error));
      } finally {
        setLoadingMore(false);
      }
    })();
  }, [active, cursor, kind, limit, loadingMore, q]);

  return {
    stations,
    total,
    hasMore,
    loadingMore,
    status,
    message,
    reload,
    loadMore,
  };
}

/** Junta a primeira página fresca às páginas antigas já carregadas. */
function mergeStations(
  fresh: readonly AdminStation[],
  current: readonly AdminStation[],
): readonly AdminStation[] {
  const byId = new Map(current.map((station) => [station.id, station]));
  fresh.forEach((station) => byId.set(station.id, station));
  return [...byId.values()].sort((first, second) => {
    if (first.kind !== second.kind) {
      return first.kind.localeCompare(second.kind);
    }
    const byName = first.name.localeCompare(second.name, "pt-BR");
    return byName !== 0 ? byName : first.id.localeCompare(second.id);
  });
}
