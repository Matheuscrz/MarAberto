import type { ApiProblem } from "@/shared/types/api";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ApiProblem;
  readonly network: boolean;

  constructor(
    status: number,
    problem?: ApiProblem,
    network = false,
    message?: string,
  ) {
    super(message ?? problem?.title ?? "Falha na comunicação com o servidor");
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.network = network;
  }
}

/**
 * Ponte entre o cliente HTTP e a sessão da feature `auth`.
 *
 * `shared/` não conhece `features/`, então a feature registra esta ponte uma
 * única vez no boot. Sem ela, o cliente funciona anônimo (rotas públicas).
 */
export interface AuthBridge {
  /** Access token em memória, ou `null` quando não há sessão. */
  readonly getAccessToken: () => string | null;
  /** Renova a sessão (single-flight). `true` quando há novo access token. */
  readonly refresh: () => Promise<boolean>;
  /** Descarta a sessão local quando a renovação falha de vez. */
  readonly onUnauthorized: () => void;
}

let authBridge: AuthBridge | null = null;

export function setAuthBridge(bridge: AuthBridge | null): void {
  authBridge = bridge;
}

/**
 * Rotas de sessão que nunca recebem `Authorization` nem disparam renovação:
 * login e refresh usam o cookie/credenciais, e repetir o logout não ajuda.
 */
const SESSION_ENDPOINTS_WITHOUT_TOKEN = ["/api/v1/auth/login", "/api/v1/auth/refresh"];

const SESSION_ENDPOINTS_WITHOUT_REFRESH = [
  ...SESSION_ENDPOINTS_WITHOUT_TOKEN,
  "/api/v1/auth/logout",
];

function matches(path: string, prefixes: readonly string[]): boolean {
  return prefixes.some((prefix) => path.startsWith(prefix));
}

/** Executa o fetch com timeout e devolve a resposta (mesmo quando `!ok`). */
async function execute(
  path: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);

  const token =
    authBridge && !matches(path, SESSION_ENDPOINTS_WITHOUT_TOKEN)
      ? authBridge.getAccessToken()
      : null;

  try {
    return await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      credentials: "same-origin",
      cache: "no-store",
      headers: {
        Accept: "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...init.headers,
      },
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new ApiError(
        0,
        undefined,
        true,
        "Tempo esgotado ao falar com o servidor",
      );
    }
    throw new ApiError(0, undefined, true, "Sem conexão com o servidor");
  } finally {
    window.clearTimeout(timeout);
  }
}

/** Normaliza erro RFC 9457 em `ApiError`. */
async function ensureOk(response: Response): Promise<Response> {
  if (response.ok) {
    return response;
  }
  let problem: ApiProblem | undefined;
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/problem+json")) {
    try {
      problem = (await response.json()) as ApiProblem;
    } catch {
      problem = undefined;
    }
  }
  throw new ApiError(response.status, problem);
}

/**
 * Requisição com sessão: anexa o access token e, em `401`, renova uma única vez
 * (single-flight) e repete. O corpo precisa ser reenviável (string/JSON), o que
 * vale para todas as chamadas do app.
 */
async function sendRequest(
  path: string,
  init: RequestInit,
  timeoutMs: number,
  allowRefresh: boolean,
): Promise<Response> {
  const response = await execute(path, init, timeoutMs);

  if (
    response.status === 401 &&
    allowRefresh &&
    authBridge &&
    !matches(path, SESSION_ENDPOINTS_WITHOUT_REFRESH)
  ) {
    const refreshed = await authBridge.refresh();
    if (refreshed) {
      return sendRequest(path, init, timeoutMs, false);
    }
    authBridge.onUnauthorized();
  }

  return ensureOk(response);
}

/**
 * Cliente HTTP único do app: same-origin, sem cache, timeout, Bearer token e
 * renovação de sessão em 401. Erros do backend chegam como RFC 9457
 * (`application/problem+json`) e viram `ApiError`.
 */
export async function apiRequest<T>(
  path: string,
  init: RequestInit,
  timeoutMs = 8000,
): Promise<T> {
  const response = await sendRequest(path, init, timeoutMs, true);
  return (await response.json()) as T;
}

/** Mesmo cliente para respostas sem corpo (ex.: `204 No Content` no logout). */
export async function apiRequestVoid(
  path: string,
  init: RequestInit,
  timeoutMs = 8000,
): Promise<void> {
  await sendRequest(path, init, timeoutMs, true);
}
