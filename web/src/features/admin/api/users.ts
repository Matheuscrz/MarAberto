import { apiRequest } from "@/shared/lib/api-client";
import type { Role } from "@/features/auth/types";

/**
 * Contrato do módulo `identity` para a TI (RF20, ADR-027).
 *
 * <p>Listagem, cadastro, ativação/papel e reset. Não existe `DELETE`: desativar
 * preserva plantões, ocorrências e a própria trilha.
 */
export interface AdminUser {
  readonly id: string;
  readonly name: string;
  readonly email?: string;
  readonly badgeNumber?: string;
  readonly role: Role;
  readonly active: boolean;
  readonly mustChangePassword: boolean;
  readonly temporaryPasswordExpiresAt?: string;
  readonly passwordChangedAt?: string;
  readonly createdAt: string;
  readonly lastAccessAt?: string;
}

export interface AdminUserPage {
  readonly items: readonly AdminUser[];
  readonly nextCursor?: string | null;
  readonly hasMore: boolean;
  readonly total: number;
}

/** Resposta de criação/reset: a senha temporária aparece uma única vez. */
export interface ProvisionedAdminUser {
  readonly user: AdminUser;
  readonly temporaryPassword: string;
  readonly expiresAt: string;
}

export interface CreateAdminUserInput {
  readonly name: string;
  readonly badgeNumber?: string;
  readonly email: string;
  readonly cpf: string;
  readonly role: Role;
}

export interface UpdateAdminUserInput {
  readonly role?: Role;
  readonly active?: boolean;
}

export interface AdminUserQuery {
  readonly active?: boolean;
  readonly role?: Role;
  readonly q?: string;
  readonly limit?: number;
  readonly cursor?: string;
}

export async function fetchAdminUsers(
  query: AdminUserQuery = {},
): Promise<AdminUserPage> {
  const params = new URLSearchParams();
  if (query.active !== undefined) params.set("active", String(query.active));
  if (query.role) params.set("role", query.role);
  if (query.q?.trim()) params.set("q", query.q.trim());
  if (query.limit) params.set("limit", String(query.limit));
  if (query.cursor) params.set("cursor", query.cursor);
  const suffix = params.size > 0 ? `?${params.toString()}` : "";
  return apiRequest<AdminUserPage>(`/api/v1/admin/users${suffix}`, {});
}

export async function createAdminUser(
  input: CreateAdminUserInput,
): Promise<ProvisionedAdminUser> {
  return apiRequest<ProvisionedAdminUser>("/api/v1/admin/users", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      name: input.name,
      badgeNumber: input.badgeNumber?.trim() || undefined,
      email: input.email,
      cpf: input.cpf,
      role: input.role,
    }),
  });
}

export async function updateAdminUser(
  userId: string,
  input: UpdateAdminUserInput,
): Promise<AdminUser> {
  return apiRequest<AdminUser>(`/api/v1/admin/users/${userId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export async function resetAdminUserPassword(
  userId: string,
): Promise<ProvisionedAdminUser> {
  return apiRequest<ProvisionedAdminUser>(
    `/api/v1/admin/users/${userId}/password-reset`,
    { method: "POST" },
  );
}
