import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Crosshair, Loader2, LocateOff } from 'lucide-react';
import 'maplibre-gl/dist/maplibre-gl.css';
import {
  AttributionControl,
  Map as MlMap,
  Marker,
  NavigationControl,
  addProtocol,
  setWorkerUrl,
} from 'maplibre-gl';
// O bundler não acompanha a URL do worker que o MapLibre deduz de
// `import.meta.url`; sem este import o worker não carrega e o mapa fica
// parado no fundo (só o background do estilo aparece).
import maplibreWorkerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url';
import {
  DEMO_BOUNDS,
  FLAG_COLORS,
  accuracyRing,
  buildMapStyle,
  riskZonesOf,
} from '../../lib/map';
import { sanitizeText } from '../../lib/sanitize';
import type { Beach } from '../../types';
import type { FeatureCollection, Polygon } from 'geojson';

export interface MapViewProps {
  beaches: Beach[];
  showRisks: boolean;
  showMarkers: boolean;
  showGraticule: boolean;
  selectedBeachId: string | null;
  onSelectBeach: (beachId: string | null) => void;
}

interface Located {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
}

function supportsWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return Boolean(canvas.getContext('webgl2') ?? canvas.getContext('webgl'));
  } catch {
    return false;
  }
}

type MapLibreMap = MlMap;

setWorkerUrl(maplibreWorkerUrl);

/**
 * Mapa principal do banhista (RF01): abre na região da instalação, com
 * marcadores de praia por bandeira e camadas de zonas de risco. MapLibre é um
 * chunk lazy; PMTiles (ADR-005) entra quando VITE_PMTILES_URL estiver definida.
 */
