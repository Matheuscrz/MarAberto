/**
 * Símbolos vetoriais dos marcadores. A mesma definição alimenta o DOM criado
 * pelo MapLibre e a legenda em React, para o banhista ver o mesmo ícone nos
 * dois lugares.
 */
export type SymbolPrimitive =
  | { readonly kind: "path"; readonly d: string }
  | {
      readonly kind: "circle";
      readonly cx: number;
      readonly cy: number;
      readonly r: number;
    }
  | {
      readonly kind: "line";
      readonly x1: number;
      readonly y1: number;
      readonly x2: number;
      readonly y2: number;
    };

export type SymbolKey = "STATION_FIXED" | "STATION_MOBILE" | "SIGNAL" | "CAMERA";

export const MARKER_SYMBOLS: Record<SymbolKey, readonly SymbolPrimitive[]> = {
  /** Boia de salvamento: identifica posto fixo de guarda-vidas. */
  STATION_FIXED: [
    { kind: "circle", cx: 12, cy: 12, r: 9 },
    { kind: "circle", cx: 12, cy: 12, r: 4 },
    { kind: "line", x1: 5.6, y1: 5.6, x2: 8.5, y2: 8.5 },
    { kind: "line", x1: 18.4, y1: 5.6, x2: 15.5, y2: 8.5 },
    { kind: "line", x1: 18.4, y1: 18.4, x2: 15.5, y2: 15.5 },
    { kind: "line", x1: 5.6, y1: 18.4, x2: 8.5, y2: 15.5 },
  ],
  /** Veículo: identifica base móvel / quadriciclo. */
  STATION_MOBILE: [
    { kind: "path", d: "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2" },
    { kind: "path", d: "M15 18H9" },
    {
      kind: "path",
      d: "M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.62l-3.48-4.35A1 1 0 0 0 17.52 8H14",
    },
    { kind: "circle", cx: 17, cy: 18, r: 2 },
    { kind: "circle", cx: 7, cy: 18, r: 2 },
  ],
  /** Bandeira: identifica a sinalização/bandeiramento, colorida pela bandeira. */
  SIGNAL: [
    {
      kind: "path",
      d: "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z",
    },
    { kind: "line", x1: 4, y1: 22, x2: 4, y2: 15 },
  ],
  /** Câmera: selo de que a sinalização tem registro fotográfico. */
  CAMERA: [
    {
      kind: "path",
      d: "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
    },
    { kind: "circle", cx: 12, cy: 13, r: 3 },
  ],
};
