import { lazy, Suspense, useMemo, useState } from "react";
import {
  NavLink,
  Link,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  BookOpen,
  ChevronDown,
  ChevronUp,
  Info,
  LogIn,
  MapPin,
  Moon,
  Phone,
  Search,
  Shield,
  Siren,
  X,
} from "lucide-react";
import { OfflineBanner } from "@/app/layout/OfflineBanner";
import { InstallPrompt } from "@/app/layout/InstallPrompt";
import { homePathForRole, useCurrentUser } from "@/features/auth";
import { SosFab } from "@/features/sos";
import { BeachSheet, beaches, riskZonesOf } from "@/features/beaches";
import { SignalSheet, beachFlagSignals } from "@/features/signals";
import { StationSheet, lifeguardStations } from "@/features/stations";
import { MapLegend, type MapMarker } from "@/features/map";
import { DEMO_DATA_NOTICE } from "@/shared/lib/notices";
import { FLAG_COLORS } from "@/shared/lib/flags";

const MapView = lazy(() =>
  import("@/features/map/components/MapView").then((m) => ({
    default: m.MapView,
  })),
);

function MapFallback() {
  return (
    <div className="grid h-full w-full place-items-center bg-ocean-100">
      <output className="block font-semibold text-ocean-700">
        Carregando o mapa
      </output>
    </div>
  );
}

const BEACH_PREFIX = "beach:";
const STATION_PREFIX = "station:";
const SIGNAL_PREFIX = "signal:";

const quickTabs = [
  { to: "/praias", label: "Praias", icon: MapPin },
  { to: "/guarda-vidas", label: "Guarda-Vidas", icon: Shield },
  { to: "/mares", label: "Marés", icon: Moon },
  { to: "/aprender", label: "Aprender", icon: BookOpen },
  { to: "/sobre", label: "Sobre", icon: Info },
];

export interface HomeOutletContext {
  readonly query: string;
  readonly onSelectBeach: (beachId: string) => void;
}

