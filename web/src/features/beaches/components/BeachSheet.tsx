import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronDown, MapPin } from 'lucide-react';
import { FlagBadge, RiskBadge } from '@/shared/components/Badges';
import { formatOccupancy } from '@/shared/lib/format';
import { sanitizeText } from '@/shared/lib/sanitize';
import type { Beach } from "@/features/beaches/types";
import { useOverlay } from '@/shared/lib/overlay';

/**
 * Ficha rápida da praia sobre o mapa (RF02): bandeira, risco, ocupação e as
 * orientações de cada zona. O toque em uma zona já amarela? Incentive o
 * guarda-vidas e o 193.
 */
export function BeachSheet({ beach, onClose }: { beach: Beach; onClose: () => void }) {
  const panelRef = useRef<HTMLDivElement | null>(null);

  // Foco inicial, Escape/Tab e retorno de foco quando a ficha fecha.
  useOverlay(true, panelRef, onClose);

  return (
    <div className="absolute inset-x-2 bottom-2 z-30 md:inset-x-auto md:bottom-4 md:right-4 md:w-96">
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="false"
        aria-label={`Ficha da praia ${sanitizeText(beach.name)}`}
        className="max-h-[55%] overflow-y-auto rounded-2xl border border-ocean-900/10 bg-white/95 p-4 shadow-2xl backdrop-blur md:max-h-[70%]"
      >
        <div className="flex items-start justify-between gap-2">
          <div>
            <h2 className="text-xl font-black text-ocean-950">{sanitizeText(beach.name)}</h2>
            <p className="mt-0.5 flex items-center gap-1 text-sm text-ocean-600">
              <MapPin aria-hidden="true" className="size-3.5" />
              {sanitizeText(beach.municipality)} — {sanitizeText(beach.state)}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar ficha da praia"
            className="grid size-10 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-50"
          >
            <ChevronDown aria-hidden="true" className="size-5" />
          </button>
        </div>

        <div className="mt-3 flex flex-wrap gap-2">
          <FlagBadge flag={beach.flag} />
          <RiskBadge level={beach.riskLevel} />
        </div>

        <dl className="mt-3 grid grid-cols-2 gap-2 rounded-xl border border-ocean-200 bg-ocean-50 p-3 text-sm">
          <div>
            <dt className="text-ocean-500">Ocupação estimada</dt>
            <dd className="font-bold text-ocean-950">{formatOccupancy(beach.occupancyPercent)}</dd>
          </div>
          <div>
            <dt className="text-ocean-500">Riscos mapeados</dt>
            <dd className="font-bold text-ocean-950">{beach.risks.length}</dd>
          </div>
        </dl>

        <ul className="mt-3 grid gap-2">
          {beach.risks.map((risk) => (
            <li key={risk.id} className="rounded-xl border border-ocean-200 bg-white p-3">
              <p className="text-sm font-black text-coral-800">{sanitizeText(risk.label, 80)}</p>
              <p className="mt-1 text-sm text-ocean-700">{sanitizeText(risk.description, 220)}</p>
              <ul className="mt-2 list-disc pl-4 text-xs text-ocean-700">
                {risk.guidance.map((item) => (
                  <li key={item}>{sanitizeText(item, 140)}</li>
                ))}
              </ul>
            </li>
          ))}
        </ul>

        <Link
          to={`/praias/${beach.id}`}
          data-autofocus=""
          className="mt-3 inline-flex min-h-11 w-full items-center justify-center rounded-xl bg-ocean-950 px-4 py-2 font-bold text-white"
        >
          Ver detalhes completos
        </Link>
      </div>
    </div>
  );
}
