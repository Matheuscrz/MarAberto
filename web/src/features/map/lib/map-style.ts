import type {
  Feature,
  FeatureCollection,
  LineString,
  Polygon,
  Position,
} from "geojson";
import type { StyleSpecification } from "maplibre-gl";

export const DEFAULT_MAP_CENTER: [number, number] = import.meta.env
  .VITE_MAP_DEFAULT_CENTER
  ? (JSON.parse(import.meta.env.VITE_MAP_DEFAULT_CENTER) as [number, number])
  : [-51.9253, -14.235]; // Centro do Brasil como fallback neutro

export const DEFAULT_MAP_BOUNDS:
  | [[number, number], [number, number]]
  | undefined = import.meta.env.VITE_MAP_BOUNDS
  ? (JSON.parse(import.meta.env.VITE_MAP_BOUNDS) as [
      [number, number],
      [number, number],
    ])
  : undefined;

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
  for (let lon = -74; lon <= -34; lon += 0.5) {
    features.push({
      type: "Feature",
      properties: {},
      geometry: {
        type: "LineString",
        coordinates: [
          [lon, -34],
          [lon, 6],
        ],
      },
    });
  }
  for (let lat = -34; lat <= 6; lat += 0.5) {
    features.push({
      type: "Feature",
      properties: {},
      geometry: {
        type: "LineString",
        coordinates: [
          [-74, lat],
          [-34, lat],
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
