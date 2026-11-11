import { useRef } from "react";
import {
  BellRing,
  CheckCircle,
  MapPin,
  Siren,
  Timer,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";
import { formatDateTime, formatDistance } from "@/shared/lib/format";
import { useOverlay } from "@/shared/lib/overlay";
import type { DispatchOpenCall } from "@/features/operator/types";
import type { SosAlert } from "@/features/operator/lib/use-sos-alert";

interface SosAlertOverlayProps {
  readonly alert: SosAlert;
  /** Chamado já reconciliado pela fila; `null` enquanto o frame antecipou. */
  readonly call: DispatchOpenCall | null;
  readonly acknowledging: boolean;
  readonly soundReady: boolean;
  readonly onAcknowledge: (callId: string) => void;
  readonly onMute: () => void;
  readonly onPlay: () => void;
  readonly onDismiss: () => void;
}

/**
 * Popup do SOS em tela cheia.
 *
 * <p>É a camada que não depende de o operador estar olhando a lista: aparece
 * sobre qualquer tela do console, prende o foco e mostra só o necessário para
 * decidir — distância aproximada, horário e rodada. Coordenada precisa e dado
 * do banhista continuam apenas na rota autenticada, atrás do gesto de assumir.
 *
 * <p>Fechar o popup **não** assume nem descarta o chamado: ele segue na fila.
 * Escape e o botão de fechar encerram o alerta; a sirene para junto para não
 * tocar numa tela vazia.
 */
export function SosAlertOverlay({
  alert,
  call,
  acknowledging,
  soundReady,
  onAcknowledge,
  onMute,
  onPlay,
  onDismiss,
}: SosAlertOverlayProps) {
  const panel = useRef<HTMLDivElement | null>(null);
  useOverlay(true, panel, onDismiss);

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-ocean-950/70 p-4 backdrop-blur-sm">
      <dialog
        open
        aria-modal="true"
        aria-labelledby="sos-alert-title"
        aria-describedby="sos-alert-description"
        className="w-full max-w-md rounded-3xl border-4 border-coral-600 bg-white p-0 text-ocean-950 shadow-2xl"
      >
        <div ref={panel} className="p-5">
          <div className="flex items-start justify-between gap-3">
            <span className="grid size-12 shrink-0 place-items-center rounded-2xl bg-coral-600 text-white motion-safe:animate-pulse">
              <Siren aria-hidden="true" className="size-6" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[11px] font-black uppercase tracking-wider text-coral-700">
                Alerta operacional
              </p>
              <h2
                id="sos-alert-title"
                className="text-lg font-black leading-tight text-ocean-950"
              >
                SOS na sua área
              </h2>
            </div>
            <button
              type="button"
              onClick={onDismiss}
              aria-label="Fechar alerta (o chamado continua na fila)"
              className="shrink-0 rounded-lg p-1.5 text-ocean-600 transition hover:bg-ocean-100"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
          </div>

          <p
            id="sos-alert-description"
            className="mt-3 text-sm leading-relaxed text-ocean-800"
          >
            Um banhista pediu ajuda
            {call ? (
              <>
                {" "}
                a <strong>{formatDistance(call.distanceMeters)}</strong>{" "}
                {call.positionStale ? "da sua unidade" : "de você"}.
              </>
            ) : (
              "."
            )}{" "}
            {call?.positionStale
              ? "A posição do seu aparelho não está no plantão: atualize a localização para medir de onde você está."
              : "Assuma o chamado para liberar o local exato e a rota."}
          </p>

          <dl className="mt-3 grid grid-cols-2 gap-2 rounded-xl border border-ocean-100 bg-ocean-50/70 p-2.5 text-xs">
            <div>
              <dt className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-ocean-500">
                <Timer aria-hidden="true" className="size-3" />
                Recebido
              </dt>
              <dd className="font-bold text-ocean-900">
                {formatDateTime(call?.createdAt ?? alert.startedAt)}
              </dd>
            </div>
            <div>
              <dt className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-ocean-500">
                <BellRing aria-hidden="true" className="size-3" />
                Chamado
              </dt>
              <dd className="font-bold text-ocean-900">
                {alert.callId.slice(0, 8)}
                {call ? ` · rodada ${call.round}` : ""}
              </dd>
            </div>
          </dl>

          {call === null ? (
            <p className="mt-2 text-[11px] font-semibold text-amber-800">
              O frame chegou antes da fila. O detalhe aparece assim que o
              servidor responder.
            </p>
          ) : null}

          <div className="mt-4 flex flex-col gap-2">
            <button
              type="button"
              data-autofocus
              onClick={() => onAcknowledge(alert.callId)}
              disabled={acknowledging}
              className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-coral-600 px-4 py-3 text-sm font-black text-white transition hover:bg-coral-700 disabled:cursor-progress disabled:opacity-70"
            >
              <CheckCircle aria-hidden="true" className="size-5" />
              {acknowledging ? "Assumindo…" : "Assumir chamado agora"}
            </button>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={alert.muted ? onPlay : onMute}
                className="inline-flex min-h-9 flex-1 items-center justify-center gap-1.5 rounded-xl border border-ocean-200 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
              >
                {alert.muted ? (
                  <Volume2 aria-hidden="true" className="size-4" />
                ) : (
                  <VolumeX aria-hidden="true" className="size-4" />
                )}
                {alert.muted ? "Ligar sirene" : "Silenciar sirene"}
              </button>
              <button
                type="button"
                onClick={onDismiss}
                className="inline-flex min-h-9 flex-1 items-center justify-center gap-1.5 rounded-xl border border-ocean-200 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
              >
                <X aria-hidden="true" className="size-4" />
                Fechar alerta
              </button>
            </div>
          </div>

          {!soundReady ? (
            <p className="mt-3 flex items-start gap-1.5 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1.5 text-[11px] font-semibold text-amber-900">
              <VolumeX aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
              O navegador ainda não liberou o som: toque em qualquer lugar da
              tela para a sirene entrar.
            </p>
          ) : null}

          <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-relaxed text-ocean-500">
            <MapPin aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            A coordenada do banhista só aparece depois que você assume. Se
            outra pessoa assumir antes, este alerta fecha sozinho.
          </p>
        </div>
      </dialog>
    </div>
  );
}
