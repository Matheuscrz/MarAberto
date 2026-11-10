import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { MapPin, Search } from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { FlagBadge, RiskBadge } from "@/shared/components/Badges";
import { beaches } from "@/features/beaches/api/demo-beaches";
import type { RiskLevel } from "@/shared/types/flags";
import { sanitizeText } from "@/shared/lib/sanitize";
import { formatOccupancy } from "@/shared/lib/format";

const riskFilters: Array<{ value: RiskLevel | "ALL"; label: string }> = [
  { value: "ALL", label: "Todos os riscos" },
  { value: "LOW", label: "Risco baixo" },
  { value: "MODERATE", label: "Risco moderado" },
  { value: "HIGH", label: "Risco alto" },
  { value: "EXTREME", label: "Risco extremo" },
];

export function BeachesPage() {
  const [query, setQuery] = useState("");
  const [risk, setRisk] = useState<RiskLevel | "ALL">("ALL");

  const filtered = useMemo(() => {
    const term = sanitizeText(query, 80).toLocaleLowerCase("pt-BR");
    return beaches.filter((beach) => {
      const matchesRisk = risk === "ALL" || beach.riskLevel === risk;
      const matchesTerm =
        term.length === 0 ||
        `${beach.name} ${beach.municipality} ${beach.state}`
          .toLocaleLowerCase("pt-BR")
          .includes(term);
      return matchesRisk && matchesTerm;
    });
  }, [query, risk]);

  return (
    <div className="grid gap-3">
      <PageHeader
        title="Praias"
        subtitle="Consulte bandeira, risco e ocupação estimada antes de entrar no mar."
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
          <span className="sr-only">Filtrar por nível de risco</span>
          <select
            value={risk}
            onChange={(event) =>
              setRisk(event.target.value as RiskLevel | "ALL")
            }
            className="min-h-10 w-full rounded-xl border border-ocean-200 bg-white px-3 text-sm font-semibold text-ocean-950"
          >
            {riskFilters.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {filtered.length === 0 ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Nenhuma praia encontrada.
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            Ajuste o filtro ou aguarde a importação do mapeamento KML das
            praias.
          </p>
        </Card>
      ) : (
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
                        {beach.municipality} · {beach.state}
                      </p>
                    </div>
                    <RiskBadge level={beach.riskLevel} />
                  </div>
                  <p className="mt-2 line-clamp-2 text-xs text-ocean-700">
                    {beach.description}
                  </p>
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
                    <FlagBadge flag={beach.flag} />
                    <span className="text-xs text-ocean-600">
                      Ocupação:{" "}
                      <strong>{formatOccupancy(beach.occupancyPercent)}</strong>
                    </span>
                  </div>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
