import type {
  Feature,
  FeatureCollection,
  LineString,
  Polygon,
  Position,
} from 'geojson';
import type { StyleSpecification } from '@maplibre/maplibre-gl-style-spec';
import type { Beach, FlagColor, RiskLevel } from '../types';

/*
 * Suporte de mapa do MarAberto.
 *
 * ADR-005: produção usa PMTiles próprios (VITE_PMTILES_URL). Sem essa variável
 * o mapa funciona com um estilo local, sem tiles externos — oceano, costa de
 * demonstração, retícula e marcadores da própria aplicação.
 */

/** Cores de bandeira, em hex, alinhadas com Badges.tsx. */
export const FLAG_COLORS: Record<FlagColor, string> = {
  GREEN: '#10b981',
  YELLOW: '#f59e0b',
  RED: '#ef4444',
  BLACK: '#111827',
};

/** Cor de preenchimento das zonas de risco por severidade. */
export const RISK_ZONE_COLORS: Record<RiskLevel, string> = {
  LOW: '#10b981',
  MODERATE: '#f59e0b',
  HIGH: '#ef4444',
  EXTREME: '#0f172a',
};

/**
 * Centro de demonstração (média das praias demo, litoral de Aracaju/SE).
 * A instalação real substituirá pela configuração de região.
 */
export const DEMO_CENTER: [number, number] = [-37.0371, -10.9515];

/**
 * Costa de demonstração: polígono de terra com oceano a leste. Os marcadores
 * de praia caem sobre a terra; nenhum traço aqui é dado oficial.
 */
const COAST_POLYGON: Position[] = [
  [-37.6, -11.1],
  [-37.09, -11.1],
  [-37.07, -11.06],
  [-37.055, -11.03],
  [-37.043, -11.01],
  [-37.035, -10.995],
  [-37.036, -10.985],
  [-37.038, -10.96],
  [-37.034, -10.94],
  [-37.037, -10.92],
  [-37.033, -10.905],
  [-37.03, -10.9],
  [-37.034, -10.885],
  [-37.045, -10.86],
  [-37.057, -10.83],
  [-37.07, -10.82],
  [-37.6, -10.82],
];

/** Anel linear fechado (primeiro = último ponto), como exige o GeoJSON. */
const COAST_RING: Position[] = [...COAST_POLYGON, COAST_POLYGON[0]];

/** Limites usados para enquadrar as praias de demonstração. */
export const DEMO_BOUNDS: [[number, number], [number, number]] = [
  [-37.09, -11.03],
  [-37.0, -10.88],
];

/**
 * Estilo base do mapa, sem tiles externos: o oceano é o fundo, a terra é o
 * polígono acima e a retícula fica desligada por padrão.
 *
 * Com `pmtilesUrl` (ADR-005) o estilo passa a ser o basemap raster próprio e
 * descarta a costa de demonstração.
 */
export function buildMapStyle(pmtilesUrl?: string | null): StyleSpecification {
  if (pmtilesUrl) {
    return {
      version: 8,
      sources: {
        graticule: { type: 'geojson', data: graticuleGeoJson() },
        basemap: {
          type: 'raster',
          tiles: [`pmtiles://${pmtilesUrl}`],
          tileSize: 256,
        },
      },
      layers: [
        {
          id: 'basemap',
          type: 'raster',
          source: 'basemap',
        },
      ],
    };
  }

  return {
    version: 8,
    sources: {
      coast: {
        type: 'geojson',
        data: coastlineGeoJson(),
      },
      graticule: {
        type: 'geojson',
        data: graticuleGeoJson(),
      },
    },
    layers: [
      {
        id: 'ocean',
        type: 'background',
        paint: { 'background-color': '#b7dce9' },
      },
      {
        id: 'graticule',
        type: 'line',
        source: 'graticule',
        minzoom: 9,
        layout: { visibility: 'none' },
        paint: { 'line-color': '#0d2836', 'line-opacity': 0.25, 'line-width': 1 },
      },
      {
        id: 'land',
        type: 'fill',
        source: 'coast',
        paint: { 'fill-color': '#f2dfba', 'fill-outline-color': '#0d2836' },
      },
    ],
  };
}

