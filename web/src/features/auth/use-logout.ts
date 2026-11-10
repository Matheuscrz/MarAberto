import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logoutRequest } from "@/features/auth/api";
import { DEMO_AUTH_ENABLED } from "@/features/auth/demo";
import { useAuthSession } from "@/features/auth/session";

/**
 * Encerra a sessão: pede o logout ao servidor (revoga o refresh) e limpa o
 * token em memória. Mesmo com falha de rede a sessão local é limpa — o cookie
 * opaco continua no navegador, mas sem access token não há como operar.
 */
export function useLogout(): {
  readonly logout: () => Promise<void>;
  readonly pending: boolean;
} {
  const navigate = useNavigate();
  const clearSession = useAuthSession((state) => state.clearSession);
  const [pending, setPending] = useState(false);

  const logout = useCallback(async () => {
    setPending(true);
    try {
      if (!DEMO_AUTH_ENABLED) {
        await logoutRequest();
      }
    } catch {
      // Sessão local é descartada de qualquer forma.
    } finally {
      clearSession();
      setPending(false);
      navigate("/login", { replace: true });
    }
  }, [clearSession, navigate]);

  return { logout, pending };
}
