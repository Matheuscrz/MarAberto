import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { apiAssetUrl } from "@/shared/lib/api-client";
import { useOverlay } from "@/shared/lib/overlay";
import { sanitizeText } from "@/shared/lib/sanitize";

interface PhotoGalleryProps {
  /** Caminhos devolvidos pelo backend (`/api/v1/media/…`). */
  readonly urls: readonly string[];
  /** Contexto das fotos, usado no texto alternativo e no cabeçalho. */
  readonly label: string;
  /** Miniatura pequena: usada nas listas e fichas compactas. */
  readonly compact?: boolean;
  /** Rótulo do botão de abrir, para leitores de tela. */
  readonly openLabel?: string;
}

/**
 * Galeria de fotos servidas pelo backend (sinalização, posto, base móvel).
 *
 * <p>Na ampliação a imagem aparece inteira (`object-contain`) sobre fundo
 * escuro, com anterior/próxima, contador, miniaturas e as setas do teclado —
 * o mesmo gesto de uma galeria de fotos. Escape fecha e devolve o foco.
 */
export function PhotoGallery({
  urls,
  label,
  compact = false,
  openLabel,
}: PhotoGalleryProps) {
  const [preview, setPreview] = useState<number | null>(null);
  const dialogRef = useRef<HTMLDialogElement | null>(null);

  const close = useCallback(() => setPreview(null), []);
  const total = urls.length;

  const step = useCallback(
    (delta: number) => {
      setPreview((current) => {
        if (current === null || total === 0) return current;
        return (current + delta + total) % total;
      });
    },
    [total],
  );

  useOverlay(preview !== null, dialogRef, close);

  useEffect(() => {
    if (preview === null) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "ArrowRight") {
        event.preventDefault();
        step(1);
      } else if (event.key === "ArrowLeft") {
        event.preventDefault();
        step(-1);
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [preview, step]);

  if (total === 0) {
    return null;
  }

  const safeLabel = sanitizeText(label, 80);
  const current = preview === null ? null : urls[preview] ?? null;
  const position = preview === null ? 0 : preview + 1;

  return (
    <>
      <ul
        className={
          compact
            ? "mt-1.5 flex flex-wrap gap-1.5"
            : "mt-3 grid grid-cols-3 gap-1.5"
        }
        aria-label={`Fotos de ${safeLabel}`}
      >
        {urls.map((url, index) => (
          <li key={`${url}-${index}`}>
            <button
              type="button"
              onClick={() => setPreview(index)}
              className={[
                "block overflow-hidden rounded-lg border border-ocean-200 bg-ocean-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ocean-700",
                compact ? "size-12" : "aspect-square w-full",
              ].join(" ")}
              aria-label={`${openLabel ?? "Ampliar foto"} ${index + 1} de ${total}`}
            >
              <img
                src={apiAssetUrl(url)}
                alt={`Foto ${index + 1} de ${safeLabel}`}
                loading="lazy"
                decoding="async"
                className="size-full object-cover"
              />
            </button>
          </li>
        ))}
      </ul>

      {current ? (
        <dialog
          ref={dialogRef}
          open
          aria-modal="true"
          aria-label={`Galeria de fotos de ${safeLabel}`}
          className="fixed inset-0 z-50 m-0 flex h-dvh max-h-none w-screen max-w-none flex-col bg-ocean-950/95 p-3 text-white backdrop-blur-sm sm:p-4"
        >
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-bold text-white/80">
              {safeLabel} · {position} de {total}
            </p>
            <button
              type="button"
              onClick={close}
              aria-label="Fechar galeria"
              data-autofocus
              className="grid size-9 place-items-center rounded-full text-white/80 transition hover:bg-white/10 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
          </div>

          <div className="relative flex min-h-0 flex-1 items-center justify-center py-2">
            {total > 1 ? (
              <button
                type="button"
                onClick={() => step(-1)}
                aria-label="Foto anterior"
                className="absolute left-0 z-10 grid size-10 place-items-center rounded-full bg-ocean-950/60 text-white/90 transition hover:bg-ocean-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white sm:size-12"
              >
                <ChevronLeft aria-hidden="true" className="size-6" />
              </button>
            ) : null}

            <img
              src={apiAssetUrl(current)}
              alt={`Foto ${position} de ${total} de ${safeLabel}`}
              className="max-h-full max-w-full rounded-lg object-contain"
            />

            {total > 1 ? (
              <button
                type="button"
                onClick={() => step(1)}
                aria-label="Próxima foto"
                className="absolute right-0 z-10 grid size-10 place-items-center rounded-full bg-ocean-950/60 text-white/90 transition hover:bg-ocean-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white sm:size-12"
              >
                <ChevronRight aria-hidden="true" className="size-6" />
              </button>
            ) : null}
          </div>

          {total > 1 ? (
            <ul
              className="flex shrink-0 justify-center gap-1.5 overflow-x-auto py-1"
              aria-label="Miniaturas das fotos"
            >
              {urls.map((url, index) => (
                <li key={`strip-${url}-${index}`}>
                  <button
                    type="button"
                    onClick={() => setPreview(index)}
                    aria-label={`Ver foto ${index + 1} de ${total}`}
                    aria-current={index === preview}
                    className={[
                      "block size-12 overflow-hidden rounded-md border-2 transition",
                      index === preview
                        ? "border-white"
                        : "border-transparent opacity-60 hover:opacity-100",
                    ].join(" ")}
                  >
                    <img
                      src={apiAssetUrl(url)}
                      alt=""
                      aria-hidden="true"
                      loading="lazy"
                      decoding="async"
                      className="size-full object-cover"
                    />
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </dialog>
      ) : null}
    </>
  );
}
