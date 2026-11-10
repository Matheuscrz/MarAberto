import { MarkerSymbol } from "@/features/map/components/MarkerSymbol";

interface MapLegendProps {
  readonly className?: string;
}

/**
 * Legenda fixa no canto inferior esquerdo, abaixo do aviso do 193 (RF: o
 * banhista identifica o marcador sem precisar abrir a ficha). Usa os mesmos
 * símbolos e cores do mapa. Praia não entra aqui: não há marcador de praia.
 */
export function MapLegend({ className = "" }: MapLegendProps) {
  return (
    <aside
      aria-label="Legenda dos marcadores do mapa"
      className={`rounded-xl border border-ocean-900/10 bg-white/95 px-2.5 py-2 shadow-md backdrop-blur ${className}`}
    >
      <ul className="grid grid-cols-2 gap-x-3 gap-y-1.5 text-[11px] font-semibold text-ocean-900 sm:grid-cols-1">
        <li className="flex items-center gap-1.5">
          <span className="grid size-5 shrink-0 place-items-center rounded-full border-2 border-white bg-ocean-600 text-white shadow-sm">
            <MarkerSymbol symbol="STATION_FIXED" className="size-3" />
          </span>
          <span>Posto fixo</span>
        </li>
        <li className="flex items-center gap-1.5">
          <span className="grid size-5 shrink-0 place-items-center rounded-lg border-2 border-white bg-sand-500 text-ocean-950 shadow-sm">
            <MarkerSymbol symbol="STATION_MOBILE" className="size-3" />
          </span>
          <span>Base móvel</span>
        </li>
        <li className="flex items-center gap-1.5">
          <span className="grid size-5 shrink-0 place-items-center rounded-[50%_50%_50%_4px] border-2 border-white bg-coral-500 text-white shadow-sm">
            <MarkerSymbol symbol="SIGNAL" className="size-3" />
          </span>
          <span>
            Sinalização <span className="text-ocean-500">(cor da bandeira)</span>
          </span>
        </li>
      </ul>
    </aside>
  );
}
