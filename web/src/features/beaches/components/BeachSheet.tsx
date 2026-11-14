import { useRef } from "react";
import { Link } from "react-router-dom";
import {
  ChevronDown,
  MapPin,
  Navigation,
  Waves,
} from "lucide-react";
import { BEACH_KIND_LABEL, type Beach } from "@/features/beaches";
import { formatDistance } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useOverlay } from "@/shared/lib/overlay";

/**
 * Ficha rápida da área de banho sobre o mapa: tipo, município e quantos setores
 * o mapeamento traz. Quando a posição da pessoa cai na praia aberta, mostra em
 * qual setor ela está.
 */
export function BeachSheet({
  beach,
  currentSectorLabel,
  onClose,
}: {
  beach: Beach;
  currentSectorLabel?: string | null;
  onClose: () => void;
}) {
  const panelRef = useRef<HTMLDivElement | null>(null);

  // Foco inicial, Escape/Tab e retorno de foco quando a ficha fecha.
  useOverlay(true, panelRef, onClose);

  return (
    <div className="pointer-events-auto absolute inset-x-2 bottom-2 z-30 md:inset-x-auto md:bottom-4 md:right-4 md:w-96">
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="false"
        aria-label={`Ficha da praia ${sanitizeText(beach.name)}`}
        className="max-h-[55%] overflow-y-auto rounded-2xl border border-ocean-900/10 bg-white/95 p-4 shadow-2xl backdrop-blur md:max-h-[70%]"
      >
        <div className="flex items-start justify-between gap-2">
          <div>
            <h2 className="text-xl font-black text-ocean-950">
              {sanitizeText(beach.name)}
            </h2>
            <p className="mt-0.5 flex items-center gap-1 text-sm text-ocean-600">
              <MapPin aria-hidden="true" className="size-3.5" />
              {[beach.municipality, beach.state].filter(Boolean).join(" — ")}
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
          <span className="inline-flex rounded-full bg-ocean-50 px-3 py-1 text-xs font-bold text-ocean-800">
            {BEACH_KIND_LABEL[beach.kind]}
          </span>
          <span className="inline-flex items-center gap-1 rounded-full bg-ocean-50 px-3 py-1 text-xs font-bold text-ocean-800">
            <Waves aria-hidden="true" className="size-3.5" />
            {beach.sectorCount === 1
              ? "1 setor"
              : `${beach.sectorCount} setores`}
          </span>
          {currentSectorLabel ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-3 py-1 text-xs font-bold text-amber-900">
              <MapPin aria-hidden="true" className="size-3.5" />
              Você está no {sanitizeText(currentSectorLabel, 60)}
            </span>
          ) : null}
        </div>

        <dl className="mt-3 grid grid-cols-2 gap-2 rounded-xl border border-ocean-200 bg-ocean-50 p-3 text-sm">
          <div>
            <dt className="text-ocean-500">Distância</dt>
            <dd className="flex items-center gap-1 font-bold text-ocean-950">
              <Navigation aria-hidden="true" className="size-3.5" />
              {formatDistance(beach.distanceMeters)}
            </dd>
          </div>
          <div>
            <dt className="text-ocean-500">Coordenadas</dt>
            <dd className="font-mono text-xs font-bold text-ocean-950">
              {beach.latitude.toFixed(3)}, {beach.longitude.toFixed(3)}
            </dd>
          </div>
        </dl>

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
