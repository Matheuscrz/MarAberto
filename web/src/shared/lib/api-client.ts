import type { ApiProblem } from "@/shared/types/api";
import { isNativeApp } from "@/shared/lib/platform";
import type { NativeHttpResponse } from "@/shared/lib/native";

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
  const token =
    authBridge && !matches(path, SESSION_ENDPOINTS_WITHOUT_TOKEN)
      ? authBridge.getAccessToken()
      : null;

  // No app a API é outra origem e o backend não abre CORS: todas as chamadas
  // saem pelo HTTP nativo, com timeout explícito e o cookie do refresh no jar
  // do processo (`CapacitorCookies`), inclusive entre reinícios do app.
  if (isNativeApp()) {
    return executeNative(path, init, timeoutMs, token);
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);

  try {
    return await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      // No navegador/PWA o refresh é same-origin (o app usa `executeNative`,
      // que não passa por `fetch`).
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

/**
 * Chamadas do app pelo HTTP nativo, sem CORS e com timeout explícito.
 *
 * <p>O cookie de refresh continua sendo do backend (`HttpOnly; Secure;
 * SameSite=Strict`) e não passa pelo JavaScript: o plugin core
 * `CapacitorCookies` registra o `CookieHandler` do processo com o
 * `CookieManager` do WebView, então o `HttpURLConnection` usado pelo
 * `CapacitorHttp` envia o cookie no refresh/logout e guarda o `Set-Cookie` do
 * login — inclusive entre reinícios do app. Nenhum token entra em storage web.
 */
async function executeNative(
  path: string,
  init: RequestInit,
  timeoutMs: number,
  token: string | null,
): Promise<Response> {
  const { nativeHttpRequest } = await import("@/shared/lib/native");
  const url = `${API_BASE_URL}${path}`;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  new Headers(init.headers).forEach((value, key) => {
    headers[key] = value;
  });

  let response: NativeHttpResponse;
  try {
    response = await nativeHttpRequest({
      url,
      method: init.method ?? "GET",
      headers,
      data: typeof init.body === "string" ? init.body : undefined,
      connectTimeout: timeoutMs,
      readTimeout: timeoutMs,
    });
  } catch {
    throw new ApiError(0, undefined, true, "Sem conexão com o servidor");
  }

  return toNativeResponse(response);
}

function toNativeResponse(response: NativeHttpResponse): Response {
  const contentType = headerValue(response.headers, "content-type") ?? "";
  const hasBody =
    response.status !== 204 &&
    response.data !== null &&
    response.data !== undefined &&
    response.data !== "";
  const body: BodyInit | null = hasBody
    ? contentType.includes("json")
      ? JSON.stringify(response.data)
      : String(response.data)
    : null;
  return new Response(body, {
    status: response.status,
    headers: contentType ? { "Content-Type": contentType } : undefined,
  });
}

function headerValue(
  headers: Record<string, string>,
  name: string,
): string | undefined {
  const target = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === target) {
      return value;
    }
  }
  return undefined;
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
 * Cliente HTTP único do app: mesma origem no navegador (proxy `/api`) ou URL
 * absoluta no app Capacitor, sem cache, timeout, Bearer token e renovação de
 * sessão em 401. Erros do backend chegam como RFC 9457
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
