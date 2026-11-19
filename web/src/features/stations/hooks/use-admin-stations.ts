import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchAdminStations,
  fetchFieldStations,
  stationErrorMessage,
  updateFieldStationStatus,
  updateStationStatus,
} from "@/features/stations/api/stations";
import type { AdminStation, StationKind } from "@/features/stations/types";

export type AdminStationsStatus = "loading" | "ready" | "error";

/**
 * Origem da lista de estruturas.
 *
 * <p>`admin` é a TI (`/api/v1/admin/stations`); `coordination` é a coordenação
 * (`/api/v1/operations/stations`), que lê e ativa/desativa qualquer estrutura
 * sem precisar do papel `ADMIN`.
 */
export type StationAdminSource = "admin" | "coordination";

export interface AdminStationsOptions {
  readonly source?: StationAdminSource;
  /** Filtra por estado; `undefined` devolve ativos e inativos. */
  readonly active?: boolean;
  readonly kind?: StationKind;
  /** Trecho do nome; o servidor aplica `ILIKE`. */
  readonly q?: string;
  readonly limit?: number;
}

export interface AdminStationsState {
  readonly stations: readonly AdminStation[];
  readonly total: number;
  readonly hasMore: boolean;
  readonly loadingMore: boolean;
  readonly status: AdminStationsStatus;
  readonly message: string | null;
  readonly feedback: string | null;
  /** Identificador da estrutura com uma alteração em voo. */
  readonly busyId: string | null;
  readonly reload: () => void;
  readonly loadMore: () => void;
  readonly setActive: (
    station: AdminStation,
    active: boolean,
    statusNote: string | null,
  ) => Promise<boolean>;
  /** Substitui uma estrutura já atualizada (posição) na lista carregada. */
  readonly applyUpdate: (station: AdminStation) => void;
  readonly dismissFeedback: () => void;
}

const DEFAULT_LIMIT = 30;

/**
 * Postos e bases no painel da TI ou na aba da coordenação.
 *
 * <p>A lista é do servidor, paginada por cursor e filtrada no banco: a aba
 * abre só os ativos (o histórico de bases móveis desativadas continua acessível
 * pelo filtro "Inativos"/"Todos") e "Carregar mais" busca a página seguinte sem
 * repetir registros. Toda alteração de estado recarrega a página atual para o
 * item entrar ou sair do filtro corrente.
 */
export function useAdminStations(
  options: AdminStationsOptions = {},
): AdminStationsState {
  const source = options.source ?? "admin";
  const active = options.active;
  const kind = options.kind;
  const q = options.q;
  const limit = options.limit ?? DEFAULT_LIMIT;

  const [stations, setStations] = useState<readonly AdminStation[]>([]);
  const [total, setTotal] = useState(0);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [status, setStatus] = useState<AdminStationsStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const loadedOnce = useRef(false);

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      try {
        const page =
          source === "coordination"
            ? await fetchFieldStations({ active, kind, q, limit })
            : await fetchAdminStations({ active, kind, q, limit });
        if (cancelled) return;
        loadedOnce.current = true;
        setStations(page.items);
        setTotal(page.total);
        setCursor(page.nextCursor);
        setHasMore(page.hasMore);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        // Já carregado: mantém a lista e só sinaliza a falha da atualização.
        setStatus((current) => (current === "ready" ? "ready" : "error"));
        setMessage(stationErrorMessage(error));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [attempt, source, active, kind, q, limit]);

  const loadMore = useCallback(() => {
    if (!cursor || loadingMore) return;
    setLoadingMore(true);
    void (async () => {
      try {
        const page =
          source === "coordination"
            ? await fetchFieldStations({ active, kind, q, limit, cursor })
            : await fetchAdminStations({ active, kind, q, limit, cursor });
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
        setMessage(null);
      } catch (error) {
        setMessage(stationErrorMessage(error));
      } finally {
        setLoadingMore(false);
      }
    })();
  }, [active, cursor, kind, limit, loadingMore, q, source]);

  const setActive = useCallback(
    async (station: AdminStation, nextActive: boolean, statusNote: string | null) => {
      setBusyId(station.id);
      setFeedback(null);
      try {
        const updated =
          source === "coordination"
            ? await updateFieldStationStatus(
                station.kind,
                station.id,
                nextActive,
                statusNote,
              )
            : await updateStationStatus(
                station.kind,
                station.id,
                nextActive,
                statusNote,
              );
        // Com filtro de estado, a linha pode deixar a página; recarrega para o
        // total e a lista refletirem a mudança sem esperar o próximo acesso.
        setStations((current) =>
          current.map((item) => (item.id === updated.id ? updated : item)),
        );
        setAttempt((value) => value + 1);
        setFeedback(
          nextActive
            ? `${updated.name} voltou a aparecer no mapa público.`
            : `${updated.name} saiu do mapa público. O histórico de plantões e ocorrências continua.`,
        );
        return true;
      } catch (error) {
        setFeedback(stationErrorMessage(error));
        return false;
      } finally {
        setBusyId(null);
      }
    },
    [source],
  );

  const applyUpdate = useCallback((updated: AdminStation) => {
    setStations((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
    setFeedback(`${updated.name}: posição atualizada com a leitura do aparelho.`);
  }, []);

  const reload = useCallback(() => {
    if (!loadedOnce.current) {
      setStatus("loading");
    }
    setAttempt((value) => value + 1);
  }, []);

  const dismissFeedback = useCallback(() => setFeedback(null), []);

  return {
    stations,
    total,
    hasMore,
    loadingMore,
    status,
    message,
    feedback,
    busyId,
    reload,
    loadMore,
    setActive,
    applyUpdate,
    dismissFeedback,
  };
}
