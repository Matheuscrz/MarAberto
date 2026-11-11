import {
  AlertTriangle,
  CheckCircle,
  ExternalLink,
  MapPin,
  Navigation,
  Radio,
  RefreshCw,
  Siren,
  Timer,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import {
  formatAccuracy,
  formatDateTime,
  formatDistance,
} from "@/shared/lib/format";
import type {
  DispatchAssignedCall,
  DispatchOpenCall,
} from "@/features/operator/types";
import {
  useSosConsole,
  type ConsoleFeedback,
} from "@/features/operator/lib/use-sos-console";
import type { SosRealtimeState } from "@/features/operator/lib/sos-realtime";
import { useSosAlert } from "@/features/operator/lib/use-sos-alert";
import { ShiftPanel } from "@/features/operator/components/ShiftPanel";
import { SosAlertOverlay } from "@/features/operator/components/SosAlertOverlay";
import { SosAlertActivation } from "@/features/operator/components/SosAlertActivation";
import { placeUrl, routeUrl } from "@/features/operator/lib/maps";

const REALTIME_LABEL: Record<SosRealtimeState, string> = {
  live: "Tempo real",
  connecting: "Reconectando",
  offline: "Sem tempo real",
};

const REALTIME_STYLE: Record<SosRealtimeState, string> = {
  live: "border-coral-300 bg-coral-50 text-coral-900",
  connecting: "border-amber-300 bg-amber-50 text-amber-900",
  offline: "border-ocean-200 bg-white text-ocean-600",
};

const FEEDBACK_STYLE: Record<ConsoleFeedback["tone"], string> = {
  success: "border-emerald-300 bg-emerald-50 text-emerald-900",
  warning: "border-amber-300 bg-amber-50 text-amber-900",
  error: "border-coral-300 bg-coral-50 text-coral-900",
};

/**
 * Fila de SOS da mesa do guarda-vidas: dados reais do despacho (ADR-018),
 * notificação por WebSocket autenticado e assunção por compare-and-set.
 *
 * <p>A lista é sempre a do banco — o socket só antecipa e o polling reconcilia.
 * As coordenadas chegam pela rota autenticada, nunca pelo frame.
 */
export function OperatorSosPage() {
  const {
    calls,
    assigned,
    incoming,
    realtime,
    loading,
    loadError,
    feedback,
    acknowledgingId,
    refresh,
    acknowledge,
    dismissFeedback,
  } = useSosConsole();

  const sosAlert = useSosAlert({ calls, incoming });
  const alertCall =
    sosAlert.alert === null
      ? null
      : (calls.find((call) => call.callId === sosAlert.alert?.callId) ?? null);

  const nearest = calls.reduce<number | null>((closest, call) => {
    if (call.distanceMeters === null) return closest;
    if (closest === null) return call.distanceMeters;
    return Math.min(closest, call.distanceMeters);
  }, null);
  const highestRound = calls.reduce(
    (max, call) => Math.max(max, call.round),
    0,
  );

  return (
    <div>
      {sosAlert.alert ? (
        <SosAlertOverlay
          alert={sosAlert.alert}
          call={alertCall}
          acknowledging={acknowledgingId === sosAlert.alert.callId}
          soundReady={sosAlert.soundReady}
          onAcknowledge={acknowledge}
          onMute={sosAlert.mute}
          onPlay={sosAlert.play}
          onDismiss={sosAlert.dismiss}
        />
      ) : null}

      <PageHeader
        title="Alertas SOS"
        subtitle="Chamados enviados pelo aplicativo do banhista. Assuma o atendimento — o primeiro que confirmar fica com o chamado."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <SosAlertActivation
              permission={sosAlert.permission}
              soundReady={sosAlert.soundReady}
              activating={sosAlert.activating}
              onActivate={() => {
                void sosAlert.activate();
              }}
            />
            <span
              className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${REALTIME_STYLE[realtime]}`}
            >
              {realtime === "live" ? (
                <Wifi aria-hidden="true" className="size-3.5" />
              ) : (
                <WifiOff aria-hidden="true" className="size-3.5" />
              )}
              {REALTIME_LABEL[realtime]}
            </span>
            <button
              type="button"
              onClick={refresh}
              className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
            >
              <RefreshCw aria-hidden="true" className="size-3.5" />
              Atualizar
            </button>
          </div>
        }
      />

      <ShiftPanel
        onArmAlerts={() => {
          void sosAlert.activate();
        }}
      />

      {feedback ? (
        <output
          className={`mb-4 flex items-start justify-between gap-3 rounded-xl border px-3 py-2 text-xs font-semibold ${FEEDBACK_STYLE[feedback.tone]}`}
        >
          <span className="flex items-start gap-1.5">
            {feedback.tone === "success" ? (
              <CheckCircle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            ) : (
              <AlertTriangle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            )}
            {feedback.message}
          </span>
          <button
            type="button"
            onClick={dismissFeedback}
            aria-label="Fechar aviso"
            className="shrink-0 rounded p-0.5 transition hover:bg-black/5"
          >
            <X aria-hidden="true" className="size-3.5" />
          </button>
        </output>
      ) : null}

      {loadError ? (
        <output className="mb-4 block rounded-xl border border-coral-300 bg-coral-50 px-3 py-2 text-xs font-semibold text-coral-900">
          {loadError}
        </output>
      ) : null}

      <dl className="mb-4 grid grid-cols-3 gap-2">
        <SummaryTile
          label="Aguardando você"
          value={String(calls.length)}
          tone="border-coral-200 bg-coral-50 text-coral-900"
        />
        <SummaryTile
          label="Rodada"
          value={highestRound > 0 ? String(highestRound) : "—"}
          tone="border-ocean-200 bg-white text-ocean-900"
        />
        <SummaryTile
          label="Mais próximo"
          value={nearest === null ? "—" : formatDistance(nearest)}
          tone="border-ocean-200 bg-white text-ocean-900"
        />
      </dl>

      {assigned.length > 0 ? (
        <section aria-labelledby="assigned-title" className="mb-4">
          <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
            <h2
              id="assigned-title"
              className="text-sm font-black uppercase tracking-wider text-ocean-900"
            >
              Em atendimento por você
            </h2>
            <span className="text-[11px] font-semibold text-ocean-500">
              O chamado fica aqui até o atendimento ser registrado na ocorrência.
            </span>
          </div>
          <ul className="grid gap-3">
            {assigned.map((call) => (
              <li key={call.callId}>
                <AssignedCard call={call} />
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {loading && calls.length === 0 ? (
        <output className="block rounded-2xl border border-dashed border-ocean-300 bg-white p-8 text-center text-sm font-semibold text-ocean-600">
          Carregando a fila do SOS…
        </output>
      ) : calls.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-ocean-300 bg-white p-8 text-center text-sm font-semibold text-ocean-600">
          Nenhum chamado aberto na sua fila. O alerta aparece aqui assim que o
          banco identificar você como destinatário.
        </p>
      ) : (
        <ul className="grid gap-3">
          {calls.map((call) => (
            <li key={call.callId}>
              <CallCard
                call={call}
                acknowledging={acknowledgingId === call.callId}
                onAcknowledge={acknowledge}
              />
            </li>
          ))}
        </ul>
      )}

      <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-relaxed text-ocean-500">
        <MapPin aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
        Coordenadas precisas do banhista têm uso restrito ao atendimento: não
        aparecem em listagens públicas, logs nem na notificação em tempo real.
      </p>
    </div>
  );
}

interface CallCardProps {
  readonly call: DispatchOpenCall;
  readonly acknowledging: boolean;
  readonly onAcknowledge: (callId: string) => void;
}

const STATUS_LABEL: Record<string, string> = {
  PENDING: "Aguardando despacho",
  DISPATCHED: "Despacho ativo",
  ACKNOWLEDGED: "Assumido",
  RESOLVED: "Atendido",
  CANCELED: "Cancelado",
};

function CallCard({ call, acknowledging, onAcknowledge }: CallCardProps) {
  return (
    <article className="rounded-2xl border border-coral-300 bg-white p-4 shadow-sm ring-1 ring-coral-200">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-md bg-coral-600 px-2 py-0.5 text-[10px] font-black uppercase tracking-wider text-white">
              <Siren aria-hidden="true" className="size-3" />
              SOS banhista
            </span>
            <span className="font-mono text-xs font-bold text-ocean-900">
              {call.callId.slice(0, 8)}
            </span>
            <span className="inline-flex items-center gap-1 rounded-full border border-ocean-300 bg-ocean-50 px-2 py-0.5 text-[10px] font-bold text-ocean-900">
              {STATUS_LABEL[call.status] ?? call.status}
            </span>
            <span className="rounded-full border border-ocean-200 bg-white px-2 py-0.5 text-[10px] font-bold text-ocean-600">
              rodada {call.round}
            </span>
          </div>
          <p className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-ocean-600">
            <span className="inline-flex items-center gap-1">
              <Timer aria-hidden="true" className="size-3.5" />
              Recebido em {formatDateTime(call.createdAt)}
            </span>
            <span className="inline-flex items-center gap-1">
              <Radio aria-hidden="true" className="size-3.5" />
              {formatDistance(call.distanceMeters)}{" "}
              {distanceSource(call.positionStale)}
            </span>
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <a
            href={placeUrl(call.latitude, call.longitude)}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
          >
            <ExternalLink aria-hidden="true" className="size-3.5" />
            Ver no GPS
          </a>
          <button
            type="button"
            onClick={() => onAcknowledge(call.callId)}
            disabled={acknowledging}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:cursor-progress disabled:opacity-70"
          >
            <CheckCircle aria-hidden="true" className="size-3.5" />
            {acknowledging ? "Assumindo…" : "Assumir chamado"}
          </button>
        </div>
      </div>

      {call.positionStale ? (
        <p className="mt-2 flex items-start gap-1.5 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1.5 text-[11px] font-semibold text-amber-900">
          <AlertTriangle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          A distância usa a posição cadastrada da sua unidade, não a do seu
          aparelho. Atualize a localização do plantão para medir de onde você
          está.
        </p>
      ) : null}

      <dl className="mt-3 grid grid-cols-3 gap-2 rounded-xl border border-ocean-100 bg-ocean-50/60 p-2.5 text-center text-xs">
        <div>
          <dt className="text-[10px] font-bold uppercase text-ocean-500">
            Latitude
          </dt>
          <dd className="font-mono font-bold text-ocean-900">
            {call.latitude.toFixed(5)}
          </dd>
        </div>
        <div>
          <dt className="text-[10px] font-bold uppercase text-ocean-500">
            Longitude
          </dt>
          <dd className="font-mono font-bold text-ocean-900">
            {call.longitude.toFixed(5)}
          </dd>
        </div>
        <div>
          <dt className="text-[10px] font-bold uppercase text-ocean-500">
            Precisão
          </dt>
          <dd className="font-bold text-ocean-900">
            {call.accuracyMeters === null
              ? "não informada"
              : formatAccuracy(call.accuracyMeters)}
          </dd>
        </div>
      </dl>
    </article>
  );
}

/**
 * Chamado já assumido: o atendimento em curso.
 *
 * <p>Assumir tira o chamado da fila de disputa, mas **não** pode apagar o
 * endereço do banhista: é com ele que o guarda-vidas se desloca (rota no app de
 * mapas) e é dele que nasce a ocorrência operacional (ADR-013). A coordenada
 * continua só nesta rota autenticada.
 */
function AssignedCard({ call }: { readonly call: DispatchAssignedCall }) {
  const distance = currentDistance(call);

  return (
    <article className="rounded-2xl border border-emerald-300 bg-white p-4 shadow-sm ring-1 ring-emerald-200">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex items-center gap-1 rounded-md bg-emerald-700 px-2 py-0.5 text-[10px] font-black uppercase tracking-wider text-white">
              <CheckCircle aria-hidden="true" className="size-3" />
              Em atendimento
            </span>
            <span className="font-mono text-xs font-bold text-ocean-900">
              {call.callId.slice(0, 8)}
            </span>
            <span className="rounded-full border border-ocean-200 bg-white px-2 py-0.5 text-[10px] font-bold text-ocean-600">
              rodada {call.round}
            </span>
          </div>
          <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ocean-600">
            <span className="inline-flex items-center gap-1">
              <Timer aria-hidden="true" className="size-3.5" />
              Recebido em {formatDateTime(call.createdAt)}
            </span>
            <span className="inline-flex items-center gap-1">
              <CheckCircle aria-hidden="true" className="size-3.5" />
              Assumido em {formatDateTime(call.acknowledgedAt)}
            </span>
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <a
            href={routeUrl(call.latitude, call.longitude)}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-emerald-700 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-emerald-800"
          >
            <Navigation aria-hidden="true" className="size-3.5" />
            Abrir rota até o banhista
          </a>
          <a
            href={placeUrl(call.latitude, call.longitude)}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
          >
            <ExternalLink aria-hidden="true" className="size-3.5" />
            Ver ponto no mapa
          </a>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-baseline gap-x-3 gap-y-1 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2">
        <span className="text-[10px] font-black uppercase tracking-wider text-emerald-800">
          Distância agora
        </span>
        <span className="text-2xl font-black leading-none text-emerald-900">
          {formatDistance(distance.meters)}
        </span>
        <span className="text-xs font-semibold text-emerald-800">
          {distance.label}
        </span>
        <span className="text-[11px] text-emerald-700">
          {distance.when === null
            ? "na abertura do chamado"
            : `posição de ${formatDateTime(distance.when)}`}
        </span>
      </div>
      <p className="mt-1 text-[11px] leading-relaxed text-emerald-800">
        {distance.live
          ? "Atualizada com a posição do seu plantão; use-a para decidir entre deslocamento a pé e de veículo."
          : "Sem posição do plantão, vale a distância da abertura do chamado. Atualize sua localização para medir de onde você está."}
      </p>
      {distance.live && call.currentPositionStale ? (
        <p className="mt-1 flex items-start gap-1.5 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1.5 text-[11px] font-semibold text-amber-900">
          <AlertTriangle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          Sua última posição no plantão está desatualizada: a distância é a da
          última posição conhecida, não necessariamente de onde você está agora.
        </p>
      ) : null}

      <dl className="mt-3 grid grid-cols-3 gap-2 rounded-xl border border-ocean-100 bg-ocean-50/60 p-2.5 text-center text-xs">
        <div>
          <dt className="text-[10px] font-bold uppercase text-ocean-500">
            Latitude
          </dt>
          <dd className="font-mono font-bold text-ocean-900">
            {call.latitude.toFixed(5)}
          </dd>
        </div>
        <div>
          <dt className="text-[10px] font-bold uppercase text-ocean-500">
            Longitude
          </dt>
          <dd className="font-mono font-bold text-ocean-900">
            {call.longitude.toFixed(5)}
          </dd>
        </div>
        <div>
          <dt className="text-[10px] font-bold uppercase text-ocean-500">
            Precisão
          </dt>
          <dd className="font-bold text-ocean-900">
            {call.accuracyMeters === null
              ? "não informada"
              : formatAccuracy(call.accuracyMeters)}
          </dd>
        </div>
      </dl>

      <p className="mt-2 flex items-start gap-1.5 text-[11px] leading-relaxed text-ocean-500">
        <MapPin aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
        A rota abre o app de mapas do aparelho e envia a coordenada do banhista
        só quando você toca. Este atendimento fica na mesa para virar a
        ocorrência no CIOSP.
      </p>
    </article>
  );
}

/**
 * De onde saiu a distância: da posição do plantão ou do cadastro da estrutura.
 * Sem posição reportada, dizer "de você" seria mentira — e foi assim que um
 * chamado a poucos metros apareceu com 200 km.
 */
function distanceSource(positionStale: boolean): string {
  return positionStale ? "da sua unidade" : "de você";
}

interface DistanceNow {
  readonly meters: number | null;
  readonly label: string;
  readonly when: string | null;
  /** `true` quando a distância vem da posição atual do plantão. */
  readonly live: boolean;
}

/**
 * Distância do guarda-vidas até o banhista **agora**.
 *
 * <p>Depois do ack o operador se desloca: repetir a distância da abertura do
 * chamado esconderia a aproximação e atrapalharia a decisão de ir a pé ou de
 * veículo. O servidor calcula da última posição do plantão a cada consulta;
 * sem posição, a distância da abertura é o fallback — e assim é rotulada.
 */
function currentDistance(call: DispatchAssignedCall): DistanceNow {
  if (call.currentDistanceMeters !== null) {
    return {
      meters: call.currentDistanceMeters,
      label: call.currentPositionStale
        ? "da sua última posição"
        : "de você",
      when: call.currentPositionAt,
      live: true,
    };
  }
  return {
    meters: call.distanceMeters,
    label: distanceSource(call.positionStale),
    when: null,
    live: false,
  };
}

interface SummaryTileProps {
  readonly label: string;
  readonly value: string;
  readonly tone: string;
}

function SummaryTile({ label, value, tone }: SummaryTileProps) {
  return (
    <div className={`rounded-xl border p-3 text-center ${tone}`}>
      <dt className="text-[10px] font-bold uppercase tracking-wider opacity-70">
        {label}
      </dt>
      <dd className="text-lg font-black">{value}</dd>
    </div>
  );
}
