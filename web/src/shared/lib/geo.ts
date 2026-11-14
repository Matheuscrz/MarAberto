/**
 * Distância entre dois pontos WGS84.
 *
 * <p>Serve para o console mostrar "a X m de você" ao escolher onde abrir o
 * plantão e ao localizar uma estrutura em campo. A conta é a de haversine:
 * suficiente para distâncias curtas e sem dependência nova.
 */
export interface Coordinates {
  readonly latitude: number;
  readonly longitude: number;
}

const EARTH_RADIUS_M = 6_371_000;

export function distanceMetersBetween(
  from: Coordinates,
  to: Coordinates,
): number {
  const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
  const deltaLatitude = toRadians(to.latitude - from.latitude);
  const deltaLongitude = toRadians(to.longitude - from.longitude);
  const latitudeFrom = toRadians(from.latitude);
  const latitudeTo = toRadians(to.latitude);

  const haversine =
    Math.sin(deltaLatitude / 2) ** 2 +
    Math.cos(latitudeFrom) *
      Math.cos(latitudeTo) *
      Math.sin(deltaLongitude / 2) ** 2;

  return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(haversine)));
}
