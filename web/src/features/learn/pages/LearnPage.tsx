import { useMemo, useState } from "react";
import {
  AlertTriangle,
  Eye,
  Play,
  ShieldCheck,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { demoRiskGuides } from "@/features/learn/api/demo-guides";
import { isValidYoutubeId, sanitizeText } from "@/shared/lib/sanitize";
import type { RiskGuide } from "@/features/learn/types";

export function LearnPage() {
  const [selectedCategory, setSelectedCategory] = useState<string>("ALL");
  const [activeVideoId, setActiveVideoId] = useState<string | null>(null);

  // Lista dinâmica de categorias para os filtros
  const categories = useMemo(() => {
    const unique = new Map<string, string>();
    unique.set("ALL", "Todos os perigos");
    demoRiskGuides.forEach((guide) => {
      unique.set(guide.category, guide.categoryLabel);
    });
    return Array.from(unique.entries()).map(([value, label]) => ({
      value,
      label,
    }));
  }, []);

  const filteredGuides = useMemo(() => {
    if (selectedCategory === "ALL") return demoRiskGuides;
    return demoRiskGuides.filter(
      (guide) => guide.category === selectedCategory,
    );
  }, [selectedCategory]);

  return (
    <div className="grid gap-3.5 pb-6">
      <PageHeader
        title="Prevenção & Riscos"
        subtitle="Orientações dos guarda-vidas para identificar ameaças e agir em emergências."
      />

      {/* Navegação horizontal de categorias (Chips) */}
      <nav
        aria-label="Filtrar por tipo de perigo"
        className="flex flex-wrap gap-1.5"
      >
        {categories.map((cat) => (
          <button
            key={cat.value}
            type="button"
            onClick={() => setSelectedCategory(cat.value)}
            className={[
              "rounded-full px-3 py-1 text-xs font-bold transition",
              selectedCategory === cat.value
                ? "bg-ocean-950 text-white shadow-sm"
                : "border border-ocean-200 bg-white text-ocean-700 hover:bg-ocean-50",
            ].join(" ")}
          >
            {cat.label}
          </button>
        ))}
      </nav>

      {/* Listagem de Guias com Abordagem Compacta */}
      <ul className="grid gap-3">
        {filteredGuides.map((guide) => (
          <li key={guide.id}>
            <RiskGuideCard
              guide={guide}
              isVideoActive={activeVideoId === guide.id}
              onToggleVideo={() =>
                setActiveVideoId((curr) =>
                  curr === guide.id ? null : guide.id,
                )
              }
            />
          </li>
        ))}
      </ul>
    </div>
  );
}

interface RiskGuideCardProps {
  readonly guide: RiskGuide;
  readonly isVideoActive: boolean;
  readonly onToggleVideo: () => void;
}

function RiskGuideCard({
  guide,
  isVideoActive,
  onToggleVideo,
}: RiskGuideCardProps) {
  const [expandedSection, setExpandedSection] = useState<
    "howToIdentify" | "prevention" | "whatToDo" | null
  >("whatToDo");

  const hasValidVideo = guide.video && isValidYoutubeId(guide.video.youtubeId);

  return (
    <Card className="flex flex-col gap-3 p-3.5">
      <div className="flex flex-wrap items-start justify-between gap-1.5">
        <div>
          <span className="rounded-md bg-ocean-100 px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wide text-ocean-800">
            {guide.categoryLabel}
          </span>
          <h2 className="mt-1 text-sm font-black text-ocean-950">
            {guide.title}
          </h2>
        </div>
        {hasValidVideo ? (
          <button
            type="button"
            onClick={onToggleVideo}
            className={[
              "inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-bold transition",
              isVideoActive
                ? "border-coral-300 bg-coral-50 text-coral-800"
                : "border-ocean-200 bg-white text-ocean-800 hover:bg-ocean-50",
            ].join(" ")}
          >
            <Play aria-hidden="true" className="size-3.5 fill-current" />
            {isVideoActive ? "Fechar Vídeo" : "Assistir"}
          </button>
        ) : null}
      </div>

      <p className="text-xs text-ocean-700 leading-relaxed">
        {sanitizeText(guide.description, 300)}
      </p>

      {/* Miniplayer YouTube carregado sob demanda */}
      {hasValidVideo && isVideoActive ? (
        <div className="overflow-hidden rounded-xl border border-ocean-200 bg-black">
          <iframe
            className="aspect-video w-full"
            src={`https://www.youtube-nocookie.com/embed/${guide.video?.youtubeId}?autoplay=1&rel=0`}
            title={guide.video?.title ?? guide.title}
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            referrerPolicy="strict-origin-when-cross-origin"
            allowFullScreen
          />
          <div className="flex items-center justify-between bg-ocean-900/90 px-3 py-1.5 text-[11px] text-white">
            <span className="truncate">{guide.video?.title}</span>
            <span className="text-ocean-300">{guide.video?.duration}</span>
          </div>
        </div>
      ) : null}

      {/* Seções em Tabs/Acordeão para não poluir o card */}
      <div className="grid gap-1.5 pt-1">
        <AccordionTab
          icon={AlertTriangle}
          label="O que fazer se for atingido"
          isOpen={expandedSection === "whatToDo"}
          badgeVariant="coral"
          onToggle={() =>
            setExpandedSection((curr) =>
              curr === "whatToDo" ? null : "whatToDo",
            )
          }
          items={guide.whatToDoIfAffected}
        />
        <AccordionTab
          icon={Eye}
          label="Como identificar"
          isOpen={expandedSection === "howToIdentify"}
          badgeVariant="ocean"
          onToggle={() =>
            setExpandedSection((curr) =>
              curr === "howToIdentify" ? null : "howToIdentify",
            )
          }
          items={guide.howToIdentify}
        />
        <AccordionTab
          icon={ShieldCheck}
          label="Prevenção"
          isOpen={expandedSection === "prevention"}
          badgeVariant="emerald"
          onToggle={() =>
            setExpandedSection((curr) =>
              curr === "prevention" ? null : "prevention",
            )
          }
          items={guide.prevention}
        />
      </div>
    </Card>
  );
}

interface AccordionTabProps {
  readonly icon: React.ElementType;
  readonly label: string;
  readonly isOpen: boolean;
  readonly badgeVariant: "coral" | "ocean" | "emerald";
  readonly onToggle: () => void;
  readonly items: readonly string[];
}

function AccordionTab({
  icon: Icon,
  label,
  isOpen,
  badgeVariant,
  onToggle,
  items,
}: AccordionTabProps) {
  const colorMap = {
    coral: "text-coral-600 bg-coral-50 hover:bg-coral-100/70 border-coral-200",
    ocean: "text-ocean-700 bg-ocean-50 hover:bg-ocean-100/70 border-ocean-200",
    emerald:
      "text-emerald-700 bg-emerald-50 hover:bg-emerald-100/70 border-emerald-200",
  };

  return (
    <div className="overflow-hidden rounded-xl border border-ocean-100">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={isOpen}
        className={`flex w-full items-center justify-between px-3 py-2 text-left text-xs font-bold transition ${colorMap[badgeVariant]}`}
      >
        <span className="flex items-center gap-2">
          <Icon aria-hidden="true" className="size-3.5 shrink-0" />
          {label}
        </span>
        <span className="text-[10px] text-ocean-600">
          {isOpen ? "Ocultar" : `Ver (${items.length})`}
        </span>
      </button>

      {isOpen ? (
        <ul className="grid gap-1.5 bg-white p-3 text-xs text-ocean-800">
          {items.map((item, idx) => (
            <li key={idx} className="flex items-start gap-2">
              <span className="mt-1 size-1.5 shrink-0 rounded-full bg-ocean-400" />
              <span>{sanitizeText(item, 240)}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