export function RootLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const user = useCurrentUser();
  const isHome = pathname === "/";
  const isSosRoute = pathname.startsWith("/sos");

  const [showRisks, setShowRisks] = useState(true);
  const [showMarkers, setShowMarkers] = useState(true);
  const [showGraticule, setShowGraticule] = useState(false);
  const [selectedMarkerId, setSelectedMarkerId] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);

  const isPanelExpanded = !isHome || searchOpen;

  // Composição fica no app: o mapa recebe só marcadores genéricos.
  // Praia não tem marcador; ela é acessada pela lista e pela ficha.
  const markers = useMemo<readonly MapMarker[]>(
    () => [
      ...lifeguardStations.map((station) => ({
        id: `${STATION_PREFIX}${station.id}`,
        kind:
          station.type === "MOBILE"
            ? ("STATION_MOBILE" as const)
            : ("STATION_FIXED" as const),
        latitude: station.latitude,
        longitude: station.longitude,
        label: station.name,
      })),
      ...beachFlagSignals.map((signal) => ({
        id: `${SIGNAL_PREFIX}${signal.id}`,
        kind: "SIGNAL" as const,
        latitude: signal.latitude,
        longitude: signal.longitude,
        label: signal.beachName,
        color: FLAG_COLORS[signal.flag],
        hasPhoto: signal.photoUrls.length > 0,
      })),
    ],
    [],
  );

  const riskZones = useMemo(() => riskZonesOf(beaches), []);

  const selectedBeach = useMemo(() => {
    if (!selectedMarkerId?.startsWith(BEACH_PREFIX)) return null;
    const id = selectedMarkerId.slice(BEACH_PREFIX.length);
    return beaches.find((beach) => beach.id === id) ?? null;
  }, [selectedMarkerId]);

  const selectedSignal = useMemo(() => {
    if (!selectedMarkerId?.startsWith(SIGNAL_PREFIX)) return null;
    const id = selectedMarkerId.slice(SIGNAL_PREFIX.length);
    return beachFlagSignals.find((signal) => signal.id === id) ?? null;
  }, [selectedMarkerId]);

  const selectedStation = useMemo(() => {
    if (!selectedMarkerId?.startsWith(STATION_PREFIX)) return null;
    const id = selectedMarkerId.slice(STATION_PREFIX.length);
    return lifeguardStations.find((station) => station.id === id) ?? null;
  }, [selectedMarkerId]);

  const outletContext = useMemo<HomeOutletContext>(
    () => ({
      query: searchQuery,
      onSelectBeach: (beachId) => {
        setSelectedMarkerId(`${BEACH_PREFIX}${beachId}`);
        setSearchOpen(false);
      },
    }),
    [searchQuery],
  );

  const handleClosePanel = () => {
    setSearchOpen(false);
    if (!isHome) {
      navigate("/");
    }
  };

  return (
    <div className="relative h-dvh w-screen overflow-hidden bg-ocean-100 text-ocean-950">
      <div className="pointer-events-auto absolute inset-x-0 top-0 z-50">
        <OfflineBanner />
      </div>

      <div className="absolute inset-0 z-0">
        <Suspense fallback={<MapFallback />}>
          <MapView
            markers={markers}
            selectedMarkerId={selectedMarkerId}
            onSelectMarker={(id) => {
              setSelectedMarkerId(id);
              if (id) {
                setSearchOpen(false);
              }
            }}
            riskZones={riskZones}
            showRisks={showRisks}
            showMarkers={showMarkers}
            showGraticule={showGraticule}
          />
        </Suspense>
      </div>

      <div className="pointer-events-none absolute inset-x-3 top-3 z-30 flex max-h-[calc(100dvh-1.5rem)] flex-col gap-2 sm:left-4 sm:right-auto sm:w-104">
        <div className="pointer-events-auto flex shrink-0 items-center gap-1.5 rounded-2xl border border-ocean-900/10 bg-white/95 p-1.5 shadow-xl backdrop-blur">
          <div className="relative flex flex-1 items-center pl-1">
            <Search
              aria-hidden="true"
              className="pointer-events-none size-4 shrink-0 text-ocean-500"
            />
            <input
              type="search"
              value={searchQuery}
              maxLength={80}
              onFocus={() => setSearchOpen(true)}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setSearchOpen(true);
              }}
              placeholder="Buscar no MarAberto..."
              aria-label="Buscar praia ou município"
              className="h-10 w-full bg-transparent pl-2 pr-6 text-sm font-medium text-ocean-950 placeholder:text-ocean-400 focus:outline-none"
            />
            {searchQuery ? (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                aria-label="Limpar busca"
                className="absolute right-1 grid size-6 place-items-center rounded-full text-ocean-400 hover:bg-ocean-50 hover:text-ocean-700"
              >
                <X aria-hidden="true" className="size-3.5" />
              </button>
            ) : null}
          </div>

          <button
            type="button"
            onClick={() => {
              if (isPanelExpanded) {
                handleClosePanel();
              } else {
                setSearchOpen(true);
              }
            }}
            aria-label={
              isPanelExpanded
                ? "Recolher aba flutuante"
                : "Expandir aba flutuante"
            }
            aria-expanded={isPanelExpanded}
            className="grid size-10 shrink-0 place-items-center rounded-xl text-ocean-700 transition hover:bg-ocean-50"
          >
            {isPanelExpanded ? (
              <ChevronUp aria-hidden="true" className="size-5" />
            ) : (
              <ChevronDown aria-hidden="true" className="size-5" />
            )}
          </button>

          <Link
            to="/sos"
            className="inline-flex h-10 shrink-0 items-center gap-1.5 rounded-xl bg-coral-600 px-3 text-xs font-black text-white shadow-sm transition hover:bg-coral-500"
          >
            <Siren aria-hidden="true" className="size-4" />
            SOS
          </Link>
        </div>

        <div className="pointer-events-auto flex shrink-0 flex-wrap items-center gap-1.5">
          {quickTabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <NavLink
                key={tab.to}
                to={tab.to}
                className={({ isActive }) =>
                  [
                    "inline-flex min-h-8 items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold shadow-md backdrop-blur transition",
                    isActive
                      ? "border-ocean-950 bg-ocean-950 text-white"
                      : "border-ocean-900/10 bg-white/95 text-ocean-900 hover:bg-white",
                  ].join(" ")
                }
              >
                <Icon aria-hidden="true" className="size-3.5" />
                {tab.label}
              </NavLink>
            );
          })}

          <ChipToggle
            label="Riscos"
            on={showRisks}
            onToggle={() => setShowRisks((v) => !v)}
          />
          <ChipToggle
            label="Pins"
            on={showMarkers}
            onToggle={() => setShowMarkers((v) => !v)}
          />
          <ChipToggle
            label="Grade"
            on={showGraticule}
            onToggle={() => setShowGraticule((v) => !v)}
          />

          {/* Acesso às áreas restritas: um login só, área definida pelo papel. */}
          <Link
            to={user ? homePathForRole(user.role) : "/login"}
            className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-ocean-300 bg-white/95 px-3 py-1 text-xs font-bold text-ocean-900 shadow-md backdrop-blur transition hover:bg-white"
          >
            <LogIn aria-hidden="true" className="size-3.5" />
            {user ? "Minha área" : "Entrar"}
          </Link>
        </div>

        {isPanelExpanded ? (
          <section
            aria-label="Painel de conteúdo flutuante"
            className="pointer-events-auto flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-ocean-900/10 bg-white/95 shadow-2xl backdrop-blur"
          >
            <div className="flex shrink-0 items-center justify-between border-b border-ocean-100 px-4 py-3">
              <Link
                to="/"
                onClick={() => setSearchOpen(false)}
                className="flex items-center gap-2"
              >
                <img
                  src="/brand/mark-96.png"
                  alt=""
                  aria-hidden="true"
                  className="size-7 rounded-lg"
                />
                <span className="text-sm font-black text-ocean-950">
                  MarAberto
                </span>
              </Link>

              <button
                type="button"
                onClick={handleClosePanel}
                aria-label="Fechar painel e voltar ao mapa"
                className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-bold text-ocean-600 hover:bg-ocean-50"
              >
                <span>Minimizar</span>
                <X aria-hidden="true" className="size-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4">
              <Outlet context={outletContext} />
            </div>

            <div className="flex shrink-0 items-center justify-between gap-2 border-t border-ocean-100 bg-sand-50/60 px-4 py-2.5">
              <a
                href="tel:193"
                className="inline-flex min-h-9 flex-1 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-ocean-900"
              >
                <Phone aria-hidden="true" className="size-3.5" />
                Ligar 193
              </a>
              {!isSosRoute ? (
                <Link
                  to="/sos"
                  className="inline-flex min-h-9 flex-1 items-center justify-center gap-1.5 rounded-xl bg-coral-600 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-coral-500"
                >
                  <Siren aria-hidden="true" className="size-3.5" />
                  Abrir SOS
                </Link>
              ) : null}
            </div>
          </section>
        ) : null}
      </div>

      <div className="pointer-events-none absolute right-14 top-3 z-20 hidden max-w-sm md:block">
        <div className="pointer-events-auto">
          <InstallPrompt />
        </div>
      </div>

      {/* Canto inferior esquerdo: aviso do 193 e, logo abaixo, a legenda. */}
      <div className="pointer-events-none absolute bottom-3 left-3 z-20 flex max-w-[calc(100vw-4.5rem)] flex-col items-start gap-2">
        <p className="max-w-68 rounded-xl border border-amber-300 bg-amber-50/95 px-3 py-2 text-xs font-semibold text-amber-950 shadow-md backdrop-blur">
          {DEMO_DATA_NOTICE}
        </p>
        <MapLegend className="pointer-events-auto" />
      </div>

      <div className="pointer-events-none absolute inset-0 z-40">
        {selectedBeach ? (
          <BeachSheet
            beach={selectedBeach}
            onClose={() => setSelectedMarkerId(null)}
          />
        ) : null}

        {selectedSignal ? (
          <SignalSheet
            signal={selectedSignal}
            onClose={() => setSelectedMarkerId(null)}
          />
        ) : null}

        {selectedStation ? (
          <StationSheet
            station={selectedStation}
            onClose={() => setSelectedMarkerId(null)}
          />
        ) : null}
      </div>

      {!isSosRoute ? <SosFab /> : null}
    </div>
  );
}

interface ChipToggleProps {
  readonly label: string;
  readonly on: boolean;
  readonly onToggle: () => void;
}

function ChipToggle({ label, on, onToggle }: ChipToggleProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={on}
      className={[
        "min-h-8 rounded-full border px-3 py-1 text-xs font-bold shadow-md backdrop-blur transition",
        on
          ? "border-ocean-700 bg-ocean-700 text-white"
          : "border-ocean-900/10 bg-white/95 text-ocean-800 hover:bg-white",
      ].join(" ")}
    >
      {label}
    </button>
  );
}
