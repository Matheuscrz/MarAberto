import type { ApiProblem, SosPayload, SosResponse } from '../types';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ApiProblem;
  readonly network: boolean;

  constructor(status: number, problem?: ApiProblem, network = false, message?: string) {
    super(message ?? problem?.title ?? 'Falha na comunicação com o servidor');
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.network = network;
  }
}

async function request<T>(
  path: string,
  init: RequestInit,
  timeoutMs = 8000,
): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      signal: controller.signal,
      credentials: 'same-origin',
      cache: 'no-store',
      headers: {
        Accept: 'application/json',
        ...init.headers,
      },
    });

    if (!response.ok) {
      let problem: ApiProblem | undefined;
      const contentType = response.headers.get('content-type') ?? '';
      if (contentType.includes('application/problem+json')) {
        try {
          problem = (await response.json()) as ApiProblem;
        } catch {
          problem = undefined;
        }
      }
      throw new ApiError(response.status, problem);
    }

    return (await response.json()) as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError(0, undefined, true, 'Tempo esgotado ao falar com o servidor');
    }
    throw new ApiError(0, undefined, true, 'Sem conexão com o servidor');
  } finally {
    window.clearTimeout(timeout);
  }
}

/**
 * Registra um SOS. O timeout é curto de propósito: se o servidor não confirmar,
 * a interface orienta a ligar 193 em vez de fingir que o chamado foi enviado.
 */
export async function createEmergencyCall(payload: SosPayload): Promise<SosResponse> {
  return request<SosResponse>(
    '/api/v1/emergency-calls',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    },
    5000,
  );
}

export function isApiProblem(value: unknown): value is ApiProblem {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return typeof candidate.type === 'string' && typeof candidate.status === 'number';
}
