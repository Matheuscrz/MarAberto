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
  /**
   * Destino explícito para `/login` quando a sessão é limpa de propósito
   * (troca de senha ou logout). Evita que `RequireRole` re-renderize antes da
   * transição do roteador e sobrescreva `/login?senha=alterada` ou anexe
   * `?next=` com a rota anterior.
   */
  readonly loginRedirect: string | null;
  readonly setSession: (session: AuthSession) => void;
  readonly setMfaEnabled: (enabled: boolean) => void;
  readonly clearSession: (loginRedirect?: string | null) => void;
  readonly clearLoginRedirect: () => void;
  readonly markAnonymous: () => void;
}

export const useAuthSession = create<AuthState>((set) => ({
  status: "unknown",
  user: null,
  accessToken: null,
  expiresAt: null,
  loginRedirect: null,
  setSession: (session) =>
    set({
      status: "authenticated",
      user: session.user,
      accessToken: session.accessToken,
      expiresAt: session.expiresAt,
      loginRedirect: null,
    }),
  setMfaEnabled: (enabled) =>
    set((state) =>
      state.user
        ? { user: { ...state.user, mfaEnabled: enabled } }
        : state,
    ),
  clearSession: (loginRedirect = null) =>
    set({
      status: "anonymous",
      user: null,
      accessToken: null,
      expiresAt: null,
      loginRedirect: loginRedirect ?? null,
    }),
  clearLoginRedirect: () => set({ loginRedirect: null }),
  markAnonymous: () => set({ status: "anonymous", loginRedirect: null }),
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
