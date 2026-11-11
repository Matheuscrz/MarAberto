/**
 * Deep links para o app de mapas do aparelho.
 *
 * <p>O envio da coordenada a um serviço externo é uma ação explícita do
 * operador (o clique), nunca automática: o console não embute mapa de terceiro
 * nem manda o ponto do banhista para fora sem gesto. Sem app de mapas
 * instalado, o link abre o Google Maps no navegador.
 */

/** Rota a partir de onde o aparelho está até o ponto do chamado. */
export function routeUrl(latitude: number, longitude: number): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${latitude},${longitude}`;
}

/** Ponto exato do chamado, sem rota. */
export function placeUrl(latitude: number, longitude: number): string {
  return `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;
}
