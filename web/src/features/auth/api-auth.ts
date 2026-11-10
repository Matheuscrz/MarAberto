import { ApiError, setAuthBridge } from "@/shared/lib/api-client";
import { refreshSingleFlight } from "@/features/auth/api";
import { useAuthSession } from "@/features/auth/session";

let installed = false;

/**
 * Liga o cliente HTTP à sessão em memória.
 *
 * Chamado uma vez em `main.tsx`, antes do primeiro render: a partir daí toda
 * requisição leva `Authorization: Bearer` e, em `401`, tenta renovar a sessão
 * com o cookie de refresh antes de repetir.
 */
export function installAuthBridge(): void {
  if (installed) {
    return;
  }
  installed = true;

  setAuthBridge({
    getAccessToken: () => useAuthSession.getState().accessToken,
    refresh: async () => {
      try {
        const session = await refreshSingleFlight();
        useAuthSession.getState().setSession(session);
        return true;
      } catch (error) {
        // 401 = sessão realmente inválida. Falha de rede preserva o token em
        // memória para uma nova tentativa; a requisição original devolve o erro.
        if (error instanceof ApiError && error.status === 401) {
          useAuthSession.getState().clearSession();
        }
        return false;
      }
    },
    onUnauthorized: () => useAuthSession.getState().clearSession(),
  });
}
