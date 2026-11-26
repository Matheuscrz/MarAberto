import type { OperatorUser, Role } from "../types.ts";

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

const NON_RETURNABLE_AUTH_PATHS = new Set(["/login", "/trocar-senha"]);

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
  const origin =
    typeof window === "undefined" ? "http://localhost" : window.location.origin;
  try {
    const url = new URL(value, origin);
    return url.origin === origin;
  } catch {
    return false;
  }
}

function normalizePathname(value: string): string {
  const pathname = new URL(value, "http://localhost").pathname;
  return pathname.length > 1 && pathname.endsWith("/")
    ? pathname.slice(0, -1)
    : pathname;
}

/**
 * Caminho interno seguro que pode servir como destino pós-login.
 * Exclui rotas transitórias de autenticação (`/login`, `/trocar-senha`) para
 * evitar que o usuário caia de volta na troca de senha após já tê-la concluído.
 */
export function isPostLoginRedirectPath(
  value: string | null | undefined,
): value is string {
  if (!isInternalPath(value)) {
    return false;
  }
  return !NON_RETURNABLE_AUTH_PATHS.has(normalizePathname(value));
}

export function resolvePostLoginTarget(
  user: Pick<OperatorUser, "role" | "mustChangePassword">,
  next: string | null | undefined,
): string {
  if (user.mustChangePassword) {
    return "/trocar-senha";
  }
  return isPostLoginRedirectPath(next) ? next : homePathForRole(user.role);
}

/**
 * Monta o destino de `/login` quando `RequireRole` encontra a sessão encerrada.
 * Se houver um redirecionamento explícito (ex.: `/login?senha=alterada` após
 * troca de senha ou `/login` após logout), ele prevalece; caso contrário
 * preserva `next` apenas para rotas válidas de retorno pós-login.
 */
export function buildUnauthenticatedLoginTarget(
  currentPath: string,
  explicitRedirect?: string | null,
): string {
  if (explicitRedirect && isInternalPath(explicitRedirect)) {
    return explicitRedirect;
  }
  if (isPostLoginRedirectPath(currentPath)) {
    return `/login?next=${encodeURIComponent(currentPath)}`;
  }
  return "/login";
}
