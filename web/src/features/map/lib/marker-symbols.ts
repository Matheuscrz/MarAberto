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

export type SymbolKey =
  | "STATION_FIXED"
  | "STATION_MOBILE"
  | "SIGNAL"
  | "UNMAPPED_SIGNAL"
  | "LIFEGUARD"
  | "OCCURRENCE"
  | "CAMERA";

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
  /**
   * Ponto de interrogação dentro de um pino: a sinalização existe, mas a área
   * ainda não está no mapeamento (RF12/RN17).
   */
  UNMAPPED_SIGNAL: [
    {
      kind: "path",
      d: "M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7z",
    },
    {
      kind: "path",
      d: "M9.6 8.9a2.4 2.4 0 1 1 3.3 2.2c-.7.4-1 .9-1 1.7",
    },
    { kind: "circle", cx: 12, cy: 15.6, r: 0.9 },
  ],
  /** Câmera: selo de que a sinalização tem registro fotográfico. */
  CAMERA: [
    {
      kind: "path",
      d: "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
    },
    { kind: "circle", cx: 12, cy: 13, r: 3 },
  ],
  /** Pessoa: guarda-vidas em plantão no mapa de situação (ADR-026). */
  LIFEGUARD: [
    { kind: "circle", cx: 12, cy: 7.5, r: 3.2 },
    { kind: "path", d: "M5.5 21c0-3.6 2.9-6.5 6.5-6.5s6.5 2.9 6.5 6.5" },
  ],
  /** Alerta: ocorrência ativa ou pendente de CIOSP. */
  OCCURRENCE: [
    { kind: "path", d: "M12 3 2.5 20.5h19L12 3z" },
    { kind: "line", x1: 12, y1: 9, x2: 12, y2: 14 },
    { kind: "circle", cx: 12, cy: 17.2, r: 0.9 },
  ],
};