export function MapView({
  beaches,
  showRisks,
  showMarkers,
  showGraticule,
  selectedBeachId,
  onSelectBeach,
}: MapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const markersRef = useRef<Marker[]>([]);
  const userMarkerRef = useRef<Marker | null>(null);
  const onSelectRef = useRef(onSelectBeach);

  const [ready, setReady] = useState(false);
  // Sem WebGL o fallback é decidido já na primeira renderização (sem cascata).
  const [noWebGL] = useState(() => !supportsWebGL());
  const [locating, setLocating] = useState(false);
  const [location, setLocation] = useState<Located | null>(null);
  const [locateError, setLocateError] = useState<string | null>(null);

  // `beaches` é constante do módulo de dados; memo evita recriar o GeoJSON.
  const riskZones = useMemo(() => riskZonesOf(beaches), [beaches]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || noWebGL || mapRef.current) {
      return;
    }

    let cancelled = false;
    let map: MapLibreMap | null = null;

    void (async () => {
      const pmtilesUrl = import.meta.env.VITE_PMTILES_URL as string | undefined;
      if (pmtilesUrl) {
        try {
          const { Protocol } = await import('pmtiles');
          const protocol = new Protocol();
          addProtocol('pmtiles', (params, abortController) =>
            protocol.tile(params, abortController),
          );
          if (cancelled) {
            return;
          }
        } catch {
          // Sem PMTiles o app continua com o estilo local de demonstração.
        }
      }

      map = new MlMap({
        container,
        style: buildMapStyle(pmtilesUrl),
        bounds: DEMO_BOUNDS,
        // Reserva espaço para o cabeçalho flutuante e a barra inferior (mobile).
        fitBoundsOptions: { padding: { top: 96, bottom: 160, left: 48, right: 48 }, animate: false },
        attributionControl: false,
      });
      if (cancelled) {
        map.remove();
        return;
      }

      mapRef.current = map;
      map.addControl(new NavigationControl({ showCompass: false }), 'top-right');
      map.addControl(
        new AttributionControl({
          compact: true,
          customAttribution: 'MarAberto · dados de demonstração',
        }),
        'bottom-right',
      );

      map.on('error', () => {
        // Erros de tile/rede não devem travar a interface.
      });

      map.on('load', () => {
        if (cancelled || !map) {
          return;
        }
        map.addSource('risks', { type: 'geojson', data: riskZones });
        map.addLayer({
          id: 'risks-fill',
          type: 'fill',
          source: 'risks',
          paint: {
            'fill-color': ['get', 'color'],
            'fill-opacity': 0.25,
          },
        });
        map.addLayer({
          id: 'risks-line',
          type: 'line',
          source: 'risks',
          paint: {
            'line-color': ['get', 'color'],
            'line-width': 2,
            'line-opacity': 0.8,
          },
        });

        map.addSource('user', { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
        map.addLayer({
          id: 'user-accuracy',
          type: 'fill',
          source: 'user',
          filter: ['==', ['get', 'kind'], 'accuracy'],
          paint: { 'fill-color': '#3da6c4', 'fill-opacity': 0.2 },
        });
        map.addLayer({
          id: 'user-point',
          type: 'circle',
          source: 'user',
          filter: ['==', ['get', 'kind'], 'position'],
          paint: {
            'circle-radius': 7,
            'circle-color': '#228aa8',
            'circle-stroke-color': '#ffffff',
            'circle-stroke-width': 2,
          },
        });

        setReady(true);
      });

      // Toque fora dos marcadores fecha a ficha da praia.
      map.on('click', (event) => {
        const target = event.originalEvent.target;
        if (target instanceof Element && target.closest('[data-mld-marker]')) {
          return;
        }
        onSelectRef.current(null);
      });
    })();

    return () => {
      cancelled = true;
      mapRef.current?.remove();
      mapRef.current = null;
      markersRef.current = [];
      userMarkerRef.current = null;
      setReady(false);
    };
    // `beaches` muda junto com a aplicação; o efeito roda só uma vez.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Camadas ligadas/desligadas sem recarregar o mapa (RF01).
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) {
      return;
    }
    map.setLayoutProperty('risks-fill', 'visibility', showRisks ? 'visible' : 'none');
    map.setLayoutProperty('risks-line', 'visibility', showRisks ? 'visible' : 'none');
  }, [ready, showRisks]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) {
      return;
    }
    map.setLayoutProperty('graticule', 'visibility', showGraticule ? 'visible' : 'none');
  }, [ready, showGraticule]);

  useEffect(() => {
    onSelectRef.current = onSelectBeach;
  }, [onSelectBeach]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) {
      return;
    }
    (map.getSource('risks') as { setData: (data: FeatureCollection<Polygon>) => void } | undefined)
      ?.setData(riskZones);
  }, [riskZones, ready]);

  // Marcadores de praia com bandeira atual, recriados por render simples.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) {
      return;
    }
    markersRef.current.forEach((marker) => marker.remove());
    markersRef.current = [];

    if (!showMarkers) {
      return;
    }

    beaches.forEach((beach) => {
      const element = document.createElement('button');
      element.type = 'button';
      element.dataset.mldMarker = 'true';
      element.className = 'mld-pin';
      element.setAttribute(
        'aria-label',
        `${sanitizeText(beach.name)} — abrir ficha da praia`,
      );
      if (beach.id === selectedBeachId) {
        element.classList.add('mld-pin-selected');
      }

      const dot = document.createElement('span');
      dot.className = 'mld-pin-dot';
      dot.style.backgroundColor = FLAG_COLORS[beach.flag];
      element.appendChild(dot);

      const label = document.createElement('span');
      label.className = 'mld-pin-label';
      label.textContent = sanitizeText(beach.name);
      element.appendChild(label);

      element.addEventListener('click', (event) => {
        event.stopPropagation();
        onSelectRef.current(beach.id);
      });

      const marker = new Marker({ element, anchor: 'bottom', offset: [-1, 4] })
        .setLngLat([beach.longitude, beach.latitude])
        .addTo(map);
      markersRef.current.push(marker);
    });
  }, [beaches, showMarkers, selectedBeachId, ready]);

  // Localização sob demanda (RF01): só aparece quando o usuário pede.
  const locate = () => {
    if (!mapRef.current || !ready) {
      return;
    }
    if (!('geolocation' in navigator)) {
      setLocateError('Este navegador não oferece localização.');
      return;
    }
    setLocating(true);
    setLocateError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setLocation({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters:
            Number.isFinite(position.coords.accuracy) && position.coords.accuracy > 0
              ? position.coords.accuracy
              : undefined,
        });
      },
      () => {
        setLocating(false);
        setLocateError('Não foi possível obter a localização. Verifique a permissão.');
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  };

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !ready) {
      return;
    }

    if (!location) {
      userMarkerRef.current?.remove();
      userMarkerRef.current = null;
      (map.getSource('user') as { setData: (data: FeatureCollection<Polygon>) => void } | undefined)
        ?.setData({ type: 'FeatureCollection', features: [] });
      return;
    }

    const features = [accuracyRing(location, Math.max(location.accuracyMeters ?? 30, 30))];
    (map.getSource('user') as { setData: (data: FeatureCollection<Polygon>) => void } | undefined)
      ?.setData({
        type: 'FeatureCollection',
        features: features.map((feature) => ({
          ...feature,
          properties: { kind: 'accuracy' },
        })),
      });

    userMarkerRef.current?.remove();
    userMarkerRef.current = null;
    const userDot = document.createElement('div');
    userDot.className = 'mld-user-dot';
    userMarkerRef.current = new Marker({ element: userDot })
      .setLngLat([location.longitude, location.latitude])
      .addTo(map);

    map.easeTo({ center: [location.longitude, location.latitude], zoom: Math.max(map.getZoom(), 13.5) });
  }, [location, ready]);

  if (noWebGL) {
    return (
      <div
        ref={containerRef}
        className="grid h-full place-items-center bg-ocean-100 px-6 text-center"
      >
        <div className="max-w-sm">
          <LocateOff aria-hidden="true" className="mx-auto size-10 text-ocean-600" />
          <h2 className="mt-3 text-xl font-black text-ocean-950">
            O navegador não tem suporte a WebGL para desenhar o mapa
          </h2>
          <p className="mt-2 text-sm text-ocean-700">
            Você ainda pode consultar bandeiras, riscos e marés na lista de praias.
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
      <button
        type="button"
        onClick={locate}
        disabled={locating || !ready}
        aria-label={locating ? 'Obtendo localização…' : 'Centralizar na minha localização'}
        className="absolute right-3 top-20 z-20 grid size-11 place-items-center rounded-xl border border-ocean-900/10 bg-white shadow-md transition hover:bg-ocean-50 disabled:opacity-60 md:top-24"
      >
        {locating ? (
          <Loader2 aria-hidden="true" className="size-5 animate-spin text-ocean-700" />
        ) : (
          <Crosshair aria-hidden="true" className="size-5 text-ocean-700" />
        )}
      </button>

      {locateError ? (
        <p
          role="alert"
          className="absolute inset-x-3 top-16 z-20 rounded-xl border border-coral-300 bg-white px-3 py-2 text-sm font-semibold text-coral-800 shadow-md md:left-auto md:right-16 md:max-w-xs"
        >
          {locateError}
        </p>
      ) : null}
    </div>
  );
}
