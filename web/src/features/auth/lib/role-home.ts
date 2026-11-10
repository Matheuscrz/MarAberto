import type { Role } from "@/features/auth/types";

/** Área inicial de cada papel (ADR-009). */
export const ROLE_HOME: Record<Role, string> = {
  LIFEGUARD: "/operador",
  COORDINATOR: "/gestao",
  ADMIN: "/admin",
};

export const ROLE_LABEL: Record<Role, string> = {
  LIFEGUARD: "Guarda-vidas",
  COORDINATOR: "Coordenação",
  ADMIN: "Administração (TI)",
};

export const ROLE_AREA_LABEL: Record<Role, string> = {
  LIFEGUARD: "Área do operador",
  COORDINATOR: "Área da gestão",
  ADMIN: "Área administrativa",
};

export function homePathForRole(role: Role): string {
  return ROLE_HOME[role];
}

/**
 * Só aceita caminho interno (`/praias`, `/operador?x=1`). Rejeita URL absoluta,
 * protocol-relative (`//evil.com`) e barras invertidas (`/\evil.com`, que o
 * navegador normaliza para `//evil.com`) — o `next` do login não pode virar
 * redirecionamento aberto.
 */
export function isInternalPath(value: string | null | undefined): value is string {
  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return false;
  }
  if (value.includes("\\")) {
    return false;
  }
  try {
    const url = new URL(value, window.location.origin);
    return url.origin === window.location.origin;
  } catch {
    return false;
  }
}
