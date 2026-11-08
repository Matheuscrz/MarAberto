import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Search } from 'lucide-react';
import { Card, PageHeader } from '../components/Card';
import { DemoNotice } from '../components/DemoNotice';
import { FlagBadge, RiskBadge } from '../components/Badges';
import { beaches } from '../data/demo-data';
import type { RiskLevel } from '../types';
import { sanitizeText } from '../lib/sanitize';
import { formatOccupancy } from '../lib/format';
import { useAppStore } from '../store/app-store';

const riskFilters: Array<{ value: RiskLevel | 'ALL'; label: string }> = [
  { value: 'ALL', label: 'Todos os riscos' },
  { value: 'LOW', label: 'Baixo' },
  { value: 'MODERATE', label: 'Moderado' },
  { value: 'HIGH', label: 'Alto' },
  { value: 'EXTREME', label: 'Extremo' },
];

export function BeachesPage() {
  const [query, setQuery] = useState('');
  const [risk, setRisk] = useState<RiskLevel | 'ALL'>('ALL');
  const selectedBeachId = useAppStore((state) => state.selectedBeachId);
  const setSelectedBeach = useAppStore((state) => state.setSelectedBeach);

  const filtered = useMemo(() => {
    const term = sanitizeText(query, 80).toLocaleLowerCase('pt-BR');
    return beaches.filter((beach) => {
      const matchesRisk = risk === 'ALL' || beach.riskLevel === risk;
      const matchesTerm =
        term.length === 0 ||
        `${beach.name} ${beach.municipality} ${beach.state}`
          .toLocaleLowerCase('pt-BR')
          .includes(term);
      return matchesRisk && matchesTerm;
    });
  }, [query, risk]);

  return (
    <div>
      <PageHeader
        title="Praias"
        subtitle="Consulte bandeira, risco e ocupação estimada antes de entrar no mar."
      />

      <DemoNotice />

      <div className="mb-6 grid gap-3 sm:grid-cols-[1fr_auto]">
        <label className="relative block">
          <span className="sr-only">Buscar praia ou município</span>
          <Search
            aria-hidden="true"
            className="pointer-events-none absolute left-3 top-1/2 size-5 -translate-y-1/2 text-ocean-500"
          />
          <input
            type="search"
            value={query}
            maxLength={80}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar praia ou município"
            className="min-h-12 w-full rounded-xl border border-ocean-200 bg-white pl-11 pr-4 text-ocean-950 placeholder:text-ocean-400"
          />
        </label>

        <label className="block">
          <span className="sr-only">Filtrar por nível de risco</span>
          <select
            value={risk}
            onChange={(event) => setRisk(event.target.value as RiskLevel | 'ALL')}
            className="min-h-12 w-full rounded-xl border border-ocean-200 bg-white px-3 font-semibold text-ocean-950 sm:w-56"
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
        <Card>
          <p className="font-bold text-ocean-950">Nenhuma praia encontrada.</p>
          <p className="mt-1 text-sm text-ocean-700">
            Ajuste a busca ou o filtro de risco. Se a praia não aparece, ela ainda não está no
            cadastro desta instalação.
          </p>
        </Card>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2">
          {filtered.map((beach) => (
            <li key={beach.id}>
              <Link
                to={`/praias/${beach.id}`}
                onClick={() => setSelectedBeach(beach.id)}
                className="group block rounded-2xl focus-visible:outline-2"
              >
                <Card
                  className={`h-full transition group-hover:-translate-y-0.5 group-hover:shadow-md ${
                    selectedBeachId === beach.id ? 'ring-2 ring-ocean-500' : ''
                  }`}
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <h2 className="text-xl font-black text-ocean-950">{beach.name}</h2>
                      <p className="flex items-center gap-1 text-sm text-ocean-600">
                        <MapPin aria-hidden="true" className="size-4" />
                        {beach.municipality} — {beach.state}
                      </p>
                    </div>
                    <RiskBadge level={beach.riskLevel} />
                  </div>

                  <p className="mt-3 line-clamp-2 text-sm text-ocean-700">{beach.description}</p>

                  <div className="mt-4 flex flex-wrap items-center gap-3">
                    <FlagBadge flag={beach.flag} />
                    <span className="text-sm text-ocean-600">
                      Ocupação estimada: <strong>{formatOccupancy(beach.occupancyPercent)}</strong>
                    </span>
                  </div>

                  <p className="mt-3 text-sm font-semibold text-ocean-700">
                    {beach.risks.length === 1
                      ? '1 risco mapeado'
                      : `${beach.risks.length} riscos mapeados`}
                  </p>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
