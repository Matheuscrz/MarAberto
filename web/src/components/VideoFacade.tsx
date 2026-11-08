import { useState } from 'react';
import { Play } from 'lucide-react';
import type { EducationalVideo } from '../types';
import { isValidYoutubeId } from '../lib/sanitize';

export function VideoFacade({ video }: { video: EducationalVideo }) {
  const [active, setActive] = useState(false);
  const validId = isValidYoutubeId(video.id);

  if (!validId) {
    return (
      <article className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-950">
        Vídeo indisponível: identificador inválido.
      </article>
    );
  }

  return (
    <article className="overflow-hidden rounded-2xl border border-ocean-900/10 bg-white shadow-sm">
      {active ? (
        <iframe
          className="aspect-video w-full"
          src={`https://www.youtube-nocookie.com/embed/${video.id}?autoplay=1&rel=0`}
          title={video.title}
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          referrerPolicy="strict-origin-when-cross-origin"
          allowFullScreen
          loading="lazy"
        />
      ) : (
        <button
          type="button"
          onClick={() => setActive(true)}
          aria-label={`Assistir: ${video.title}`}
          className="group flex aspect-video w-full flex-col items-center justify-center gap-3 bg-gradient-to-br from-ocean-900 via-ocean-800 to-ocean-600 p-6 text-center text-white"
        >
          <span className="grid size-16 place-items-center rounded-full bg-white/15 transition group-hover:bg-white/25">
            <Play aria-hidden="true" className="size-7 fill-current" />
          </span>
          <span className="text-sm font-semibold">Carregar vídeo do YouTube (privacidade)</span>
          <span className="text-xs text-ocean-100">
            O player só é carregado após o toque, em youtube-nocookie.com.
          </span>
        </button>
      )}
      <div className="p-4">
        <div className="flex items-center justify-between gap-2 text-xs font-bold uppercase tracking-wide text-ocean-500">
          <span>{video.topic}</span>
          <span>{video.duration}</span>
        </div>
        <h3 className="mt-2 text-lg font-bold text-ocean-950">{video.title}</h3>
        <p className="mt-1 text-sm text-ocean-700">{video.description}</p>
      </div>
    </article>
  );
}
