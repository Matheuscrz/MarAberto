import { useEffect } from "react";
import { refreshSingleFlight } from "@/features/auth/api";
import { DEMO_AUTH_ENABLED } from "@/features/auth/demo";
import { useAuthSession } from "@/features/auth/session";

/**
 * Restaura a sessão no boot uma única vez.
 *
 * Com o `identity` no ar, o `POST /auth/refresh` troca o cookie opaco por um
 * access token novo (single-flight entre abas); falha leva a `anonymous` sem
 * travar o app. No modo demo a sessão morre ao recarregar — é o comportamento
 * honesto, já que o token vive só em memória.
 */
export function useSessionBootstrap(): void {
  const status = useAuthSession((state) => state.status);
  const setSession = useAuthSession((state) => state.setSession);
  const clearSession = useAuthSession((state) => state.clearSession);
  const markAnonymous = useAuthSession((state) => state.markAnonymous);

  useEffect(() => {
    if (status !== "unknown") {
      return;
    }

    if (DEMO_AUTH_ENABLED) {
      markAnonymous();
      return;
    }

    let cancelled = false;
    refreshSingleFlight()
      .then((session) => {
        if (!cancelled) setSession(session);
      })
      .catch(() => {
        if (!cancelled) clearSession();
      });

    return () => {
      cancelled = true;
    };
  }, [status, setSession, clearSession, markAnonymous]);
}
