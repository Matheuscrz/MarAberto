import { apiRequest } from "@/shared/lib/api-client";

/**
 * Consulta da trilha de auditoria (RNF06, ADR-027).
 *
 * <p>A ordenação e o cursor são do servidor (`occurred_at DESC, id DESC`); o
 * cliente nunca reordena nem applique filtro local. A trilha é imutável: não há
 * endpoint de edição ou remoção.
 */
export interface AuditEntry {
  readonly id: number;
  readonly occurredAt: string;
  readonly actorId?: string;
  readonly actorRole?: string;
  readonly action: string;
  readonly entityType: string;
  readonly entityId?: string;
  readonly traceId?: string;
  readonly detail: Readonly<Record<string, unknown>>;
}

export interface AuditPage {
  readonly items: readonly AuditEntry[];
  readonly nextCursor?: string | null;
  readonly hasMore: boolean;
}

export interface AuditIntegrity {
  readonly checked: number;
  readonly valid: boolean;
  readonly firstInvalidId?: number | null;
}

export interface AuditQuery {
  readonly actorId?: string;
  readonly action?: string;
  readonly entityType?: string;
  readonly entityId?: string;
  readonly traceId?: string;
  readonly from?: string;
  readonly to?: string;
  readonly limit?: number;
  readonly cursor?: string;
}

export async function fetchAuditEntries(
  query: AuditQuery = {},
): Promise<AuditPage> {
  const params = new URLSearchParams();
  if (query.actorId?.trim()) params.set("actorId", query.actorId.trim());
  if (query.action?.trim()) params.set("action", query.action.trim());
  if (query.entityType?.trim()) params.set("entityType", query.entityType.trim());
  if (query.entityId?.trim()) params.set("entityId", query.entityId.trim());
  if (query.traceId?.trim()) params.set("traceId", query.traceId.trim());
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  if (query.limit) params.set("limit", String(query.limit));
  if (query.cursor) params.set("cursor", query.cursor);
  const suffix = params.size > 0 ? `?${params.toString()}` : "";
  return apiRequest<AuditPage>(`/api/v1/admin/audit${suffix}`, {});
}

export async function fetchAuditIntegrity(): Promise<AuditIntegrity> {
  return apiRequest<AuditIntegrity>("/api/v1/admin/audit/integrity", {});
}
