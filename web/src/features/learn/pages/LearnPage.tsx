import { useCallback, useEffect, useMemo, useState } from "react";
import {
  BookOpen,
  ExternalLink,
  Megaphone,
  RefreshCw,
  Video,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { fetchPublicContent } from "@/features/learn/api/content";
import { VideoFacade } from "@/features/learn/components/VideoFacade";
import { safeExternalUrl, sanitizeText } from "@/shared/lib/sanitize";
import type {
  ContentKind,
  EducationalContentItem,
} from "@/features/learn/types";

const KIND_FILTERS: readonly {
  readonly value: "ALL" | ContentKind;
  readonly label: string;
}[] = [
  { value: "ALL", label: "Tudo" },
  { value: "VIDEO", label: "Vídeos" },
  { value: "GUIDE", label: "Guias" },
  { value: "CAMPAIGN", label: "Divulgação" },
];

const KIND_BADGE: Record<
  ContentKind,
  { readonly label: string; readonly icon: typeof Video; readonly tone: string }
> = {
  VIDEO: {
    label: "Vídeo educativo",
    icon: Video,
    tone: "bg-ocean-100 text-ocean-800",
  },
  GUIDE: {
    label: "Guia de prevenção",
    icon: BookOpen,
    tone: "bg-emerald-100 text-emerald-800",
  },
  CAMPAIGN: {
    label: "Divulgação",
    icon: Megaphone,
    tone: "bg-amber-100 text-amber-900",
  },
};

export function LearnPage() {
  const [items, setItems] = useState<readonly EducationalContentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedKind, setSelectedKind] = useState<"ALL" | ContentKind>("ALL");
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");

  const loadContent = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchPublicContent();
      setItems(data);
    } catch {
      setError("Não foi possível carregar os conteúdos educativos agora.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    fetchPublicContent()
      .then((data) => {
        if (active) {
          setItems(data);
          setError(null);
        }
      })
      .catch(() => {
        if (active) {
          setError("Não foi possível carregar os conteúdos educativos agora.");
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  const categories = useMemo(() => {
    const unique = new Set<string>();
    items.forEach((item) => {
      if (item.category.trim()) {
        unique.add(item.category.trim());
      }
    });
    return ["ALL", ...Array.from(unique)];
  }, [items]);

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (selectedKind !== "ALL" && item.kind !== selectedKind) {
        return false;
      }
      if (selectedCategory !== "ALL" && item.category !== selectedCategory) {
        return false;
      }
      return true;
    });
  }, [items, selectedKind, selectedCategory]);

  return (
    <div className="grid gap-3.5 pb-6">
      <PageHeader
        title="Central Aprender"
        subtitle="Vídeos educativos, guias de prevenção e materiais de divulgação mantidos pela coordenação."
      />

      <div className="flex flex-wrap items-center justify-between gap-2">
        <nav
          aria-label="Filtrar por tipo de conteúdo"
          className="flex flex-wrap gap-1.5"
        >
          {KIND_FILTERS.map((filter) => (
            <button
              key={filter.value}
              type="button"
              onClick={() => setSelectedKind(filter.value)}
              className={[
                "rounded-full px-3 py-1 text-xs font-bold transition",
                selectedKind === filter.value
                  ? "bg-ocean-950 text-white shadow-sm"
                  : "border border-ocean-200 bg-white text-ocean-700 hover:bg-ocean-50",
              ].join(" ")}
            >
              {filter.label}
            </button>
          ))}
        </nav>

        <button
          type="button"
          onClick={() => void loadContent()}
          disabled={loading}
          className="inline-flex items-center gap-1.5 rounded-lg border border-ocean-200 bg-white px-2.5 py-1 text-xs font-semibold text-ocean-700 hover:bg-ocean-50 disabled:opacity-50"
        >
          <RefreshCw
            aria-hidden="true"
            className={loading ? "size-3.5 animate-spin" : "size-3.5"}
          />
          Atualizar
        </button>
      </div>

      {categories.length > 2 ? (
        <nav
          aria-label="Filtrar por categoria"
          className="flex flex-wrap gap-1.5"
        >
          {categories.map((category) => (
            <button
              key={category}
              type="button"
              onClick={() => setSelectedCategory(category)}
              className={[
                "rounded-md px-2.5 py-1 text-[11px] font-bold transition",
                selectedCategory === category
                  ? "bg-ocean-800 text-white"
                  : "bg-ocean-50 text-ocean-700 hover:bg-ocean-100",
              ].join(" ")}
            >
              {category === "ALL"
                ? "Todas as categorias"
                : sanitizeText(category, 60)}
            </button>
          ))}
        </nav>
      ) : null}

      {error ? (
        <Card className="border-red-200 bg-red-50 p-4 text-sm text-red-900">
          {error}
        </Card>
      ) : null}

      {loading && items.length === 0 ? (
        <Card className="p-6 text-center text-sm text-ocean-700">
          Carregando conteúdos da Central Aprender...
        </Card>
      ) : null}

      {!loading && !error && filteredItems.length === 0 ? (
        <Card className="p-6 text-center text-sm text-ocean-700">
          Nenhum conteúdo publicado encontrado para este filtro.
        </Card>
      ) : null}

      <ul className="grid gap-3">
        {filteredItems.map((item) => (
          <li key={item.id}>
            <ContentCard item={item} />
          </li>
        ))}
      </ul>
    </div>
  );
}

function ContentCard({ item }: { readonly item: EducationalContentItem }) {
  const badge = KIND_BADGE[item.kind];
  const BadgeIcon = badge.icon;
  const safeUrl = item.externalUrl ? safeExternalUrl(item.externalUrl) : null;

  return (
    <Card className="flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span
              className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wide ${badge.tone}`}
            >
              <BadgeIcon aria-hidden="true" className="size-3" />
              {badge.label}
            </span>
            <span className="rounded-md bg-ocean-50 px-2 py-0.5 text-[10px] font-bold text-ocean-700">
              {sanitizeText(item.category, 60)}
            </span>
          </div>
          <h2 className="mt-1.5 text-base font-black text-ocean-950">
            {sanitizeText(item.title, 160)}
          </h2>
        </div>
      </div>

      <p className="whitespace-pre-line text-sm leading-relaxed text-ocean-800">
        {sanitizeText(item.description, 2000)}
      </p>

      {item.youtubeId ? (
        <VideoFacade
          youtubeId={item.youtubeId}
          title={item.title}
          duration={item.duration}
        />
      ) : null}

      {item.tags.length > 0 ? (
        <ul className="flex flex-wrap gap-1.5" aria-label="Tags">
          {item.tags.map((tag) => (
            <li
              key={tag}
              className="rounded-full border border-ocean-200 bg-white px-2.5 py-0.5 text-[11px] font-semibold text-ocean-700"
            >
              #{sanitizeText(tag, 40)}
            </li>
          ))}
        </ul>
      ) : null}

      {safeUrl ? (
        <div>
          <a
            href={safeUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 rounded-lg border border-ocean-200 bg-ocean-50 px-3 py-1.5 text-xs font-bold text-ocean-900 transition hover:bg-ocean-100"
          >
            <ExternalLink aria-hidden="true" className="size-3.5" />
            {item.externalLinkLabel
              ? sanitizeText(item.externalLinkLabel, 80)
              : "Acessar material"}
          </a>
        </div>
      ) : null}
    </Card>
  );
}
