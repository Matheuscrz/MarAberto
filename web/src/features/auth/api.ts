import { apiRequest, apiRequestVoid } from "@/shared/lib/api-client";
import type {
  AuthSession,
  LoginCredentials,
  LoginOutcome,
  OperatorUser,
} from "@/features/auth/types";

interface SessionResponse {
  readonly accessToken: string | null;
  /** Validade do access token em segundos. */
  readonly expiresIn: number;
  readonly user: OperatorUser;
  readonly mfaRequired?: boolean;
  readonly mfaChallengeToken?: string | null;
}

export interface MfaSetupData {
  readonly secret: string;
  readonly otpauthUri: string;
}

export interface MfaConfirmData {
  readonly recoveryCodes: readonly string[];
}

function toSession(response: SessionResponse): AuthSession {
  return {
    user: response.user,
    accessToken: response.accessToken ?? "",
    expiresAt: Date.now() + Math.max(response.expiresIn, 0) * 1000,
  };
}

export async function loginRequest(
  credentials: LoginCredentials,
): Promise<LoginOutcome> {
  const response = await apiRequest<SessionResponse>(
    "/api/v1/auth/login",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
    },
    10000,
  );
  if (response.mfaRequired && response.mfaChallengeToken) {
    return {
      kind: "mfa_required",
      challengeToken: response.mfaChallengeToken,
      user: response.user,
    };
  }
  return { kind: "authenticated", session: toSession(response) };
}

export async function verifyMfaRequest(input: {
  readonly challengeToken: string;
  readonly code: string;
}): Promise<AuthSession> {
  const response = await apiRequest<SessionResponse>(
    "/api/v1/auth/mfa/verify",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    10000,
  );
  return toSession(response);
}

export async function setupMfaRequest(): Promise<MfaSetupData> {
  return apiRequest<MfaSetupData>(
    "/api/v1/auth/mfa/setup",
    { method: "POST" },
    8000,
  );
}

export async function confirmMfaRequest(code: string): Promise<MfaConfirmData> {
  return apiRequest<MfaConfirmData>(
    "/api/v1/auth/mfa/confirm",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ code }),
    },
    8000,
  );
}

export async function disableMfaRequest(input: {
  readonly currentPassword: string;
  readonly code: string;
}): Promise<void> {
  await apiRequestVoid(
    "/api/v1/auth/mfa/disable",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    8000,
  );
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

/**
 * Troca a senha (inclusive a temporária). O servidor revoga todas as sessões e
 * limpa o cookie de refresh; a tela volta ao login em seguida.
 */
export async function changePasswordRequest(input: {
  readonly currentPassword: string;
  readonly newPassword: string;
}): Promise<void> {
  await apiRequestVoid(
    "/api/v1/auth/password",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
    10000,
  );
}
