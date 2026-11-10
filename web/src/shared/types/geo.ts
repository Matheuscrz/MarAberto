/** Coordenadas WGS84 validadas no cliente (o backend revalida tudo). */
export interface Coordinates {
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters?: number;
}
