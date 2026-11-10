/**
 * Contrato genérico de marcador do mapa. O `features/map` não conhece praia,
 * posto ou sinalização: quem compõe (o `app/`) monta a lista e resolve o item
 * selecionado de volta para a entidade do seu domínio.
 *
 * Praia não tem marcador: a praia é acessada pela lista e pela ficha, e o
 * mapa mostra apenas postos/bases e sinalizações.
 */
export type MapMarkerKind = "STATION_FIXED" | "STATION_MOBILE" | "SIGNAL";

export interface MapMarker {
  readonly id: string;
  readonly kind: MapMarkerKind;
  readonly latitude: number;
  readonly longitude: number;
  readonly label: string;
  /** Cor da bandeira (sinalizações) em formato CSS. */
  readonly color?: string;
  /** Exibe o selo de foto no marcador de sinalização. */
  readonly hasPhoto?: boolean;
}
