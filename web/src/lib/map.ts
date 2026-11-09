import type {
  Feature,
  FeatureCollection,
  LineString,
  Polygon,
  Position,
} from "geojson";
import type { StyleSpecification } from "maplibre-gl";
import type { Beach, FlagColor, RiskLevel } from "../types";

export const FLAG_COLORS: Record<FlagColor, string> = {
  GREEN: "#10b981",
  YELLOW: "#f59e0b",
  RED: "#ef4444",
  BLACK: "#111827",
};

export const RISK_ZONE_COLORS: Record<RiskLevel, string> = {
  LOW: "#10b981",
  MODERATE: "#f59e0b",
  HIGH: "#ef4444",
  EXTREME: "#0f172a",
};

export const DEMO_CENTER: [number, number] = [-37.05, -10.95];

export const DEMO_BOUNDS: [[number, number], [number, number]] = [
  [-37.45, -11.45],
  [-36.38, -10.45],
];

export function buildMapStyle(pmtilesUrl?: string | null): StyleSpecification {
  if (pmtilesUrl) {
    return {
      version: 8,
      sources: {
        graticule: { type: "geojson", data: graticuleGeoJson() },
        basemap: {
          type: "raster",
          tiles: [`pmtiles://${pmtilesUrl}`],
          tileSize: 256,
        },
      },
      layers: [
        {
          id: "basemap",
          type: "raster",
          source: "basemap",
        },
        {
          id: "graticule",
          type: "line",
          source: "graticule",
          minzoom: 5,
          layout: { visibility: "none" },
          paint: {
            "line-color": "#0d2836",
            "line-opacity": 0.25,
            "line-width": 1,
          },
        },
      ],
    };
  }

  return {
    version: 8,
    sources: {
      "osm-tiles": {
        type: "raster",
        tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
        tileSize: 256,
        attribution:
          '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      },
      graticule: {
        type: "geojson",
        data: graticuleGeoJson(),
      },
    },
    layers: [
      {
        id: "osm-tiles-layer",
        type: "raster",
        source: "osm-tiles",
        minzoom: 0,
        maxzoom: 19,
      },
      {
        id: "graticule",
        type: "line",
        source: "graticule",
        minzoom: 5,
        layout: { visibility: "none" },
        paint: {
          "line-color": "#0d2836",
          "line-opacity": 0.25,
          "line-width": 1,
        },
      },
    ],
  };
}

export function graticuleGeoJson(): FeatureCollection<LineString> {
  const features: Array<Feature<LineString>> = [];
  for (let lon = -37.8; lon <= -36.2; lon += 0.1) {
    features.push({
      type: "Feature",
      properties: {},
      geometry: {
        type: "LineString",
        coordinates: [
          [lon, -11.6],
          [lon, -10.3],
        ],
      },
    });
  }
  for (let lat = -11.6; lat <= -10.3; lat += 0.1) {
    features.push({
      type: "Feature",
      properties: {},
      geometry: {
        type: "LineString",
        coordinates: [
          [-37.8, lat],
          [-36.2, lat],
        ],
      },
    });
  }
  return { type: "FeatureCollection", features };
}

const METERS_PER_DEGREE = 111_320;

export function accuracyRing(
  center: { latitude: number; longitude: number },
  radiusMeters: number,
): Feature<Polygon> {
  const dLat = radiusMeters / METERS_PER_DEGREE;
  const dLon =
    radiusMeters /
    (METERS_PER_DEGREE * Math.cos((center.latitude * Math.PI) / 180));
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
    type: "Feature",
    properties: {},
    geometry: { type: "Polygon", coordinates: [positions] },
  };
}

/** Retorna GeoJSON vazio até que os polígonos reais do KML sejam importados. */
export function riskZonesOf(beaches: Beach[]): FeatureCollection<Polygon> {
  void beaches;
  return { type: "FeatureCollection", features: [] };
}
