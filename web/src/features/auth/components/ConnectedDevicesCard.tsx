import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Laptop,
  Loader2,
  LogOut,
  RefreshCw,
  Smartphone,
  Trash2,
} from "lucide-react";
import { ApiError } from "@/shared/lib/api-client";
import { formatDateTime } from "@/shared/lib/format";
import {
  listActiveSessionsRequest,
  revokeOtherSessionsRequest,
  revokeSessionRequest,
  type ActiveDeviceSession,
} from "@/features/auth/api";
import { DEMO_AUTH_ENABLED } from "@/features/auth/demo";
import { describeDevice } from "@/features/auth/lib/device-label";
import { useAuthSession } from "@/features/auth/session";

/**
 * Gerenciamento dos dispositivos conectados (famílias de sessão de refresh
 * ativas) do operador autenticado.
 */
export function ConnectedDevicesCard() {
  const navigate = useNavigate();
  const clearSession = useAuthSession((state) => state.clearSession);

  const [sessions, setSessions] = useState<readonly ActiveDeviceSession[]>([]);
  const [loading, setLoading] = useState(!DEMO_AUTH_ENABLED);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [revokingOthers, setRevokingOthers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);

  useEffect(() => {
    if (DEMO_AUTH_ENABLED) {
      return;
    }
    let cancelled = false;
    listActiveSessionsRequest()
      .then((data) => {
        if (!cancelled) {
          setSessions(data);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(deviceErrorMessage(cause));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleRefresh = useCallback(async () => {
    if (DEMO_AUTH_ENABLED) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setSessions(await listActiveSessionsRequest());
    } catch (cause) {
      setError(deviceErrorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  const otherDevicesCount = sessions.filter((item) => !item.current).length;

  const handleRevokeOne = async (session: ActiveDeviceSession) => {
    setBusyId(session.id);
    setError(null);
    setFeedback(null);
    try {
      await revokeSessionRequest(session.id);
      if (session.current) {
        clearSession("/login");
        navigate("/login", { replace: true });
        return;
      }
      setSessions((prev) => prev.filter((item) => item.id !== session.id));
      setFeedback("Dispositivo desconectado.");
    } catch (cause) {
      setError(deviceErrorMessage(cause));
    } finally {
      setBusyId(null);
    }
  };

  const handleRevokeOthers = async () => {
    setRevokingOthers(true);
    setError(null);
    setFeedback(null);
    try {
      await revokeOtherSessionsRequest();
      setSessions((prev) => prev.filter((item) => item.current));
      setFeedback("Todos os outros dispositivos foram desconectados.");
    } catch (cause) {
      setError(deviceErrorMessage(cause));
    } finally {
      setRevokingOthers(false);
    }
  };

  return (
    <section className="rounded-2xl border border-ocean-200 bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-ocean-100 pb-3">
        <div>
          <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
            <Laptop aria-hidden="true" className="size-4 text-ocean-700" />
            Dispositivos conectados
          </h2>
          <p className="mt-0.5 text-xs text-ocean-600">
            Sessões ativas da sua conta. Desconectar um dispositivo bloqueia a
            renovação de acesso dele imediatamente.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {!DEMO_AUTH_ENABLED ? (
            <button
              type="button"
              disabled={loading || revokingOthers}
              onClick={() => void handleRefresh()}
              aria-label="Atualizar lista de dispositivos"
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-200 bg-white px-2.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:opacity-60"
            >
              <RefreshCw
                aria-hidden="true"
                className={`size-3.5 ${loading ? "animate-spin" : ""}`}
              />
              Atualizar
            </button>
          ) : null}

          {otherDevicesCount > 0 ? (
            <button
              type="button"
              disabled={revokingOthers || busyId !== null}
              onClick={() => void handleRevokeOthers()}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-coral-300 bg-coral-50 px-3 text-xs font-bold text-coral-900 transition hover:bg-coral-100 disabled:opacity-60"
            >
              {revokingOthers ? (
                <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
              ) : (
                <LogOut aria-hidden="true" className="size-3.5" />
              )}
              Desconectar outros ({otherDevicesCount})
            </button>
          ) : null}
        </div>
      </div>

      {error ? (
        <p
          role="alert"
          className="mt-3 rounded-xl border border-coral-200 bg-coral-50 p-2.5 text-xs font-semibold text-coral-900"
        >
          {error}
        </p>
      ) : null}

      {feedback ? (
        <output className="mt-3 block rounded-xl border border-emerald-200 bg-emerald-50 p-2.5 text-xs font-semibold text-emerald-900">
          {feedback}
        </output>
      ) : null}

      {DEMO_AUTH_ENABLED ? (
        <p className="mt-3 text-xs text-ocean-600">
          O gerenciamento de dispositivos conectados requer autenticação no
          servidor (desativado no modo demonstração).
        </p>
      ) : loading ? (
        <p className="mt-4 flex items-center gap-2 text-xs font-semibold text-ocean-600">
          <Loader2 aria-hidden="true" className="size-4 animate-spin" />
          Carregando dispositivos conectados...
        </p>
      ) : sessions.length === 0 ? (
        <p className="mt-4 text-xs text-ocean-600">
          Nenhuma sessão ativa encontrada.
        </p>
      ) : (
        <ul className="mt-3 divide-y divide-ocean-100">
          {sessions.map((session) => {
            const device = describeDevice(session.userAgent);
            const DeviceIcon =
              device.kind === "mobile" ? Smartphone : Laptop;
            const isBusy = busyId === session.id;

            return (
              <li
                key={session.id}
                className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-1 last:pb-1"
              >
                <div className="flex min-w-0 items-start gap-3">
                  <span
                    aria-hidden="true"
                    className={`mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl ${
                      session.current
                        ? "bg-emerald-100 text-emerald-800"
                        : "bg-ocean-100 text-ocean-700"
                    }`}
                  >
                    <DeviceIcon className="size-4" />
                  </span>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-xs font-bold text-ocean-950">
                        {device.label}
                      </p>
                      {session.current ? (
                        <span className="inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-900">
                          Este dispositivo
                        </span>
                      ) : null}
                    </div>
                    <p className="mt-0.5 text-[11px] text-ocean-600">
                      Última atividade: {formatDateTime(session.lastUsedAt)} ·
                      Expira em {formatDateTime(session.expiresAt)}
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  disabled={isBusy || revokingOthers}
                  onClick={() => void handleRevokeOne(session)}
                  className="inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-lg border border-ocean-200 bg-white px-2.5 text-xs font-bold text-ocean-800 transition hover:border-coral-300 hover:bg-coral-50 hover:text-coral-900 disabled:opacity-60"
                >
                  {isBusy ? (
                    <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
                  ) : (
                    <Trash2 aria-hidden="true" className="size-3.5" />
                  )}
                  {session.current ? "Sair daqui" : "Desconectar"}
                </button>
              </li>
            );
          })}
        </ul>
      )}

      <p className="mt-3 text-[11px] leading-relaxed text-ocean-500">
        Para revogar imediatamente até tokens de acesso ainda válidos em outros
        aparelhos, altere sua senha abaixo.
      </p>
    </section>
  );
}

function deviceErrorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    return (
      cause.problem?.detail ??
      "Não foi possível consultar ou atualizar os dispositivos conectados."
    );
  }
  return "Não foi possível consultar ou atualizar os dispositivos conectados.";
}
