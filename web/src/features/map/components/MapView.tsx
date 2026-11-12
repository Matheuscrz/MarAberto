import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Crosshair, Loader2, LocateOff, Minus, Plus } from "lucide-react";
import "maplibre-gl/dist/maplibre-gl.css";
import {
  AttributionControl,
  Map as MlMap,
  Marker,
  addProtocol,
  setWorkerUrl,
  type GeoJSONSource,
} from "maplibre-gl";
import maplibreWorkerUrl from "maplibre-gl/dist/maplibre-gl-worker.mjs?url";
import type { FeatureCollection, Polygon } from "geojson";
import {
  DEFAULT_MAP_CENTER,
  DEFAULT_MAP_BOUNDS,
  accuracyRing,
  buildMapStyle,
} from "@/features/map/lib/map-style";
import {
  createMarkerElement,
  markerAnchor,
  updateMarkerElement,
  type MarkerHandles,
} from "@/features/map/lib/marker-element";
import type { MapMarker } from "@/features/map/types";
import {
  GeolocationError,
  getDevicePosition,
} from "@/shared/lib/geolocation";

export interface MapViewProps {
  /** Marcadores já resolvidos pelo app: o mapa não conhece praia/posto. */
  readonly markers: readonly MapMarker[];
  readonly selectedMarkerId: string | null;
  readonly onSelectMarker: (markerId: string | null) => void;
  readonly riskZones: FeatureCollection<Polygon>;
  readonly showRisks: boolean;
  readonly showMarkers: boolean;
  readonly showGraticule: boolean;
}

interface Located {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
}

interface MarkerEntry {
  readonly marker: Marker;
  readonly handles: MarkerHandles;
}

function supportsWebGL(): boolean {
  try {
    const canvas = document.createElement("canvas");
    return Boolean(canvas.getContext("webgl2") ?? canvas.getContext("webgl"));
  } catch {
    return false;
  }
}

type MapLibreMap = MlMap;
setWorkerUrl(maplibreWorkerUrl);

const EMPTY_ZONES: FeatureCollection<Polygon> = {
  type: "FeatureCollection",
  features: [],
};

