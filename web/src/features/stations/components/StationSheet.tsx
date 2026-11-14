import { useRef } from "react";
import { MapPin, Navigation, Phone, Radio, Shield, X } from "lucide-react";
import { PhotoGallery } from "@/shared/components/PhotoGallery";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useOverlay } from "@/shared/lib/overlay";
import {
  STATION_KIND_LABEL,
  type PublicStation,
} from "@/features/stations/types";

interface StationSheetProps {
  readonly station: PublicStation;
  readonly onClose: () => void;
}

/**
 * Ficha do posto/base aberta pelo pin do mapa.
 *
 * <p>Os campos são só os que o órgão mantém: nome, tipo, praia e posição. A
 * base móvel mostra quando a posição foi conhecida, porque ela é a última
 * posição do plantão — não um rastreamento contínuo (RN17).
 */
export function StationSheet({ station, onClose }: StationSheetProps) {
  const panelRef = useRef<HTMLDialogElement | null>(null);
  useOverlay(true, panelRef, onClose);
  const isMobile = station.kind === "MOBILE";

  return (
    <div className="pointer-events-auto absolute inset-x-2 bottom-2 z-40 md:inset-x-auto md:bottom-4 md:right-4 md:w-96">
      <dialog
        ref={panelRef}
        open
        aria-modal="false"
        aria-label={`Ficha do ${sanitizeText(station.name)}`}
        className="relative m-0 block max-h-[70dvh] w-full overflow-y-auto rounded-2xl border border-ocean-900/10 bg-white/95 p-4 shadow-2xl backdrop-blur"
      >
        <div className="flex items-start justify-between gap-2 border-b border-ocean-100 pb-3">
          <div>
            <span
              className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[10px] font-black uppercase tracking-wider ${
                isMobile
                  ? "border-amber-200 bg-amber-100 text-amber-900"
                  : "border-ocean-200 bg-ocean-100 text-ocean-900"
              }`}
            >
              {isMobile ? (
                <Radio aria-hidden="true" className="size-3" />
              ) : (
                <Shield aria-hidden="true" className="size-3" />
              )}
              {STATION_KIND_LABEL[station.kind]}
            </span>
            <h2 className="mt-1.5 text-base font-black text-ocean-950">
              {sanitizeText(station.name, 120)}
            </h2>
            {station.beachName ? (
              <p className="flex items-center gap-1 text-xs text-ocean-600">
                <MapPin aria-hidden="true" className="size-3" />
                {sanitizeText(station.beachName, 120)}
              </p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar posto"
            className="grid size-9 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-50"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        {isMobile ? (
          <p className="mt-3 rounded-xl border border-ocean-100 bg-ocean-50/70 p-3 text-[11px] leading-snug text-ocean-700">
            A base móvel aparece no mapa enquanto o plantão está aberto.
            {station.positionUpdatedAt
              ? ` Última posição informada em ${formatDateTime(
                  station.positionUpdatedAt,
                )}.`
              : ""}
          </p>
        ) : null}

        <PhotoGallery
          urls={station.photoUrls}
          label={sanitizeText(station.name, 120)}
          openLabel="Ampliar foto do posto"
        />

        <div className="mt-3 grid grid-cols-2 gap-2">
          <a
            href={`https://www.google.com/maps/search/?api=1&query=${station.latitude},${station.longitude}`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50"
          >
            <Navigation aria-hidden="true" className="size-3.5" />
            Como chegar
          </a>
          <a
            href="tel:193"
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900"
          >
            <Phone aria-hidden="true" className="size-3.5" />
            Ligar 193
          </a>
        </div>

        <p className="mt-3 text-[11px] leading-snug text-ocean-600">
          O acionamento oficial é pelo 193. O MarAberto é ferramenta de apoio e
          não substitui o posto de guarda-vidas.
        </p>
      </dialog>
    </div>
  );
}
