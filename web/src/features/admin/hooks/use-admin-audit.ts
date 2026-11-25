import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/shared/lib/api-client";
import {
  fetchAuditEntries,
  type AuditEntry,
} from "@/features/admin/api/audit";

export type AuditStatus = "loading" | "ready" | "error";

export interface AuditFilters {
  readonly traceId: string;
  readonly actorId: string;
  readonly action: string;
  readonly entityType: string;
  readonly from: string;
  readonly to: string;
}

const EMPTY_FILTERS: AuditFilters = {
  traceId: "",
  actorId: "",
  action: "",
  entityType: "",
  from: "",
  to: "",
};

/**
 * Página da trilha, do ato mais recente para o mais antigo.
 *
 * <p>Os filtros são aplicados no servidor; o cursor opaco vem de
 * `nextCursor`. Datas do formulário viram instantes ISO — `from` inclusivo e
 * `to` exclusivo (fim do dia escolhido).
 */
export function useAdminAudit() {
  const [filters, setFilters] = useState<AuditFilters>(EMPTY_FILTERS);
  const [items, setItems] = useState<readonly AuditEntry[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [status, setStatus] = useState<AuditStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const requestRef = useRef(0);

  const reload = useCallback(() => {
    setStatus("loading");
    setMessage(null);
    setReloadToken((value) => value + 1);
  }, []);
  const applyFilters = useCallback((next: AuditFilters) => {
    setStatus("loading");
    setMessage(null);
    setFilters(next);
  }, []);

  useEffect(() => {
    const requestId = requestRef.current + 1;
    requestRef.current = requestId;

    fetchAuditEntries({
      traceId: filters.traceId,
      actorId: filters.actorId,
      action: filters.action,
      entityType: filters.entityType,
      from: startOfDay(filters.from),
      to: endOfDayExclusive(filters.to),
      limit: 50,
    })
      .then((page) => {
        if (requestRef.current !== requestId) return;
        setItems(page.items);
        setNextCursor(page.nextCursor ?? null);
        setHasMore(page.hasMore);
        setStatus("ready");
      })
      .catch((error) => {
        if (requestRef.current !== requestId) return;
        setStatus("error");
        setMessage(errorMessage(error));
      });
  }, [
    filters.traceId,
    filters.actorId,
    filters.action,
    filters.entityType,
    filters.from,
    filters.to,
    reloadToken,
  ]);

  const loadMore = useCallback(async () => {
    if (!hasMore || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await fetchAuditEntries({
        traceId: filters.traceId,
        actorId: filters.actorId,
        action: filters.action,
        entityType: filters.entityType,
        from: startOfDay(filters.from),
        to: endOfDayExclusive(filters.to),
        limit: 50,
        cursor: nextCursor,
      });
      setItems((previous) => [...previous, ...page.items]);
      setNextCursor(page.nextCursor ?? null);
      setHasMore(page.hasMore);
    } catch (error) {
      setMessage(errorMessage(error));
    } finally {
      setLoadingMore(false);
    }
  }, [filters, hasMore, nextCursor, loadingMore]);

  return {
    filters,
    items,
    hasMore,
    status,
    message,
    loadingMore,
    applyFilters,
    loadMore,
    reload,
  } as const;
}

function startOfDay(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

function endOfDayExclusive(value: string): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return undefined;
  date.setDate(date.getDate() + 1);
  return date.toISOString();
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao consultar a trilha.`)
    );
  }
  return "Não foi possível consultar a trilha agora.";
}
