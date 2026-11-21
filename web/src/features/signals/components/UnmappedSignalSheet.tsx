import { useRef } from "react";
import { Link } from "react-router-dom";
import { Clock, ExternalLink, MapPinOff, Radio, Timer, X } from "lucide-react";
import { FlagBadge } from "@/shared/components/Badges";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useOverlay } from "@/shared/lib/overlay";
import { SignalPhotos } from "@/features/signals/components/SignalPhotos";
import type { PublicUnmappedSignal } from "@/features/signals/types";

interface UnmappedSignalSheetProps {
  readonly alert: PublicUnmappedSignal;
  readonly onClose: () => void;
  /**
   * Link para a fila de triagem. Só existe no mapa da coordenação: o banhista
   * vê o alerta, mas não tem acesso à área restrita.
   */
  readonly triageHref?: string;
}

/**
 * Ficha do alerta lançado fora do mapeamento.
 *
 * <p>Não é a ficha de uma praia: não há área, setor nem posto responsável para
 * mostrar. O que o banhista precisa saber é que existe uma sinalização naquele
 * ponto e que a área ainda não está no mapa oficial. A validade é a mesma da
 * bandeira e pode ser conferida aqui.
 */
export function UnmappedSignalSheet({
  alert,
  onClose,
  triageHref,
}: UnmappedSignalSheetProps) {
  const panelRef = useRef<HTMLDialogElement | null>(null);
  useOverlay(true, panelRef, onClose);

  return (
    <div className="pointer-events-auto absolute inset-x-2 bottom-2 z-40 md:inset-x-auto md:bottom-4 md:right-4 md:w-96">
      <dialog
        ref={panelRef}
        open
        aria-modal="false"
        aria-label="Alerta em área não mapeada"
        className="relative m-0 block max-h-[70dvh] w-full overflow-y-auto rounded-2xl border border-ocean-900/10 bg-white/95 p-4 shadow-2xl backdrop-blur"
      >
        <div className="flex items-start justify-between gap-2 border-b border-ocean-100 pb-3">
          <div>
            <FlagBadge flag={alert.flag} />
            <h2 className="mt-2 text-base font-black text-ocean-950">
              Área não mapeada
            </h2>
            <p className="flex items-center gap-1 text-xs text-ocean-600">
              <MapPinOff aria-hidden="true" className="size-3" />
              Ponto informado pelo guarda-vidas em campo
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar alerta"
            className="grid size-9 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-50"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        <dl className="mt-3 grid gap-1.5 rounded-xl border border-amber-200 bg-amber-50/70 p-3 text-[11px] text-ocean-700">
          <div className="flex items-center gap-1.5">
            <Clock aria-hidden="true" className="size-3.5 text-ocean-500" />
            <dt className="sr-only">Lançada em</dt>
            <dd>Lançada em {formatDateTime(alert.createdAt)}</dd>
          </div>
          <div className="flex items-center gap-1.5">
            <Timer aria-hidden="true" className="size-3.5 text-ocean-500" />
            <dt className="sr-only">Alerta válido até</dt>
            <dd>Alerta válido até {formatDateTime(alert.validUntil)}</dd>
          </div>
        </dl>

        {alert.notes ? (
          <p className="mt-3 text-xs leading-relaxed text-ocean-700">
            <strong>Observações:</strong> {sanitizeText(alert.notes, 300)}
          </p>
        ) : null}

        <SignalPhotos urls={alert.photoUrls} label="área não mapeada" />

        <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-snug text-ocean-600">
          <Radio aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          A coordenação ainda está identificando esta área. O alerta vale só
          enquanto estiver dentro do prazo — em caso de dúvida, confirme no
          posto mais próximo antes de entrar na água.
        </p>

        {triageHref ? (
          <Link
            to={triageHref}
            className="mt-3 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 py-1.5 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50"
          >
            <ExternalLink aria-hidden="true" className="size-3.5" />
            Abrir a triagem da coordenação
          </Link>
        ) : null}
      </dialog>
    </div>
  );
}
