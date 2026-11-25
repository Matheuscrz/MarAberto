import { useState } from "react";
import { Play } from "lucide-react";
import { isValidYoutubeId, sanitizeText } from "@/shared/lib/sanitize";

interface VideoFacadeProps {
  readonly youtubeId: string;
  readonly title: string;
  readonly duration?: string | null;
}

/**
 * Facade de privacidade para vídeos do YouTube (RNF02).
 *
 * <p>Exibe thumbnail estática sem carregar scripts ou cookies de terceiros.
 * O `<iframe>` em `youtube-nocookie.com` só é montado no DOM após o toque do
 * usuário na thumbnail.
 */
export function VideoFacade({ youtubeId, title, duration }: VideoFacadeProps) {
  const [active, setActive] = useState(false);

  if (!isValidYoutubeId(youtubeId)) {
    return (
      <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-900">
        Vídeo indisponível: identificador inválido.
      </div>
    );
  }

  const cleanTitle = sanitizeText(title, 160);
  const thumbnailUrl = `https://i.ytimg.com/vi/${youtubeId}/hqdefault.jpg`;

  if (active) {
    return (
      <div className="overflow-hidden rounded-xl border border-ocean-200 bg-black">
        <iframe
          className="aspect-video w-full"
          src={`https://www.youtube-nocookie.com/embed/${youtubeId}?autoplay=1&rel=0`}
          title={cleanTitle}
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          referrerPolicy="strict-origin-when-cross-origin"
          allowFullScreen
        />
        <div className="flex items-center justify-between bg-ocean-950 px-3 py-1.5 text-[11px] text-white">
          <span className="truncate">{cleanTitle}</span>
          {duration ? (
            <span className="shrink-0 text-ocean-200">
              {sanitizeText(duration, 16)}
            </span>
          ) : null}
        </div>
      </div>
    );
  }

  return (
    <button
      type="button"
      onClick={() => setActive(true)}
      aria-label={`Assistir vídeo: ${cleanTitle}`}
      className="group relative block aspect-video w-full overflow-hidden rounded-xl border border-ocean-200 bg-ocean-950 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ocean-700"
    >
      <img
        src={thumbnailUrl}
        alt={`Thumbnail do vídeo ${cleanTitle}`}
        loading="lazy"
        className="size-full object-cover opacity-85 transition duration-300 group-hover:scale-[1.02] group-hover:opacity-100"
      />
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-gradient-to-t from-ocean-950/85 via-ocean-950/35 to-transparent p-4 text-center text-white">
        <span className="grid size-12 place-items-center rounded-full bg-white/90 text-ocean-950 shadow-md transition group-hover:scale-105 group-hover:bg-white">
          <Play aria-hidden="true" className="ml-0.5 size-5 fill-current" />
        </span>
        <span className="rounded-full bg-ocean-950/80 px-2.5 py-0.5 text-[11px] font-semibold text-ocean-50">
          Toque para carregar (youtube-nocookie.com)
        </span>
      </div>
      {duration ? (
        <span className="absolute right-2 bottom-2 rounded bg-ocean-950/85 px-1.5 py-0.5 text-[11px] font-bold text-white">
          {sanitizeText(duration, 16)}
        </span>
      ) : null}
    </button>
  );
}
