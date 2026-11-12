import { useMemo } from "react";
import { useOutletContext } from "react-router-dom";
import { MapPin, Navigation, RefreshCw } from "lucide-react";
import { BEACH_KIND_LABEL } from "@/features/beaches";
import { formatDistance } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import type { HomeOutletContext } from "@/app/layout/RootLayout";

/**
 * Conteúdo do painel quando o usuário está na home: praias importadas do
 * mapeamento num raio de até 30 km, filtráveis pelo campo de busca.
 */
export function HomePage() {
  const {
    query,
    beaches,
    status,
    message,
    onRetryLocation,
    onSelectBeach,
  } = useOutletContext<HomeOutletContext>();

  const filteredBeaches = useMemo(() => {
    const term = sanitizeText(query, 80).toLocaleLowerCase("pt-BR");
    if (!term) return beaches;
    return beaches.filter((beach) =>
      `${beach.name} ${beach.municipality} ${beach.state}`
        .toLocaleLowerCase("pt-BR")
        .includes(term),
    );
  }, [query, beaches]);

  if (status === "locating") {
    return (
      <div className="mt-2 flex items-center gap-2 rounded-xl border border-ocean-100 bg-ocean-50/60 p-4">
        <Navigation aria-hidden="true" className="size-4 text-ocean-500" />
        <output className="text-xs font-semibold text-ocean-800">
          Obtendo sua localização para listar as praias num raio de 30 km...
        </output>
      </div>
    );
  }

  if (status === "error" || status === "empty") {
    return (
      <div className="mt-2 rounded-xl border border-dashed border-ocean-200 bg-ocean-50/40 p-4 text-center">
        <p className="text-xs font-bold text-ocean-900">
          {status === "empty"
            ? "Nenhuma praia por perto"
            : "Não foi possível localizar você"}
        </p>
        <p className="mt-0.5 text-[11px] text-ocean-600">{message}</p>
        <button
          type="button"
          onClick={onRetryLocation}
          className="mt-2 inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-ocean-300 bg-white px-3 text-[11px] font-bold text-ocean-900 hover:bg-ocean-50"
        >
          <RefreshCw aria-hidden="true" className="size-3.5" />
          Tentar de novo
        </button>
      </div>
    );
  }

  return (
    <div>
      <h2 className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-ocean-500">
        <span>Praias num raio de 30 km</span>
        <span>{filteredBeaches.length}</span>
      </h2>

      {filteredBeaches.length === 0 ? (
        <div className="mt-2 rounded-xl border border-dashed border-ocean-200 bg-ocean-50/40 p-4 text-center">
          <p className="text-xs font-bold text-ocean-900">
            Nenhuma praia encontrada
          </p>
          <p className="mt-0.5 text-[11px] text-ocean-600">
            Ajuste a busca ou amplie a área de cobertura.
          </p>
        </div>
      ) : (
        <ul className="mt-2 grid gap-2">
          {filteredBeaches.map((beach) => (
            <li key={beach.id}>
              <button
                type="button"
                onClick={() => onSelectBeach(beach)}
                className="w-full rounded-xl border border-ocean-100 bg-white p-3 text-left transition hover:border-ocean-300 hover:bg-ocean-50/40"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-bold text-ocean-950">{beach.name}</p>
                    <p className="text-xs text-ocean-600">
                      {[
                        BEACH_KIND_LABEL[beach.kind],
                        beach.municipality,
                        beach.state,
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </p>
                  </div>
                  <span className="shrink-0 rounded-full bg-ocean-50 px-2 py-0.5 text-[11px] font-bold text-ocean-700">
                    {formatDistance(beach.distanceMeters)}
                  </span>
                </div>
                <p className="mt-2 flex items-center gap-1 text-[11px] text-ocean-600">
                  <MapPin aria-hidden="true" className="size-3" />
                  {beach.sectorCount === 1
                    ? "1 setor mapeado"
                    : `${beach.sectorCount} setores mapeados`}
                </p>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
