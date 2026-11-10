import { MARKER_SYMBOLS, type SymbolKey } from "@/features/map/lib/marker-symbols";

interface MarkerSymbolProps {
  readonly symbol: SymbolKey;
  readonly className?: string;
}

/**
 * Versão React dos mesmos símbolos usados nos marcadores do MapLibre. Mantém a
 * legenda idêntica ao mapa, sem duplicar os caminhos do SVG.
 */
export function MarkerSymbol({ symbol, className }: MarkerSymbolProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {MARKER_SYMBOLS[symbol].map((primitive, index) => {
        if (primitive.kind === "path") {
          return <path key={index} d={primitive.d} />;
        }
        if (primitive.kind === "circle") {
          return (
            <circle
              key={index}
              cx={primitive.cx}
              cy={primitive.cy}
              r={primitive.r}
            />
          );
        }
        return (
          <line
            key={index}
            x1={primitive.x1}
            y1={primitive.y1}
            x2={primitive.x2}
            y2={primitive.y2}
          />
        );
      })}
    </svg>
  );
}