export function MapView({
  markers,
  selectedMarkerId,
  onSelectMarker,
  riskZones,
  showRisks,
  showMarkers,
  showGraticule,
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markerRegistryRef = useRef<Map<string, MarkerEntry>>(new Map());
  const userMarkerRef = useRef<Marker | null>(null);

  const onSelectRef = useRef(onSelectMarker);

  const [ready, setReady] = useState(false);
  const [noWebGL] = useState(() => !supportsWebGL());
  const [locating, setLocating] = useState(false);
  const [location, setLocation] = useState<Located | null>(null);
  const [locateError, setLocateError] = useState<string | null>(null);

  useEffect(() => {
    onSelectRef.current = onSelectMarker;
  }, [onSelectMarker]);

  // Cria o mapa uma única vez. Camadas e marcadores são atualizados depois,
  // sem recriar o mapa (recriar derrubava o hover e "teleportava" os pins).
  useEffect(() => {
    const container = containerRef.current;
    if (!container || noWebGL || mapRef.current) return;

    const registry = markerRegistryRef.current;
    let cancelled = false;
    let map: MapLibreMap | null = null;

    void (async () => {
      const pmtilesUrl = import.meta.env.VITE_PMTILES_URL as string | undefined;
      if (pmtilesUrl) {
        try {
          const { Protocol } = await import("pmtiles");
          const protocol = new Protocol();
          addProtocol("pmtiles", (params, abortController) =>
            protocol.tile(params, abortController),
          );
          if (cancelled) return;
        } catch {
          // Segue com basemap padrão OSM
        }
      }

      map = new MlMap({
        container,
        style: buildMapStyle(pmtilesUrl),
        ...(DEFAULT_MAP_BOUNDS
          ? {
              bounds: DEFAULT_MAP_BOUNDS,
              fitBoundsOptions: { padding: 48, animate: false },
            }
          : {
              center: DEFAULT_MAP_CENTER,
              zoom: Number(import.meta.env.VITE_MAP_DEFAULT_ZOOM ?? 4),
            }),
        attributionControl: false,
      });

      if (cancelled) {
        map.remove();
        return;
      }
      mapRef.current = map;

      map.addControl(
        new AttributionControl({
          compact: true,
          customAttribution: "MarAberto • Salvamento Aquático",
        }),
        "bottom-right",
      );

      map.on("load", () => {
        if (cancelled || !map) return;

        map.addSource("risks", { type: "geojson", data: EMPTY_ZONES });
        map.addLayer({
          id: "risks-fill",
          type: "fill",
          source: "risks",
          paint: {
            "fill-color": ["get", "color"],
            "fill-opacity": 0.25,
          },
        });
        map.addLayer({
          id: "risks-line",
          type: "line",
          source: "risks",
          paint: {
            "line-color": ["get", "color"],
            "line-width": 2,
            "line-opacity": 0.8,
          },
        });

        map.addSource("user", {
          type: "geojson",
          data: { type: "FeatureCollection", features: [] },
        });
        map.addLayer({
          id: "user-accuracy",
          type: "fill",
          source: "user",
          filter: ["==", ["get", "kind"], "accuracy"],
          paint: { "fill-color": "#3da6c4", "fill-opacity": 0.2 },
        });
        map.addLayer({
          id: "user-point",
          type: "circle",
          source: "user",
          filter: ["==", ["get", "kind"], "position"],
          paint: {
            "circle-radius": 7,
            "circle-color": "#228aa8",
            "circle-stroke-color": "#ffffff",
            "circle-stroke-width": 2,
          },
        });

        setReady(true);
      });

      map.on("click", (event) => {
        const originalTarget = event.originalEvent
          ?.target as HTMLElement | null;
        if (originalTarget?.closest("[data-map-marker='true']")) {
          return;
        }

        onSelectRef.current(null);
      });
    })();

    return () => {
      cancelled = true;
      mapRef.current?.remove();
      mapRef.current = null;
      registry.clear();
      userMarkerRef.current = null;
      setReady(false);
    };
  }, [noWebGL]);

  // Zonas de risco: só troca o dado da fonte, sem recriar o mapa.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) return;
    (map.getSource("risks") as GeoJSONSource | undefined)?.setData(riskZones);
  }, [ready, riskZones]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) return;
    map.setLayoutProperty(
      "risks-fill",
      "visibility",
      showRisks ? "visible" : "none",
    );
    map.setLayoutProperty(
      "risks-line",
      "visibility",
      showRisks ? "visible" : "none",
    );
  }, [ready, showRisks]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) return;
    map.setLayoutProperty(
      "graticule",
      "visibility",
      showGraticule ? "visible" : "none",
    );
  }, [ready, showGraticule]);

  // Cria/atualiza/remove marcadores por id. Seleção NÃO recria os elementos.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) return;

    const registry = markerRegistryRef.current;

    if (!showMarkers) {
      registry.forEach((entry) => entry.marker.remove());
      registry.clear();
      return;
    }

    const nextIds = new Set(markers.map((marker) => marker.id));

    for (const [id, entry] of registry) {
      if (!nextIds.has(id)) {
        entry.marker.remove();
        registry.delete(id);
      }
    }

    markers.forEach((marker) => {
      const existing = registry.get(marker.id);
      if (existing) {
        existing.marker.setLngLat([marker.longitude, marker.latitude]);
        updateMarkerElement(existing.handles, marker);
        return;
      }

      const handles = createMarkerElement(marker, (id) =>
        onSelectRef.current(id),
      );
      const mapMarker = new Marker({
        element: handles.element,
        anchor: markerAnchor(),
        offset: [0, -4],
      })
        .setLngLat([marker.longitude, marker.latitude])
        .addTo(map);

      registry.set(marker.id, { marker: mapMarker, handles });
    });
  }, [markers, showMarkers, ready]);

  // Estado selecionado só troca classe; nunca recria o marcador.
  useEffect(() => {
    markerRegistryRef.current.forEach((entry, id) => {
      entry.handles.element.classList.toggle(
        "mld-marker-selected",
        id === selectedMarkerId,
      );
    });
  }, [selectedMarkerId, markers, showMarkers, ready]);

  // Posicionamento do usuário
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) return;

    if (!location) {
      userMarkerRef.current?.remove();
      userMarkerRef.current = null;
      (map.getSource("user") as GeoJSONSource | undefined)?.setData(
        EMPTY_ZONES,
      );
      return;
    }

    const features = [
      accuracyRing(location, Math.max(location.accuracyMeters ?? 30, 30)),
    ];
    (map.getSource("user") as GeoJSONSource | undefined)?.setData({
      type: "FeatureCollection",
      features: features.map((feature) => ({
        ...feature,
        properties: { kind: "accuracy" },
      })),
    });

    userMarkerRef.current?.remove();
    userMarkerRef.current = null;

    const userDot = document.createElement("div");
    userDot.className = "mld-user-dot";
    userMarkerRef.current = new Marker({ element: userDot })
      .setLngLat([location.longitude, location.latitude])
      .addTo(map);

    map.easeTo({
      center: [location.longitude, location.latitude],
      zoom: Math.max(map.getZoom(), 13.5),
    });
  }, [location, ready]);

  const locate = async () => {
    if (!mapRef.current || !ready) return;
    setLocating(true);
    setLocateError(null);
    try {
      const position = await getDevicePosition({
        enableHighAccuracy: true,
        timeout: 10_000,
        maximumAge: 60_000,
      });
      setLocation({
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyMeters:
          Number.isFinite(position.accuracyMeters) && position.accuracyMeters > 0
            ? position.accuracyMeters
            : undefined,
      });
    } catch (error) {
      setLocateError(
        error instanceof GeolocationError
          ? error.message
          : "Não foi possível obter a localização. Verifique a permissão.",
      );
    } finally {
      setLocating(false);
    }
  };

  if (noWebGL) {
    return (
      <div
        ref={containerRef}
        className="grid h-full place-items-center bg-ocean-100 px-6 text-center"
      >
        <div className="max-w-sm">
          <LocateOff
            aria-hidden="true"
            className="mx-auto size-10 text-ocean-600"
          />
          <h2 className="mt-3 text-xl font-black text-ocean-950">
            O navegador não tem suporte a WebGL para desenhar o mapa
          </h2>
          <p className="mt-2 text-sm text-ocean-700">
            Você ainda pode consultar praias, postos e bandeiras pela lista.
          </p>
          <Link
            to="/praias"
            className="mt-4 inline-flex min-h-12 items-center rounded-xl bg-ocean-950 px-4 font-bold text-white"
          >
            Ver lista de praias
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className="relative h-full w-full">
      <div className="absolute bottom-28 right-2.5 z-20 flex flex-col gap-2">
        <div className="flex flex-col overflow-hidden rounded-xl border border-ocean-900/10 bg-white shadow-md">
          <button
            type="button"
            onClick={() => mapRef.current?.zoomIn()}
            disabled={!ready}
            aria-label="Aproximar o mapa"
            className="grid size-10 place-items-center text-ocean-700 transition hover:bg-ocean-50 disabled:opacity-50"
          >
            <Plus aria-hidden="true" className="size-5" />
          </button>
          <span aria-hidden="true" className="h-px bg-ocean-100" />
          <button
            type="button"
            onClick={() => mapRef.current?.zoomOut()}
            disabled={!ready}
            aria-label="Afastar o mapa"
            className="grid size-10 place-items-center text-ocean-700 transition hover:bg-ocean-50 disabled:opacity-50"
          >
            <Minus aria-hidden="true" className="size-5" />
          </button>
        </div>

        <button
          type="button"
          onClick={locate}
          disabled={locating || !ready}
          aria-label={
            locating
              ? "Obtendo localização"
              : "Centralizar na minha localização"
          }
          className="grid size-10 place-items-center rounded-xl border border-ocean-900/10 bg-white text-ocean-700 shadow-md transition hover:bg-ocean-50 disabled:opacity-60"
        >
          {locating ? (
            <Loader2
              aria-hidden="true"
              className="size-5 animate-spin text-ocean-700"
            />
          ) : (
            <Crosshair aria-hidden="true" className="size-5 text-ocean-700" />
          )}
        </button>
      </div>

      {locateError ? (
        <p
          role="alert"
          className="absolute bottom-28 right-14 z-20 max-w-56 rounded-xl border border-coral-300 bg-white px-3 py-2 text-xs font-semibold text-coral-800 shadow-md"
        >
          {locateError}
        </p>
      ) : null}
    </div>
  );
}
