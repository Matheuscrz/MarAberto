import { useMemo, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { MapPin, Navigation, RefreshCw, Search } from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { BEACH_KIND_LABEL } from "@/features/beaches";
import type { BeachKind } from "@/features/beaches";
import type { BeachesOutletContext } from "@/features/beaches";
import { formatDistance } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";

const kindFilters: Array<{ value: BeachKind | "ALL"; label: string }> = [
  { value: "ALL", label: "Todos os tipos" },
  { value: "BEACH", label: "Praias" },
  { value: "RIVER", label: "Banho de rio" },
  { value: "DAM", label: "Barragens" },
  { value: "LAGOON", label: "Lagoas" },
  { value: "OTHER", label: "Outras áreas" },
];

export function BeachesPage() {
  const { beaches, status, message, onRetryLocation } =
    useOutletContext<BeachesOutletContext>();
  const [query, setQuery] = useState("");
  const [kind, setKind] = useState<BeachKind | "ALL">("ALL");

  const filtered = useMemo(() => {
    const term = sanitizeText(query, 80).toLocaleLowerCase("pt-BR");
    return beaches.filter((beach) => {
      const matchesKind = kind === "ALL" || beach.kind === kind;
      const matchesTerm =
        term.length === 0 ||
        `${beach.name} ${beach.municipality} ${beach.state}`
          .toLocaleLowerCase("pt-BR")
          .includes(term);
      return matchesKind && matchesTerm;
    });
  }, [beaches, query, kind]);

  return (
    <div className="grid gap-3">
      <PageHeader
        title="Praias"
        subtitle="Áreas de banho mapeadas num raio de até 30 km da sua posição."
      />

      <div className="grid gap-2">
        <label className="relative block">
          <span className="sr-only">Buscar praia ou município</span>
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ocean-500"
          />
          <input
            type="search"
            value={query}
            maxLength={80}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Filtrar praia ou município..."
            className="min-h-10 w-full rounded-xl border border-ocean-200 bg-white pl-9 pr-3 text-sm text-ocean-950 placeholder:text-ocean-400"
          />
        </label>

        <label className="block">
          <span className="sr-only">Filtrar por tipo de área de banho</span>
          <select
            value={kind}
            onChange={(event) => setKind(event.target.value as BeachKind | "ALL")}
            className="min-h-10 w-full rounded-xl border border-ocean-200 bg-white px-3 text-sm font-semibold text-ocean-950"
          >
            {kindFilters.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {status === "locating" ? (
        <Card className="border-dashed bg-ocean-50/40">
          <p className="flex items-center gap-2 text-sm font-semibold text-ocean-800">
            <Navigation aria-hidden="true" className="size-4 text-ocean-500" />
            <output>Obtendo sua localização...</output>
          </p>
        </Card>
      ) : null}

      {status === "error" || status === "empty" ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            {status === "empty"
              ? "Nenhuma praia num raio de 30 km"
              : "Localização necessária"}
          </p>
          <p className="mt-1 text-xs text-ocean-700">{message}</p>
          <button
            type="button"
            onClick={onRetryLocation}
            className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
          >
            <RefreshCw aria-hidden="true" className="size-3.5" />
            Tentar de novo
          </button>
        </Card>
      ) : null}

      {status === "ready" && filtered.length === 0 ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Nenhuma praia encontrada.
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            Ajuste a busca ou o tipo de área.
          </p>
        </Card>
      ) : null}

      {filtered.length > 0 ? (
        <ul className="grid gap-3">
          {filtered.map((beach) => (
            <li key={beach.id}>
              <Link
                to={`/praias/${beach.id}`}
                className="group block rounded-2xl focus-visible:outline-2"
              >
                <Card className="transition group-hover:border-ocean-300 group-hover:shadow-md">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <h2 className="text-base font-black text-ocean-950">
                        {beach.name}
                      </h2>
                      <p className="flex items-center gap-1 text-xs text-ocean-600">
                        <MapPin aria-hidden="true" className="size-3.5" />
                        {[beach.municipality, beach.state]
                          .filter(Boolean)
                          .join(" · ")}
                      </p>
                    </div>
                    <span className="rounded-full bg-ocean-50 px-2.5 py-1 text-[11px] font-bold text-ocean-700">
                      {BEACH_KIND_LABEL[beach.kind]}
                    </span>
                  </div>
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-ocean-600">
                    <span>
                      {beach.sectorCount === 1
                        ? "1 setor mapeado"
                        : `${beach.sectorCount} setores mapeados`}
                    </span>
                    <span className="font-semibold text-ocean-800">
                      {formatDistance(beach.distanceMeters)}
                    </span>
                  </div>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
