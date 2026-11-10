import { useMemo, useRef, useState } from "react";
import { Camera, Clock, ImageOff, MapPin, ShieldCheck, X } from "lucide-react";
import { FlagBadge } from "@/shared/components/Badges";
import { formatDateTime } from "@/shared/lib/format";
import { safeExternalUrl, sanitizeText } from "@/shared/lib/sanitize";
import { useOverlay } from "@/shared/lib/overlay";
import type { BeachFlagSignal } from "@/features/signals/types";

interface SignalSheetProps {
  readonly signal: BeachFlagSignal;
  readonly onClose: () => void;
}

export function SignalSheet({ signal, onClose }: SignalSheetProps) {
  const panelRef = useRef<HTMLDialogElement | null>(null);
  useOverlay(true, panelRef, onClose);

  const photos = useMemo(
    () =>
      signal.photoUrls
        .map(safeExternalUrl)
        .filter((url): url is string => url !== null),
    [signal.photoUrls],
  );
  const [activePhoto, setActivePhoto] = useState(0);
  const currentPhoto = photos[activePhoto] ?? photos[0];

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
            <div className="flex items-center gap-2">
              <FlagBadge flag={signal.flag} />
            </div>
            <h2 className="mt-1.5 text-base font-black text-ocean-950">
              {sanitizeText(signal.beachName)}
            </h2>
            <p className="flex items-center gap-1 text-xs text-ocean-600">
              <MapPin aria-hidden="true" className="size-3" />
              Lat: {signal.latitude.toFixed(4)}, Lon:{" "}
              {signal.longitude.toFixed(4)}
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

        {/* Galeria: uma sinalização pode ter mais de uma foto do local. */}
        <div className="mt-3">
          {currentPhoto ? (
            <div className="overflow-hidden rounded-xl border border-ocean-200 bg-ocean-50">
              <div className="relative aspect-video w-full bg-ocean-100">
                <img
                  src={currentPhoto}
                  alt={`Foto ${activePhoto + 1} da sinalização em ${sanitizeText(
                    signal.beachName,
                  )}`}
                  className="h-full w-full object-cover"
                  loading="lazy"
                />
                <span className="absolute bottom-2 left-2 inline-flex items-center gap-1 rounded-md bg-black/65 px-2 py-0.5 text-[10px] font-semibold text-white backdrop-blur-sm">
                  <Camera aria-hidden="true" className="size-3" />
                  Foto {activePhoto + 1} de {photos.length}
                </span>
              </div>

              {photos.length > 1 ? (
                <ul
                  aria-label="Miniaturas da sinalização"
                  className="flex gap-1.5 overflow-x-auto border-t border-ocean-200 bg-white p-1.5"
                >
                  {photos.map((url, index) => (
                    <li key={url}>
                      <button
                        type="button"
                        onClick={() => setActivePhoto(index)}
                        aria-label={`Ver foto ${index + 1}`}
                        aria-pressed={index === activePhoto}
                        className={`block h-12 w-16 overflow-hidden rounded-md border-2 transition ${
                          index === activePhoto
                            ? "border-ocean-700"
                            : "border-transparent opacity-70 hover:opacity-100"
                        }`}
                      >
                        <img
                          src={url}
                          alt=""
                          loading="lazy"
                          className="h-full w-full object-cover"
                        />
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : (
            <p className="flex items-center gap-1.5 rounded-xl border border-dashed border-ocean-200 bg-ocean-50/60 px-3 py-2 text-[11px] text-ocean-600">
              <ImageOff aria-hidden="true" className="size-3.5" />
              Esta sinalização não tem foto anexada.
            </p>
          )}
        </div>

        {/* Informações de Autenticidade e Guarda-Vidas */}
        <div className="mt-3 rounded-xl border border-ocean-100 bg-ocean-50/70 p-3">
          <div className="flex items-center gap-1.5 text-xs font-bold text-ocean-900">
            <ShieldCheck
              aria-hidden="true"
              className="size-4 text-emerald-600"
            />
            <span>
              Sinalização confirmada por: {sanitizeText(signal.lifeguardName)}
            </span>
          </div>
          {signal.lifeguardId ? (
            <p className="mt-0.5 text-[11px] text-ocean-600">
              Matrícula / ID: {sanitizeText(signal.lifeguardId)}
            </p>
          ) : null}
          <p className="mt-1 flex items-center gap-1 text-[11px] text-ocean-500">
            <Clock aria-hidden="true" className="size-3" />
            Atualizado em: {formatDateTime(signal.createdAt)}
          </p>
        </div>

        {signal.notes ? (
          <p className="mt-3 text-xs leading-relaxed text-ocean-700">
            <strong>Observações:</strong> {sanitizeText(signal.notes, 300)}
          </p>
        ) : null}
      </dialog>
    </div>
  );
}
