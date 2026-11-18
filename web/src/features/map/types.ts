/**
 * Contrato genérico de marcador do mapa. O `features/map` não conhece praia,
 * posto ou sinalização: quem compõe (o `app/` ou a página da coordenação) monta
 * a lista e resolve o item selecionado de volta para a entidade do seu domínio.
 *
 * Praia não tem marcador: a praia é acessada pela lista e pela ficha, e o
 * mapa mostra apenas postos/bases, sinalizações, guarda-vidas em plantão e
 * ocorrências ativas (mapa de situação da coordenação, ADR-026).
 */
export type MapMarkerKind =
  | "STATION_FIXED"
  | "STATION_MOBILE"
  | "SIGNAL"
  | "LIFEGUARD"
  | "OCCURRENCE";

export interface MapMarker {
  readonly id: string;
  readonly kind: MapMarkerKind;
  readonly latitude: number;
  readonly longitude: number;
  readonly label: string;
  /** Cor do pin (bandeira da sinalização ou estado do plantão) em CSS. */
  readonly color?: string;
  /** Exibe o selo de foto no marcador de sinalização. */
  readonly hasPhoto?: boolean;
}

/**
 * Pedido de enquadramento vindo da aplicação.
 *
 * <p>O `features/map` não sabe o que é praia, setor ou plantão: recebe o
 * retângulo/ponto e o identificador para saber quando o pedido mudou.
 * Enquadrar é efeito na instância do mapa (`fitBounds`/`flyTo`), nunca CSS no
 * elemento do marcador.
 */
export type MapFocus =
  | {
      /** `[minLon, minLat, maxLon, maxLat]`. */
      readonly bbox: readonly [number, number, number, number];
      readonly kind: "BEACH" | "SECTOR" | "SCOPE";
      readonly id: string;
    }
  | {
      readonly kind: "POINT";
      readonly id: string;
      readonly latitude: number;
      readonly longitude: number;
      /** Zoom do `flyTo`; o padrão aproxima o suficiente para ver o entorno. */
      readonly zoom?: number;
    };
