import { Clock, MapPin, Radio, ShieldCheck, Timer, X } from "lucide-react";
import { FlagBadge } from "@/shared/components/Badges";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useOverlay } from "@/shared/lib/overlay";
import { useRef } from "react";
import { SignalStateTag } from "@/features/signals/components/SignalStateTag";
import { signalState, type BeachFlagSignal } from "@/features/signals/types";
import { SignalPhotos } from "@/features/signals/components/SignalPhotos";

interface SignalSheetProps {
  readonly signal: BeachFlagSignal;
  readonly onClose: () => void;
}

/**
 * Ficha da sinalização aberta pelo pin do mapa.
 *
 * <p>Só chega aqui bandeira vigente (o servidor filtra), mas o selo de estado é
 * calculado de novo no cliente: uma ficha aberta por horas não pode continuar
 * dizendo "vigente" depois da validade.
 */
export function SignalSheet({ signal, onClose }: SignalSheetProps) {
  const panelRef = useRef<HTMLDialogElement | null>(null);
  useOverlay(true, panelRef, onClose);
  const state = signalState(signal);

  return (
    <div className="pointer-events-auto absolute inset-x-2 bottom-2 z-40 md:inset-x-auto md:bottom-4 md:right-4 md:w-96">
      <dialog
        ref={panelRef}
        open
        aria-modal="false"
        aria-label={`Sinalização da ${sanitizeText(signal.beachName)}`}
        className="relative m-0 block max-h-[70dvh] w-full overflow-y-auto rounded-2xl border border-ocean-900/10 bg-white/95 p-4 shadow-2xl backdrop-blur"
      >
        <div className="flex items-start justify-between gap-2 border-b border-ocean-100 pb-3">
          <div>
            <FlagBadge flag={signal.flag} />
            <h2 className="mt-2 text-base font-black text-ocean-950">
              {sanitizeText(signal.beachName)}
            </h2>
            <p className="flex items-center gap-1 text-xs text-ocean-600">
              <MapPin aria-hidden="true" className="size-3" />
              {signal.sectorLabel
                ? sanitizeText(signal.sectorLabel, 80)
                : "Área inteira"}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar sinalização"
            className="grid size-9 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-50"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        <div className="mt-3">
          <SignalStateTag state={state} />
        </div>

        <dl className="mt-3 grid gap-1.5 rounded-xl border border-ocean-100 bg-ocean-50/70 p-3 text-[11px] text-ocean-700">
          <div className="flex items-center gap-1.5">
            <Clock aria-hidden="true" className="size-3.5 text-ocean-500" />
            <dt className="sr-only">Lançada em</dt>
            <dd>Lançada em {formatDateTime(signal.postedAt)}</dd>
          </div>
          <div className="flex items-center gap-1.5">
            <Timer aria-hidden="true" className="size-3.5 text-ocean-500" />
            <dt className="sr-only">Válida até</dt>
            <dd>
              {state === "EXPIRED" ? "Venceu em " : "Válida até "}
              {formatDateTime(signal.validUntil)}
            </dd>
          </div>
          {signal.stationName ? (
            <div className="flex items-center gap-1.5">
              <ShieldCheck
                aria-hidden="true"
                className="size-3.5 text-emerald-600"
              />
              <dt className="sr-only">Posto responsável</dt>
              <dd>{sanitizeText(signal.stationName, 80)}</dd>
            </div>
          ) : null}
        </dl>

        {signal.notes ? (
          <p className="mt-3 text-xs leading-relaxed text-ocean-700">
            <strong>Observações:</strong> {sanitizeText(signal.notes, 300)}
          </p>
        ) : null}

        <SignalPhotos
          urls={signal.photoUrls}
          label={signal.sectorLabel ?? signal.beachName}
        />

        <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-snug text-ocean-600">
          <Radio aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          A bandeira é lançada pelo guarda-vidas da área. Em caso de dúvida,
          confirme no posto antes de entrar na água.
        </p>
      </dialog>
    </div>
  );
}
