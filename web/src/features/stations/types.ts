/**
 * Tipo de estrutura operacional, como vem de `/api/v1/stations`.
 *
 * <p>Posto fixo tem posição cadastrada; base móvel é uma estrutura própria, com
 * nome e fotos, que aparece no mapa enquanto estiver ativa. A posição do
 * plantão é outra coisa: ela é do guarda-vidas e pode estar longe da base.
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

/** Página de estruturas (`GET /api/v1/operations/stations|admin/stations`). */
export interface StationPage {
  readonly items: readonly AdminStation[];
  /** Cursor opaco da próxima página; `null` na última. */
  readonly nextCursor: string | null;
  readonly hasMore: boolean;
  /** Total que atende ao filtro, independente da página carregada. */
  readonly total: number;
}

/** Filtros e cursor da lista de estruturas. */
export interface StationListQuery {
  readonly active?: boolean;
  readonly kind?: StationKind;
  readonly q?: string;
  readonly limit?: number;
  readonly cursor?: string;
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
