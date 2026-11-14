import { useMemo, useState } from "react";
import { RefreshCw, Search } from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { sanitizeText } from "@/shared/lib/sanitize";
import { StationList } from "@/features/stations/components/StationList";
import { usePublicStations } from "@/features/stations/hooks/use-public-stations";
import type { StationKind } from "@/features/stations/types";

type KindFilter = StationKind | "ALL";

const FILTERS: readonly { value: KindFilter; label: string }[] = [
  { value: "ALL", label: "Todas as estruturas" },
  { value: "FIXED", label: "Postos fixos" },
  { value: "MOBILE", label: "Bases móveis" },
];

/**
 * Postos e bases visíveis no mapa agora.
 *
 * <p>A lista é a mesma fonte dos pins: posto desativado pelo órgão não aparece e
 * base móvel só entra com plantão vivo. Por isso a tela pode estar vazia numa
 * cidade litorânea — o texto explica em vez de inventar dado.
 */
export function LifeguardsPage() {
  const [query, setQuery] = useState("");
  const [kindFilter, setKindFilter] = useState<KindFilter>("ALL");
  const { stations, status, message, reload } = usePublicStations();

  const filtered = useMemo(() => {
    const term = sanitizeText(query, 80).toLocaleLowerCase("pt-BR");
    return stations.filter((station) => {
      const matchesKind = kindFilter === "ALL" || station.kind === kindFilter;
      const matchesTerm =
        term.length === 0 ||
        `${station.name} ${station.beachName ?? ""}`
          .toLocaleLowerCase("pt-BR")
          .includes(term);
      return matchesKind && matchesTerm;
    });
  }, [stations, query, kindFilter]);

  return (
    <div className="grid gap-3">
      <PageHeader
        title="Postos de Guarda-Vidas"
        subtitle="Postos fixos ativos e bases móveis com plantão em andamento."
      />

      {status === "error" ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Não foi possível carregar os postos
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            {message ?? "Tente de novo em instantes."}
          </p>
          <button
            type="button"
            onClick={reload}
            className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
          >
            <RefreshCw aria-hidden="true" className="size-3.5" />
            Tentar de novo
          </button>
        </Card>
      ) : status === "loading" ? (
        <output className="block py-6 text-center text-sm font-semibold text-ocean-600">
          Carregando postos e bases
        </output>
      ) : (
        <>
          <div className="grid gap-2">
            <label className="relative block">
              <span className="sr-only">Buscar posto ou praia</span>
              <Search
                aria-hidden="true"
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ocean-500"
              />
              <input
                type="search"
                value={query}
                maxLength={80}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Filtrar posto ou praia..."
                className="min-h-10 w-full rounded-xl border border-ocean-200 bg-white pl-9 pr-3 text-sm text-ocean-950 placeholder:text-ocean-400"
              />
            </label>

            <div className="flex flex-wrap gap-1.5">
              {FILTERS.map((filter) => (
                <button
                  key={filter.value}
                  type="button"
                  onClick={() => setKindFilter(filter.value)}
                  aria-pressed={kindFilter === filter.value}
                  className={[
                    "min-h-8 rounded-full px-3 py-1 text-xs font-bold transition",
                    kindFilter === filter.value
                      ? "bg-ocean-950 text-white"
                      : "border border-ocean-200 bg-white text-ocean-800 hover:bg-ocean-50",
                  ].join(" ")}
                >
                  {filter.label}
                </button>
              ))}
            </div>
          </div>

          <StationList
            stations={filtered}
            emptyTitle={
              stations.length === 0
                ? "Nenhum posto ou base em operação agora"
                : "Nenhuma estrutura com esse filtro"
            }
            emptyHint={
              stations.length === 0
                ? "A estrutura aparece aqui quando o órgão ativa o posto ou quando a base móvel abre plantão."
                : "Ajuste a busca ou troque o tipo de estrutura."
            }
          />
        </>
      )}
    </div>
  );
}
