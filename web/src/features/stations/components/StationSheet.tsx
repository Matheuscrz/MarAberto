import { useRef } from "react";
import {
  Clock,
  ImageOff,
  MapPin,
  Navigation,
  Phone,
  Radio,
  Shield,
  X,
} from "lucide-react";
import { safeExternalUrl, sanitizeText } from "@/shared/lib/sanitize";
import { useOverlay } from "@/shared/lib/overlay";
import type { LifeguardStation } from "@/features/stations/types";

interface StationSheetProps {
  readonly station: LifeguardStation;
  readonly onClose: () => void;
}

export function StationSheet({ station, onClose }: StationSheetProps) {
  const panelRef = useRef<HTMLDialogElement | null>(null);
  useOverlay(true, panelRef, onClose);

  const photos = (station.photoUrls ?? [])
    .map(safeExternalUrl)
    .filter((url): url is string => url !== null);

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
              className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[10px] font-black uppercase tracking-wider ${
                station.type === "MOBILE"
                  ? "bg-amber-100 text-amber-900 border border-amber-200"
                  : "bg-ocean-100 text-ocean-900 border border-ocean-200"
              }`}
            >
              {station.type === "MOBILE" ? (
                <>
                  <Radio aria-hidden="true" className="size-3" /> Base Móvel
                </>
              ) : (
                <>
                  <Shield aria-hidden="true" className="size-3" /> Posto Fixo
                </>
              )}
            </span>
            <h2 className="mt-1.5 text-base font-black text-ocean-950">
              {sanitizeText(station.name)}
            </h2>
            <p className="flex items-center gap-1 text-xs text-ocean-600">
              <MapPin aria-hidden="true" className="size-3" />
              {station.beachName ? `${sanitizeText(station.beachName)} • ` : ""}
              {sanitizeText(station.municipality)} -{" "}
              {sanitizeText(station.state)}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar ficha do posto"
            className="grid size-9 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-50"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        {/* Fotos do posto: o banhista reconhece o local antes de sair da praia. */}
        <div className="mt-3">
          <p className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
            Fotos do {station.type === "MOBILE" ? "base móvel" : "posto"}
          </p>
          {photos.length > 0 ? (
            <ul className="mt-1.5 flex snap-x gap-2 overflow-x-auto pb-1">
              {photos.map((url, index) => (
                <li key={url} className="shrink-0 snap-start">
                  <img
                    src={url}
                    alt={`Foto ${index + 1} do ${sanitizeText(station.name)}`}
                    loading="lazy"
                    className="h-28 w-40 rounded-xl border border-ocean-200 object-cover"
                  />
                </li>
              ))}
            </ul>
          ) : (
            <p className="mt-1.5 flex items-center gap-1.5 rounded-xl border border-dashed border-ocean-200 bg-ocean-50/60 px-3 py-2 text-[11px] text-ocean-600">
              <ImageOff aria-hidden="true" className="size-3.5" />
              Este posto ainda não tem foto cadastrada.
            </p>
          )}
        </div>

        {station.operatingHours ? (
          <div className="mt-3 flex items-center gap-1.5 text-xs text-ocean-700">
            <Clock aria-hidden="true" className="size-3.5 text-ocean-500" />
            <span>
              Horário de Ativação:{" "}
              <strong>{sanitizeText(station.operatingHours)}</strong>
            </span>
          </div>
        ) : null}

        {station.resources && station.resources.length > 0 ? (
          <div className="mt-3">
            <p className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
              Recursos Disponíveis
            </p>
            <div className="mt-1.5 flex flex-wrap gap-1">
              {station.resources.map((res) => (
                <span
                  key={res}
                  className="rounded-lg border border-ocean-200/80 bg-ocean-50 px-2 py-0.5 text-[11px] font-semibold text-ocean-800"
                >
                  {sanitizeText(res)}
                </span>
              ))}
            </div>
          </div>
        ) : null}

        <div className="mt-4 grid grid-cols-2 gap-2">
          <a
            href={`https://www.google.com/maps/search/?api=1&query=${station.latitude},${station.longitude}`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50"
          >
            <Navigation aria-hidden="true" className="size-3.5" />
            Traçar Rota
          </a>
          <a
            href="tel:193"
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900"
          >
            <Phone aria-hidden="true" className="size-3.5" />
            Ligar 193
          </a>
        </div>
      </dialog>
    </div>
  );
}
