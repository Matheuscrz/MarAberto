/**
 * Tipo de estrutura operacional, como vem de `/api/v1/stations`.
 *
 * <p>Posto fixo tem posição cadastrada; base móvel aparece na última posição do
 * plantão e some do mapa quando o turno encerra ou o lease expira (RN17).
 */
export type StationKind = "FIXED" | "MOBILE";

/**
 * De onde veio a coordenada da estrutura (RF11).
 *
 * <p>`DEVICE_GPS` é a leitura do aparelho no cadastro/reposição; `MANUAL` é o
 * cadastro antigo; `MAP_PICK` é o ponto escolhido no mapa.
 */
export type StationPositionSource = "MANUAL" | "DEVICE_GPS" | "MAP_PICK";

/** Estrutura visível no mapa público. */
export interface PublicStation {
  readonly id: string;
  readonly kind: StationKind;
  readonly name: string;
  /** Praia do posto fixo; nula para base móvel. */
  readonly beachName: string | null;
  readonly latitude: number;
  readonly longitude: number;
  /** Quando a posição da base foi conhecida; nulo no posto fixo. */
  readonly positionUpdatedAt: string | null;
  /** Fotos do posto/base exibidas na ficha pública. */
  readonly photoUrls: readonly string[];
}

/** Estrutura no painel da TI, ativa ou não. */
export interface AdminStation {
  readonly id: string;
  readonly kind: StationKind;
  readonly name: string;
  readonly beachId: string | null;
  readonly beachName: string | null;
  readonly latitude: number;
  readonly longitude: number;
  readonly positionSource: StationPositionSource | null;
  readonly positionAccuracyM: number | null;
  readonly positionUpdatedAt: string | null;
  readonly active: boolean;
  readonly deactivatedAt: string | null;
  readonly statusNote: string | null;
  /** Fotos do posto/base, até 3. */
  readonly photoUrls: readonly string[];
}

/** Cadastro de posto/base na posição atual (RF11). */
export interface CreateStationInput {
  readonly kind: StationKind;
  readonly name: string;
  /** Obrigatória no posto fixo; ignorada na base móvel. */
  readonly beachId?: string | null;
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters?: number;
  /** Fotos já enviadas pelo operador (até 3). */
  readonly mediaIds?: readonly string[];
}

/** Reposição da estrutura pela leitura atual do aparelho (RF11). */
export interface StationPositionInput {
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters?: number;
}

/** Teto de fotos por estrutura, o mesmo da sinalização. */
export const MAX_STATION_PHOTOS = 3;

export const STATION_KIND_LABEL: Record<StationKind, string> = {
  FIXED: "Posto fixo",
  MOBILE: "Base móvel",
};

export const POSITION_SOURCE_LABEL: Record<StationPositionSource, string> = {
  MANUAL: "Posição de cadastro",
  DEVICE_GPS: "GPS do aparelho",
  MAP_PICK: "Ponto escolhido no mapa",
};
