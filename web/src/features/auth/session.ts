import { create } from "zustand";
import type { AuthSession, OperatorUser, Role } from "@/features/auth/types";

/**
 * Sessão em memória, deliberadamente **sem `persist`**.
 *
 * - O access token vive só aqui: recarregar a página exige `POST /auth/refresh`
 *   (cookie opaco `HttpOnly`), não há cópia em `localStorage`/`sessionStorage`.
 * - `unknown` cobre o boot: enquanto o refresh não responde, a guarda mostra
 *   "Carregando" em vez de chutar "deslogado" e piscar a tela de login.
 */
export type SessionStatus = "unknown" | "anonymous" | "authenticated";

interface AuthState {
  readonly status: SessionStatus;
  readonly user: OperatorUser | null;
  readonly accessToken: string | null;
  readonly expiresAt: number | null;
  readonly setSession: (session: AuthSession) => void;
  readonly clearSession: () => void;
  readonly markAnonymous: () => void;
}

export const useAuthSession = create<AuthState>((set) => ({
  status: "unknown",
  user: null,
  accessToken: null,
  expiresAt: null,
  setSession: (session) =>
    set({
      status: "authenticated",
      user: session.user,
      accessToken: session.accessToken,
      expiresAt: session.expiresAt,
    }),
  clearSession: () =>
    set({
      status: "anonymous",
      user: null,
      accessToken: null,
      expiresAt: null,
    }),
  markAnonymous: () => set({ status: "anonymous" }),
}));

/** Access token atual, para o interceptor do `api-client` (fora de hooks). */
export function currentAccessToken(): string | null {
  return useAuthSession.getState().accessToken;
}

export function useCurrentUser(): OperatorUser | null {
  return useAuthSession((state) => state.user);
}

export function useHasRole(roles: Role | readonly Role[]): boolean {
  const user = useAuthSession((state) => state.user);
  if (!user) return false;
  const allowed: readonly Role[] = typeof roles === "string" ? [roles] : roles;
  return allowed.includes(user.role);
}
