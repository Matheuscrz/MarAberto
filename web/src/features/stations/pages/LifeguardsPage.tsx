import { useMemo, useState } from "react";
import {
  MapPin,
  Navigation,
  Phone,
  Radio,
  Search,
  Shield,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { lifeguardStations } from "@/features/stations/api/demo-stations";
import { sanitizeText } from "@/shared/lib/sanitize";
import type { StationType } from "@/features/stations/types";

export function LifeguardsPage() {
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<StationType | "ALL">("ALL");

  const filtered = useMemo(() => {
    const term = sanitizeText(query, 80).toLocaleLowerCase("pt-BR");
    return lifeguardStations.filter((station) => {
      const matchesType =
        typeFilter === "ALL" || station.type === typeFilter;
      const matchesTerm =
        term.length === 0 ||
        `${station.name} ${station.beachName ?? ""} ${station.municipality}`
          .toLocaleLowerCase("pt-BR")
          .includes(term);
      return matchesType && matchesTerm;
    });
  }, [query, typeFilter]);

  return (
    <div className="grid gap-3">
      <PageHeader
        title="Postos de Guarda-Vidas"
        subtitle="Localização de postos fixos, torres e bases operacionais móveis."
      />

      <div className="grid gap-2">
        <label className="relative block">
          <span className="sr-only">Buscar posto ou município</span>
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ocean-500"
          />
          <input
            type="search"
            value={query}
            maxLength={80}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Filtrar posto, praia ou município..."
            className="min-h-10 w-full rounded-xl border border-ocean-200 bg-white pl-9 pr-3 text-sm text-ocean-950 placeholder:text-ocean-400"
          />
        </label>

        <div className="flex gap-1.5">
          {(
            [
              { value: "ALL", label: "Todos os postos" },
              { value: "FIXED", label: "Postos Fixos" },
              { value: "MOBILE", label: "Bases Móveis" },
            ] as const
          ).map((filter) => (
            <button
              key={filter.value}
              type="button"
              onClick={() => setTypeFilter(filter.value)}
              className={[
                "min-h-8 rounded-full px-3 py-1 text-xs font-bold transition",
                typeFilter === filter.value
                  ? "bg-ocean-950 text-white"
                  : "border border-ocean-200 bg-white text-ocean-800 hover:bg-ocean-50",
              ].join(" ")}
            >
              {filter.label}
            </button>
          ))}
        </div>
      </div>

      {filtered.length === 0 ? (
        <Card className="border-dashed bg-ocean-50/40 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Nenhum posto encontrado.
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            Ajuste os filtros de busca ou aguarde o sincronismo de dados com a central.
          </p>
        </Card>
      ) : (
        <ul className="grid gap-3">
          {filtered.map((station) => (
            <li key={station.id}>
              <Card className="flex flex-col gap-2.5">
                <div className="flex flex-wrap items-start justify-between gap-2">
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
                    <h2 className="mt-1 text-base font-black text-ocean-950">
                      {station.name}
                    </h2>
                    <p className="flex items-center gap-1 text-xs text-ocean-600">
                      <MapPin aria-hidden="true" className="size-3.5" />
                      {station.beachName ? `${station.beachName} • ` : ""}
                      {station.municipality} - {station.state}
                    </p>
                  </div>

                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                      station.status === "ACTIVE"
                        ? "bg-emerald-100 text-emerald-800"
                        : "bg-slate-100 text-slate-700"
                    }`}
                  >
                    {station.status === "ACTIVE" ? "Operando" : "Inativo"}
                  </span>
                </div>

                {station.operatingHours ? (
                  <p className="text-xs text-ocean-700">
                    Horário: <strong>{station.operatingHours}</strong>
                  </p>
                ) : null}

                {station.resources && station.resources.length > 0 ? (
                  <div className="flex flex-wrap gap-1">
                    {station.resources.map((res) => (
                      <span
                        key={res}
                        className="rounded-lg bg-ocean-50 border border-ocean-200/60 px-2 py-0.5 text-[11px] text-ocean-700"
                      >
                        {res}
                      </span>
                    ))}
                  </div>
                ) : null}

                <div className="mt-1 grid grid-cols-2 gap-2 border-t border-ocean-100 pt-2.5">
                  <a
                    href={`https://www.google.com/maps/search/?api=1&query=${station.latitude},${station.longitude}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex min-h-9 items-center justify-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 transition hover:bg-ocean-50"
                  >
                    <Navigation aria-hidden="true" className="size-3.5" />
                    Como chegar
                  </a>
                  <a
                    href="tel:193"
                    className="inline-flex min-h-9 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900"
                  >
                    <Phone aria-hidden="true" className="size-3.5" />
                    Ligar 193
                  </a>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}