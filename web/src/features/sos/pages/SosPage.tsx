import { useCallback, useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Crosshair,
  Loader2,
  Lock,
  Phone,
  RotateCcw,
  Siren,
  WifiOff,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { ApiError } from "@/shared/lib/api-client";
import { createEmergencyCall } from "@/features/sos/api/emergency-calls";
import {
  GeolocationError,
  getDevicePosition,
  type GeolocationFailure,
} from "@/shared/lib/geolocation";
import { newRequestId } from "@/shared/lib/id";
import { formatAccuracy } from "@/shared/lib/format";
import { safeProblemDetail, sanitizeText } from "@/shared/lib/sanitize";
import { useOnlineStatus } from "@/shared/lib/pwa";
import { parseCoordinates } from "@/shared/lib/validation";
import type { Coordinates } from "@/shared/types/geo";
import { ZodError } from "zod";

type Phase = "idle" | "locating" | "ready" | "sending" | "sent" | "error";

const GEO_OPTIONS: PositionOptions = {
  enableHighAccuracy: true,
  timeout: 10000,
  maximumAge: 0,
};

function describeGeolocationFailure(reason: GeolocationFailure): string {
  switch (reason) {
    case "unsupported":
      return "Este navegador não oferece localização. Ligue 193 e informe o local.";
    case "denied":
      return "Permissão de localização negada. Libere o acesso no navegador ou ligue 193.";
    case "unavailable":
      return "Não foi possível obter sua localização agora. Ligue 193 e descreva o ponto de referência.";
    case "timeout":
      return "A localização demorou demais para responder. Tente novamente ou ligue 193.";
  }
}

export function SosPage() {
  const [phase, setPhase] = useState<Phase>("idle");
  const [location, setLocation] = useState<Coordinates | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [callId, setCallId] = useState<string | null>(null);
  const online = useOnlineStatus();
  const requestIdRef = useRef<string | null>(null);

  const locate = useCallback(async () => {
    setPhase("locating");
    setMessage(null);
    try {
      const position = await getDevicePosition(GEO_OPTIONS);
      const parsed = parseCoordinates({
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyMeters:
          Number.isFinite(position.accuracyMeters) &&
          position.accuracyMeters > 0
            ? Math.min(position.accuracyMeters, 100000)
            : undefined,
      });
      setLocation(parsed);
      setPhase("ready");
    } catch (error) {
      setPhase("error");
      if (error instanceof GeolocationError) {
        setMessage(describeGeolocationFailure(error.reason));
      } else if (error instanceof ZodError) {
        setMessage(
          "A localização recebida é inválida. Ligue 193 e informe o local.",
        );
      } else {
        setMessage(
          "Não foi possível obter sua localização. Ligue 193 e informe o local.",
        );
      }
    }
  }, []);

  const send = useCallback(async () => {
    if (!location) return;
    if (!online) {
      setPhase("error");
      setMessage(
        "Sem conexão com a internet. O SOS não pode ser enviado agora: ligue 193.",
      );
      return;
    }

    const clientRequestId = requestIdRef.current ?? newRequestId();
    requestIdRef.current = clientRequestId;
    setPhase("sending");
    setMessage(null);

    try {
      const response = await createEmergencyCall({
        clientRequestId,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracyMeters: location.accuracyMeters,
      });
      setCallId(response.callId);
      setPhase("sent");
    } catch (error) {
      setPhase("error");
      if (error instanceof ApiError) {
        const fieldMessage = error.problem?.errors
          ?.map(
            (item) =>
              `${sanitizeText(item.field, 60)}: ${sanitizeText(item.message, 120)}`,
          )
          .join(" · ");
        setMessage(
          fieldMessage ||
            safeProblemDetail(
              error.problem?.detail,
              error.network
                ? "O servidor não confirmou o envio. Ligue 193 imediatamente."
                : "O servidor recusou o chamado. Ligue 193.",
            ),
        );
      } else {
        setMessage("Falha inesperada ao enviar o SOS. Ligue 193.");
      }
    }
  }, [location, online]);

  const reset = () => {
    requestIdRef.current = null;
    setCallId(null);
    setLocation(null);
    setMessage(null);
    setPhase("idle");
  };

  const busy = phase === "locating" || phase === "sending";

  return (
    <div className="grid gap-3">
      <PageHeader
        title="SOS Cidadão"
        subtitle="Avisa a central de guarda-vidas com sua localização. Não substitui o 193."
      />

      <div className="rounded-xl border border-coral-300 bg-coral-50 p-3 text-coral-950">
        <p className="flex items-start gap-2 text-xs font-bold">
          <AlertTriangle
            aria-hidden="true"
            className="mt-0.5 size-4 shrink-0"
          />
          Em risco de vida, ligue 193 primeiro.
        </p>
        <p className="mt-1 text-[11px]">
          O SOS exige internet ativa e não aciona automaticamente o Corpo de
          Bombeiros ou SAMU.
        </p>
      </div>

      {!online ? (
        <p className="flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 p-3 text-xs font-semibold text-amber-950">
          <WifiOff aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          Você está sem conexão. Ligue 193 pelo telefone.
        </p>
      ) : null}

      {phase === "sent" ? (
        <Card className="border-emerald-300 bg-emerald-50">
          <h2 className="flex items-center gap-2 text-base font-black text-emerald-950">
            <CheckCircle2
              aria-hidden="true"
              className="size-5 text-emerald-600"
            />
            Chamado registrado
          </h2>
          <p className="mt-1.5 text-xs text-emerald-900">
            O servidor confirmou o recebimento. Sinalize para o guarda-vidas e,
            se necessário, ligue 193.
          </p>
          {callId ? (
            <p className="mt-2.5 rounded-lg bg-white/80 px-3 py-1.5 text-xs">
              Protocolo:{" "}
              <span className="font-mono font-bold">
                {sanitizeText(callId, 64)}
              </span>
            </p>
          ) : null}
          <div className="mt-3 grid grid-cols-2 gap-2">
            <a
              href="tel:193"
              className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-2 text-xs font-bold text-white"
            >
              <Phone aria-hidden="true" className="size-4" />
              Ligar 193
            </a>
            <button
              type="button"
              onClick={reset}
              className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl border border-ocean-950 bg-white px-3 py-2 text-xs font-bold text-ocean-950"
            >
              <RotateCcw aria-hidden="true" className="size-4" />
              Novo chamado
            </button>
          </div>
        </Card>
      ) : (
        <Card>
          <h2 className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
            <Siren aria-hidden="true" className="size-4 text-coral-600" />
            Enviar localização atual
          </h2>
          <ol className="mt-2 grid gap-1 text-xs text-ocean-700">
            <li>1. Permita o acesso à localização no navegador.</li>
            <li>2. Confira a precisão do GPS abaixo.</li>
            <li>3. Toque em "Enviar SOS" e aguarde a confirmação.</li>
          </ol>

          {location ? (
            <dl className="mt-3 grid grid-cols-3 gap-2 rounded-xl border border-ocean-200 bg-ocean-50 p-2.5 text-center">
              <div>
                <dt className="text-[10px] font-bold uppercase text-ocean-500">
                  Lat
                </dt>
                <dd className="font-mono text-xs font-bold text-ocean-950">
                  {location.latitude.toFixed(4)}
                </dd>
              </div>
              <div>
                <dt className="text-[10px] font-bold uppercase text-ocean-500">
                  Lon
                </dt>
                <dd className="font-mono text-xs font-bold text-ocean-950">
                  {location.longitude.toFixed(4)}
                </dd>
              </div>
              <div>
                <dt className="text-[10px] font-bold uppercase text-ocean-500">
                  Precisão
                </dt>
                <dd className="text-xs font-bold text-ocean-950">
                  {formatAccuracy(location.accuracyMeters)}
                </dd>
              </div>
            </dl>
          ) : null}

          {message ? (
            <p
              role="alert"
              className="mt-3 rounded-xl border border-coral-300 bg-coral-50 p-2.5 text-xs font-semibold text-coral-950"
            >
              {message}
            </p>
          ) : null}

          <div className="mt-3 grid gap-2">
            {location ? (
              <button
                type="button"
                onClick={send}
                disabled={busy || !online}
                className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-coral-600 px-4 py-2.5 text-sm font-black text-white shadow-md transition hover:bg-coral-500 disabled:opacity-60"
              >
                {phase === "sending" ? (
                  <>
                    <Loader2
                      aria-hidden="true"
                      className="size-4 animate-spin"
                    />
                    Enviando…
                  </>
                ) : (
                  <>
                    <Siren aria-hidden="true" className="size-4" />
                    {phase === "error"
                      ? "Tentar enviar novamente"
                      : "Enviar SOS"}
                  </>
                )}
              </button>
            ) : (
              <button
                type="button"
                onClick={locate}
                disabled={busy}
                className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-ocean-950 px-4 py-2.5 text-sm font-black text-white transition hover:bg-ocean-900 disabled:opacity-60"
              >
                {phase === "locating" ? (
                  <>
                    <Loader2
                      aria-hidden="true"
                      className="size-4 animate-spin"
                    />
                    Obtendo localização…
                  </>
                ) : (
                  <>
                    <Crosshair aria-hidden="true" className="size-4" />
                    Usar minha localização
                  </>
                )}
              </button>
            )}

            {location ? (
              <button
                type="button"
                onClick={locate}
                disabled={busy}
                className="inline-flex min-h-9 w-full items-center justify-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 disabled:opacity-60"
              >
                <RotateCcw aria-hidden="true" className="size-3.5" />
                Atualizar GPS
              </button>
            ) : null}
          </div>
        </Card>
      )}

      <Card>
        <h2 className="flex items-center gap-1.5 text-xs font-bold text-ocean-950">
          <Lock aria-hidden="true" className="size-4 text-ocean-600" />
          Privacidade da sua localização
        </h2>
        <ul className="mt-1.5 grid gap-1 text-[11px] text-ocean-700">
          <li>• Enviada apenas quando você toca em "Enviar SOS".</li>
          <li>• Nunca fica salva no histórico local do navegador.</li>
        </ul>
      </Card>
    </div>
  );
}
