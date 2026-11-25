import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/shared/lib/api-client";
import { fetchAdminUsers, type AdminUser } from "@/features/admin/api/users";
import type { Role } from "@/features/auth/types";

export type ActiveFilter = "all" | "active" | "inactive";
export type AdminUsersStatus = "loading" | "ready" | "error";

export interface AdminUserFilters {
  readonly q: string;
  readonly role: Role | "";
  readonly active: ActiveFilter;
}

const EMPTY_FILTERS: AdminUserFilters = { q: "", role: "", active: "all" };

/**
 * Lista paginada por cursor da administração.
 *
 * <p>A primeira página é recarregada quando o filtro aplicado muda;
 * `loadMore` anexa a página seguinte sem duplicar. A busca por nome não existe
 * no servidor (nome é cifrado): o backend aceita e-mail/CPF exatos ou prefixo
 * de matrícula.
 */
export function useAdminUsers() {
  const [filters, setFilters] = useState<AdminUserFilters>(EMPTY_FILTERS);
  const [items, setItems] = useState<readonly AdminUser[]>([]);
  const [total, setTotal] = useState(0);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [status, setStatus] = useState<AdminUsersStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const requestRef = useRef(0);

  const reload = useCallback(() => {
    setStatus("loading");
    setMessage(null);
    setReloadToken((value) => value + 1);
  }, []);

  const applyFilters = useCallback((next: AdminUserFilters) => {
    setStatus("loading");
    setMessage(null);
    setFilters(next);
  }, []);

  useEffect(() => {
    const requestId = requestRef.current + 1;
    requestRef.current = requestId;

    fetchAdminUsers({
      q: filters.q,
      role: filters.role || undefined,
      active:
        filters.active === "all" ? undefined : filters.active === "active",
      limit: 30,
    })
      .then((page) => {
        if (requestRef.current !== requestId) return;
        setItems(page.items);
        setTotal(page.total);
        setNextCursor(page.nextCursor ?? null);
        setHasMore(page.hasMore);
        setStatus("ready");
      })
      .catch((error) => {
        if (requestRef.current !== requestId) return;
        setStatus("error");
        setMessage(errorMessage(error));
      });
  }, [filters.q, filters.role, filters.active, reloadToken]);

  const loadMore = useCallback(async () => {
    if (!hasMore || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await fetchAdminUsers({
        q: filters.q,
        role: filters.role || undefined,
        active:
          filters.active === "all" ? undefined : filters.active === "active",
        limit: 30,
        cursor: nextCursor,
      });
      setItems((previous) => [...previous, ...page.items]);
      setTotal(page.total);
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
    total,
    hasMore,
    status,
    message,
    loadingMore,
    applyFilters,
    loadMore,
    reload,
  } as const;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao carregar os usuários.`)
    );
  }
  return "Não foi possível carregar os usuários agora.";
}