/** Coastline pré-computada (dados constantes). */
const COASTLINE_GEOJSON: FeatureCollection<Polygon> = {
  type: 'FeatureCollection',
  features: [
    {
      type: 'Feature',
      properties: {},
      geometry: { type: 'Polygon', coordinates: [COAST_RING] },
    },
  ],
};

function coastlineGeoJson(): FeatureCollection<Polygon> {
  return COASTLINE_GEOJSON;
}

/** Retícula de 0,1 grau ao redor da área de demonstração. */
function graticuleGeoJson(): FeatureCollection<LineString> {
  const features: Array<Feature<LineString>> = [];
  for (let lon = -37.6; lon <= -36.98; lon += 0.1) {
    features.push({
      type: 'Feature',
      properties: {},
      geometry: { type: 'LineString', coordinates: [[lon, -11.1], [lon, -10.82]] },
    });
  }
  for (let lat = -11.1; lat <= -10.82; lat += 0.1) {
    features.push({
      type: 'Feature',
      properties: {},
      geometry: { type: 'LineString', coordinates: [[-37.6, lat], [-36.98, lat]] },
    });
  }
  return { type: 'FeatureCollection', features };
}

/** ~111.320 m por grau de latitude (conversão simples o suficiente p/ demo). */
const METERS_PER_DEGREE = 111_320;

/** Polígono circular aproximado (32 vértices) para o anel de precisão. */
export function accuracyRing(
  center: { latitude: number; longitude: number },
  radiusMeters: number,
): Feature<Polygon> {
  const dLat = radiusMeters / METERS_PER_DEGREE;
  const dLon = radiusMeters / (METERS_PER_DEGREE * Math.cos((center.latitude * Math.PI) / 180));
  const positions: Position[] = [];
  for (let i = 0; i < 32; i += 1) {
    const theta = (i / 32) * 2 * Math.PI;
    positions.push([
      center.longitude + dLon * Math.sin(theta),
      center.latitude + dLat * Math.cos(theta),
    ]);
  }
  positions.push(positions[0]);
  return {
    type: 'Feature',
    properties: {},
    geometry: { type: 'Polygon', coordinates: [positions] },
  };
}

/** Todas as zonas de risco das praias como GeoJSON poligonal (demo). */
export function riskZonesOf(beaches: Beach[]): FeatureCollection<Polygon> {
  const features: Array<Feature<Polygon>> = [];

  beaches.forEach((beach) => {
    beach.risks.forEach((zone, index) => {
      features.push({
        type: 'Feature',
        properties: {
          id: zone.id,
          beachId: beach.id,
          label: zone.label,
          severity: zone.severity,
          color: RISK_ZONE_COLORS[zone.severity],
        },
        geometry: zonePolygon({ latitude: beach.latitude, longitude: beach.longitude }, index),
      });
    });
  });

  return { type: 'FeatureCollection', features };
}

/** Polígono determinístico deslocado para a água ao sul da praia (demo). */
function zonePolygon(
  beach: { latitude: number; longitude: number },
  index: number,
): Polygon {
  const dLat = 0.0028;
  const dLon = 0.0042;
  const centerLat = beach.latitude + (index % 2 === 0 ? -0.002 : 0.0055);
  const centerLon = beach.longitude + 0.007;

  return {
    type: 'Polygon',
    coordinates: [[
      [centerLon - dLon, centerLat - dLat],
      [centerLon + dLon, centerLat - dLat],
      [centerLon + dLon, centerLat + dLat],
      [centerLon - dLon, centerLat + dLat],
      [centerLon - dLon, centerLat - dLat],
    ]],
  };
}
