import type { Role } from "@/features/auth/types";

/**
 * Usuários de demonstração da administração. O cadastro real é do módulo
 * `identity` (convite por e-mail, ativação e trilha de auditoria); nada aqui
 * sai do navegador. Os papéis seguem ADR-009.
 */
export type AdminUserStatus = "ACTIVE" | "INVITED" | "INACTIVE";

export interface AdminUser {
  readonly id: string;
  readonly name: string;
  readonly email: string;
  readonly role: Role;
  readonly status: AdminUserStatus;
  readonly lastAccessAt?: string;
}

export const adminUsers: readonly AdminUser[] = [
  {
    id: "user-1",
    name: "Sgt. Silva",
    email: "silva@cbm.example",
    role: "LIFEGUARD",
    status: "ACTIVE",
    lastAccessAt: new Date(Date.now() - 55 * 60_000).toISOString(),
  },
  {
    id: "user-2",
    name: "Ten. Rocha",
    email: "rocha@cbm.example",
    role: "COORDINATOR",
    status: "ACTIVE",
    lastAccessAt: new Date(Date.now() - 4 * 60 * 60_000).toISOString(),
  },
  {
    id: "user-3",
    name: "Ana (TI)",
    email: "ti@maraberto.dev",
    role: "ADMIN",
    status: "ACTIVE",
    lastAccessAt: new Date(Date.now() - 12 * 60_000).toISOString(),
  },
  {
    id: "user-4",
    name: "Sd. Almeida",
    email: "almeida@cbm.example",
    role: "LIFEGUARD",
    status: "INVITED",
  },
];
