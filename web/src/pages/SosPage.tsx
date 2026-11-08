import { useCallback, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
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
} from 'lucide-react';
import { Card, PageHeader } from '../components/Card';
import { createEmergencyCall, ApiError } from '../lib/api';
import { newRequestId } from '../lib/id';
import { formatAccuracy } from '../lib/format';
import { safeProblemDetail, sanitizeText } from '../lib/sanitize';
import { useOnlineStatus } from '../lib/pwa';
import { parseCoordinates, type Coordinates } from '../lib/validation';
import { ZodError } from 'zod';

type Phase = 'idle' | 'locating' | 'ready' | 'sending' | 'sent' | 'error';

const GEO_OPTIONS: PositionOptions = {
  enableHighAccuracy: true,
  timeout: 10000,
  maximumAge: 0,
};

const PERMISSION_DENIED = 1;
const POSITION_UNAVAILABLE = 2;
const TIMEOUT = 3;

function isGeolocationError(value: unknown): value is GeolocationPositionError {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof (value as { code?: unknown }).code === 'number'
  );
}

function describeGeolocationError(code: number): string {
  switch (code) {
    case PERMISSION_DENIED:
      return 'Permissão de localização negada. Libere o acesso nas configurações do navegador ou informe sua localização ao ligar 193.';
    case POSITION_UNAVAILABLE:
      return 'Não foi possível obter sua localização agora. Ligue 193 e descreva o ponto de referência.';
    case TIMEOUT:
      return 'A localização demorou demais para responder. Tente novamente ou ligue 193.';
    default:
      return 'Falha ao obter a localização. Ligue 193 e informe onde você está.';
  }
}

