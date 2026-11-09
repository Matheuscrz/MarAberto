import { lazy, Suspense, useMemo, useState } from 'react';
import { DEMO_DATA_NOTICE, beaches } from '../data/demo-data';
import { BeachSheet } from '../components/BeachSheet';
import { InstallPrompt } from '../components/InstallPrompt';

const MapView = lazy(() =>
  import('../components/map/MapView').then((m) => ({ default: m.MapView })),
);

function MapFallback() {
  return (
    <div className="grid h-full w-full place-items-center bg-ocean-100">
      <p role="status" className="font-semibold text-ocean-700">
        Carregando o mapa…
      </p>
    </div>
  );
}

/**
 * Tela principal: mapa em tela cheia (RF01) com camadas ligáveis, ficha da
 * praia sobre o mapa e o SOS sempre a um toque (botão flutuante no Layout).
 */
export function HomePage() {
  const [showRisks, setShowRisks] = useState(true);
  const [showMarkers, setShowMarkers] = useState(true);
  const [showGraticule, setShowGraticule] = useState(false);
  const [selectedBeachId, setSelectedBeachId] = useState<string | null>(null);

  const selectedBeach = useMemo(
    () => beaches.find((beach) => beach.id === selectedBeachId) ?? null,
    [selectedBeachId],
  );

  return (
    <div className="absolute inset-0">
      <Suspense fallback={<MapFallback />}>
        <MapView
          beaches={beaches}
          showRisks={showRisks}
          showMarkers={showMarkers}
          showGraticule={showGraticule}
          selectedBeachId={selectedBeachId}
          onSelectBeach={setSelectedBeachId}
        />
      </Suspense>

      <div className="pointer-events-none absolute inset-x-3 top-20 z-20 flex items-start gap-2">
        <div className="pointer-events-auto flex max-w-[calc(100%-4.5rem)] flex-wrap gap-2 rounded-2xl border border-ocean-900/10 bg-white/90 p-1.5 shadow-md backdrop-blur">
          <ChipToggle label="Zonas de risco" on={showRisks} onToggle={() => setShowRisks((v) => !v)} />
          <ChipToggle label="Marcadores" on={showMarkers} onToggle={() => setShowMarkers((v) => !v)} />
          <ChipToggle label="Retícula" on={showGraticule} onToggle={() => setShowGraticule((v) => !v)} />
        </div>
      </div>

      <div className="absolute left-3 top-3 z-30 max-w-[calc(100%-1.5rem)] md:left-auto md:right-3 md:top-3 md:max-w-sm">
        <InstallPrompt />
      </div>
      <p className="absolute bottom-24 left-3 z-20 max-w-[16rem] rounded-xl border border-amber-300 bg-amber-50/95 px-3 py-2 text-xs font-semibold text-amber-950 shadow-md backdrop-blur md:bottom-4">
        {DEMO_DATA_NOTICE}
      </p>

      {selectedBeach ? (
        <BeachSheet beach={selectedBeach} onClose={() => setSelectedBeachId(null)} />
      ) : null}
    </div>
  );
}

function ChipToggle({
  label,
  on,
  onToggle,
}: {
  label: string;
  on: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={on}
      className={[
        'min-h-9 rounded-xl px-3 py-1.5 text-sm font-bold transition',
        on ? 'bg-ocean-950 text-white' : 'bg-white text-ocean-800 hover:bg-ocean-50',
      ].join(' ')}
    >
      {label}
    </button>
  );
}
