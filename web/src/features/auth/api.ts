import { apiRequest, apiRequestVoid } from "@/shared/lib/api-client";
import type {
  AuthSession,
  LoginCredentials,
  OperatorUser,
} from "@/features/auth/types";

/**
 * Contrato do módulo `identity`. O refresh opaco viaja no cookie
 * `__Secure-maraberto_rt; Path=/api/v1/auth`; no navegador o `api-client` usa
 * `credentials: "same-origin"` e no app o HTTP nativo devolve o `Set-Cookie`
 * para o jar do aparelho. O JS da aplicação nunca lê o valor.
 */
interface SessionResponse {
  readonly accessToken: string;
  /** Validade do access token em segundos. */
  readonly expiresIn: number;
  readonly user: OperatorUser;
}

function toSession(response: SessionResponse): AuthSession {
  return {
    user: response.user,
    accessToken: response.accessToken,
    expiresAt: Date.now() + Math.max(response.expiresIn, 0) * 1000,
  };
}

export async function loginRequest(
  credentials: LoginCredentials,
): Promise<AuthSession> {
  const response = await apiRequest<SessionResponse>(
    "/api/v1/auth/login",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    },
    10000,
  );
  return toSession(response);
}

/** Restaura a sessão no boot (o cookie de refresh é a única prova guardada). */
export async function refreshRequest(): Promise<AuthSession> {
  const response = await apiRequest<SessionResponse>(
    "/api/v1/auth/refresh",
    { method: "POST" },
    8000,
  );
  return toSession(response);
}

let refreshInFlight: Promise<AuthSession> | null = null;

/**
 * Renovação *single-flight*.
 *
 * Duas chamadas simultâneas (boot + retry de 401, ou duas requisições na mesma
 * aba) compartilham a mesma promessa. Entre abas, `navigator.locks` serializa a
 * renovação — sem isso, duas abas apresentariam o mesmo cookie e a rotação
 * poderia ser lida como reuso.
 */
export function refreshSingleFlight(): Promise<AuthSession> {
  if (!refreshInFlight) {
    refreshInFlight = withCrossTabLock(refreshRequest).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function withCrossTabLock<T>(task: () => Promise<T>): Promise<T> {
  const locks =
    typeof navigator !== "undefined" && "locks" in navigator
      ? navigator.locks
      : undefined;
  if (!locks?.request) {
    return task();
  }
  return locks.request("maraberto:auth-refresh", task) as Promise<T>;
}

export async function logoutRequest(): Promise<void> {
  await apiRequestVoid("/api/v1/auth/logout", { method: "POST" }, 5000);
}

export async function meRequest(): Promise<OperatorUser> {
  return apiRequest<OperatorUser>("/api/v1/auth/me", { method: "GET" });
}