export function SosPage() {
  const [phase, setPhase] = useState<Phase>('idle');
  const [location, setLocation] = useState<Coordinates | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [callId, setCallId] = useState<string | null>(null);
  const online = useOnlineStatus();

  /**
   * O mesmo `clientRequestId` é reutilizado em retries da mesma tentativa para
   * o backend deduplicar. Um novo ID só é gerado depois de um envio confirmado
   * ou quando o usuário inicia explicitamente um novo chamado.
   */
  const requestIdRef = useRef<string | null>(null);

  const locate = useCallback(async () => {
    if (!('geolocation' in navigator)) {
      setPhase('error');
      setMessage('Este navegador não oferece localização. Ligue 193 e informe o local.');
      return;
    }

    setPhase('locating');
    setMessage(null);

    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, GEO_OPTIONS);
      });

      const parsed = parseCoordinates({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracyMeters:
          Number.isFinite(position.coords.accuracy) && position.coords.accuracy > 0
            ? Math.min(position.coords.accuracy, 100000)
            : undefined,
      });

      setLocation(parsed);
      setPhase('ready');
    } catch (error) {
      setPhase('error');
      if (isGeolocationError(error)) {
        setMessage(describeGeolocationError(error.code));
      } else if (error instanceof ZodError) {
        setMessage('A localização recebida é inválida. Ligue 193 e informe o local.');
      } else {
        setMessage('Não foi possível obter sua localização. Ligue 193 e informe o local.');
      }
    }
  }, []);

  const send = useCallback(async () => {
    if (!location) {
      return;
    }
    if (!online) {
      setPhase('error');
      setMessage('Sem conexão com a internet. O SOS não pode ser enviado agora: ligue 193.');
      return;
    }

    const clientRequestId = requestIdRef.current ?? newRequestId();
    requestIdRef.current = clientRequestId;

    setPhase('sending');
    setMessage(null);

    try {
      const response = await createEmergencyCall({
        clientRequestId,
        latitude: location.latitude,
        longitude: location.longitude,
        accuracyMeters: location.accuracyMeters,
      });
      setCallId(response.callId);
      setPhase('sent');
    } catch (error) {
      setPhase('error');
      if (error instanceof ApiError) {
        const fieldMessage = error.problem?.errors
          ?.map((item) => `${sanitizeText(item.field, 60)}: ${sanitizeText(item.message, 120)}`)
          .join(' · ');
        setMessage(
          fieldMessage ||
            safeProblemDetail(
              error.problem?.detail,
              error.network
                ? 'O servidor não confirmou o envio. Não sabemos se o chamado chegou: ligue 193.'
                : 'O servidor recusou o chamado. Ligue 193.',
            ),
        );
      } else {
        setMessage('Falha inesperada ao enviar o SOS. Ligue 193.');
      }
    }
  }, [location, online]);

  const reset = () => {
    requestIdRef.current = null;
    setCallId(null);
    setLocation(null);
    setMessage(null);
    setPhase('idle');
  };

  const busy = phase === 'locating' || phase === 'sending';

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="SOS cidadão"
        subtitle="Avisa a central de guarda-vidas com a sua localização. Não substitui o 193."
      />

      <div className="mb-5 rounded-xl border-2 border-coral-300 bg-coral-50 px-4 py-3 text-coral-950">
        <p className="flex items-start gap-2 font-bold">
          <AlertTriangle aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
          Em risco de vida, ligue 193 primeiro.
        </p>
        <p className="mt-1 text-sm">
          O SOS depende de internet e de alguém acompanhando a central. Ele não funciona offline e
          não aciona o Samu ou o Corpo de Bombeiros.
        </p>
      </div>

      {!online ? (
        <p className="mb-5 flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm font-semibold text-amber-950">
          <WifiOff aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          Você está sem conexão. O envio do SOS está indisponível; use o telefone.
        </p>
      ) : null}

      {phase === 'sent' ? (
        <Card className="border-emerald-300 bg-emerald-50">
          <h2 className="flex items-center gap-2 text-xl font-black text-emerald-950">
            <CheckCircle2 aria-hidden="true" className="size-6 text-emerald-600" />
            Chamado registrado na central
          </h2>
          <p className="mt-2 text-sm text-emerald-900">
            O servidor confirmou o recebimento. Fique atento ao local e, se possível, sinalize para
            o guarda-vidas. Se a situação piorar, ligue 193.
          </p>
          {callId ? (
            <p className="mt-3 rounded-lg bg-white/70 px-3 py-2 text-sm">
              Protocolo: <span className="font-mono font-bold">{sanitizeText(callId, 64)}</span>
            </p>
          ) : null}
          <div className="mt-5 grid gap-2 sm:grid-cols-2">
            <a
              href="tel:193"
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-ocean-950 px-5 py-3 font-bold text-white"
            >
              <Phone aria-hidden="true" className="size-5" />
              Ligar 193
            </a>
            <button
              type="button"
              onClick={reset}
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-ocean-950 bg-white px-5 py-3 font-bold text-ocean-950"
            >
              <RotateCcw aria-hidden="true" className="size-5" />
              Novo chamado
            </button>
          </div>
        </Card>
      ) : (
        <Card>
          <h2 className="flex items-center gap-2 text-xl font-black text-ocean-950">
            <Siren aria-hidden="true" className="size-6 text-coral-600" />
            Enviar localização para a central
          </h2>

          <ol className="mt-3 grid gap-2 text-sm text-ocean-700">
            <li>1. Permita o acesso à localização quando o navegador pedir.</li>
            <li>2. Confira a precisão mostrada na tela.</li>
            <li>3. Toque em “Enviar SOS” e aguarde a confirmação do servidor.</li>
          </ol>

          {location ? (
            <dl className="mt-4 grid gap-3 rounded-xl border border-ocean-200 bg-ocean-50 p-4 sm:grid-cols-3">
              <div>
                <dt className="text-xs font-bold uppercase tracking-wide text-ocean-500">
                  Latitude
                </dt>
                <dd className="font-mono font-bold text-ocean-950">
                  {location.latitude.toFixed(5)}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wide text-ocean-500">
                  Longitude
                </dt>
                <dd className="font-mono font-bold text-ocean-950">
                  {location.longitude.toFixed(5)}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-bold uppercase tracking-wide text-ocean-500">
                  Precisão
                </dt>
                <dd className="font-bold text-ocean-950">
                  {formatAccuracy(location.accuracyMeters)}
                </dd>
              </div>
            </dl>
          ) : null}

          {message ? (
            <p
              role="alert"
              className="mt-4 rounded-xl border border-coral-300 bg-coral-50 px-4 py-3 text-sm font-semibold text-coral-950"
            >
              {message}
            </p>
          ) : null}

          <div className="mt-5 grid gap-2 sm:grid-cols-2">
            {location ? (
              <button
                type="button"
                onClick={send}
                disabled={busy || !online}
                className="inline-flex min-h-14 items-center justify-center gap-2 rounded-2xl bg-coral-600 px-5 py-3 text-lg font-black text-white shadow-lg shadow-coral-600/20 transition hover:bg-coral-500 disabled:cursor-not-allowed disabled:opacity-60 sm:col-span-2"
              >
                {phase === 'sending' ? (
                  <>
                    <Loader2 aria-hidden="true" className="size-5 animate-spin" />
                    Enviando…
                  </>
                ) : (
                  <>
                    <Siren aria-hidden="true" className="size-5" />
                    {phase === 'error' ? 'Tentar enviar novamente' : 'Enviar SOS'}
                  </>
                )}
              </button>
            ) : (
              <button
                type="button"
                onClick={locate}
                disabled={busy}
                className="inline-flex min-h-14 items-center justify-center gap-2 rounded-2xl bg-ocean-950 px-5 py-3 text-lg font-black text-white transition hover:bg-ocean-900 disabled:cursor-not-allowed disabled:opacity-60 sm:col-span-2"
              >
                {phase === 'locating' ? (
                  <>
                    <Loader2 aria-hidden="true" className="size-5 animate-spin" />
                    Obtendo localização…
                  </>
                ) : (
                  <>
                    <Crosshair aria-hidden="true" className="size-5" />
                    Usar minha localização
                  </>
                )}
              </button>
            )}

            <a
              href="tel:193"
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-ocean-950 bg-white px-5 py-3 font-bold text-ocean-950"
            >
              <Phone aria-hidden="true" className="size-5" />
              Ligar 193
            </a>

            {location ? (
              <button
                type="button"
                onClick={locate}
                disabled={busy}
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border border-ocean-300 bg-white px-5 py-3 font-bold text-ocean-800 disabled:opacity-60"
              >
                <RotateCcw aria-hidden="true" className="size-4" />
                Atualizar localização
              </button>
            ) : (
              <Link
                to="/praias"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border border-ocean-300 bg-white px-5 py-3 font-bold text-ocean-800"
              >
                Ver praias próximas
              </Link>
            )}
          </div>
        </Card>
      )}

      <Card className="mt-6">
        <h2 className="flex items-center gap-2 font-bold text-ocean-950">
          <Lock aria-hidden="true" className="size-5 text-ocean-600" />
          O que acontece com a sua localização
        </h2>
        <ul className="mt-2 grid gap-1 text-sm text-ocean-700">
          <li>• Ela é enviada ao servidor apenas quando você toca em “Enviar SOS”.</li>
          <li>• Não fica salva no armazenamento do navegador nem em histórico local.</li>
          <li>• No servidor, o chamado tem acesso restrito e o evento durável é cifrado.</li>
          <li>• Se o envio falhar, a interface nunca afirma que o chamado chegou.</li>
        </ul>
      </Card>
    </div>
  );
}
