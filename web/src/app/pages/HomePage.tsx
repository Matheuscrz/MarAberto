import { useMemo } from "react";
import { useOutletContext } from "react-router-dom";
import { FlagBadge, RiskBadge } from "@/shared/components/Badges";
import { beaches } from "@/features/beaches";
import { sanitizeText } from "@/shared/lib/sanitize";
import type { HomeOutletContext } from "@/app/layout/RootLayout";

/**
 * Conteúdo do painel quando o usuário está na home: lista das praias
 * mapeadas, filtrável pelo campo de busca do cabeçalho flutuante.
 */
export function HomePage() {
  const { query, onSelectBeach } = useOutletContext<HomeOutletContext>();

  const filteredBeaches = useMemo(() => {
    const term = sanitizeText(query, 80).toLocaleLowerCase("pt-BR");
    if (!term) return beaches;
    return beaches.filter((beach) =>
      `${beach.name} ${beach.municipality} ${beach.state}`
        .toLocaleLowerCase("pt-BR")
        .includes(term),
    );
  }, [query]);

  return (
    <div>
      <h2 className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-ocean-500">
        <span>Praias mapeadas</span>
        <span>{filteredBeaches.length}</span>
      </h2>

      {filteredBeaches.length === 0 ? (
        <div className="mt-2 rounded-xl border border-dashed border-ocean-200 bg-ocean-50/40 p-4 text-center">
          <p className="text-xs font-bold text-ocean-900">
            Nenhuma praia carregada no momento
          </p>
          <p className="mt-0.5 text-[11px] text-ocean-600">
            Os dados reais do KML aparecerão aqui assim que importados.
          </p>
        </div>
      ) : (
        <ul className="mt-2 grid gap-2">
          {filteredBeaches.map((beach) => (
            <li key={beach.id}>
              <button
                type="button"
                onClick={() => onSelectBeach(beach.id)}
                className="w-full rounded-xl border border-ocean-100 bg-white p-3 text-left transition hover:border-ocean-300 hover:bg-ocean-50/40"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-bold text-ocean-950">{beach.name}</p>
                    <p className="text-xs text-ocean-600">
                      {beach.municipality} · {beach.state}
                    </p>
                  </div>
                  <RiskBadge level={beach.riskLevel} />
                </div>
                <div className="mt-2">
                  <FlagBadge flag={beach.flag} />
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
