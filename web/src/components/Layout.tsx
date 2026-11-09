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
  MapPin,
  Moon,
  Phone,
  Search,
  Siren,
  Waves,
  X,
} from "lucide-react";
import { OfflineBanner } from "./OfflineBanner";
import { AppMenu } from "./AppMenu";
import { SosFab } from "./SosFab";
import { BeachSheet } from "./BeachSheet";
import { InstallPrompt } from "./InstallPrompt";
import { FlagBadge, RiskBadge } from "./Badges";
import { DEMO_DATA_NOTICE, beaches } from "../data/demo-data";
import { sanitizeText } from "../lib/sanitize";

const MapView = lazy(() =>
  import("./map/MapView").then((m) => ({ default: m.MapView })),
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

const quickTabs = [
  { to: "/praias", label: "Praias", icon: MapPin },
  { to: "/mares", label: "Marés", icon: Moon },
  { to: "/aprender", label: "Aprender", icon: BookOpen },
  { to: "/sobre", label: "Sobre", icon: Info },
];

export function Layout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const isHome = pathname === "/";
  const isSosRoute = pathname.startsWith("/sos");

  const [showRisks, setShowRisks] = useState(true);
  const [showMarkers, setShowMarkers] = useState(true);
  const [showGraticule, setShowGraticule] = useState(false);
  const [selectedBeachId, setSelectedBeachId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // O painel flutuante fica aberto se estiver em qualquer sub-rota (/praias, /mares, /sos, etc.)
  // ou se o usuário expandiu a busca manualmente na Home (/).
  const isPanelExpanded = !isHome || searchOpen;

  const selectedBeach = useMemo(
    () => beaches.find((beach) => beach.id === selectedBeachId) ?? null,
    [selectedBeachId],
  );

  const filteredBeaches = useMemo(() => {
    const term = sanitizeText(searchQuery, 80).toLocaleLowerCase("pt-BR");
    if (!term) return beaches;
    return beaches.filter((beach) =>
      `${beach.name} ${beach.municipality} ${beach.state}`
        .toLocaleLowerCase("pt-BR")
        .includes(term),
    );
  }, [searchQuery]);

  const handleClosePanel = () => {
    setSearchOpen(false);
    if (!isHome) {
      navigate("/");
    }
  };

  return (
    <div className="relative h-dvh w-screen overflow-hidden bg-ocean-100 text-ocean-950">
      {/* Banner offline fixo no topo se perder conexão */}
      <div className="pointer-events-auto absolute inset-x-0 top-0 z-50">
        <OfflineBanner />
      </div>

      {/* Mapa 100% em tela cheia (sempre vivo em todas as rotas) */}
      <div className="absolute inset-0 z-0">
        <Suspense fallback={<MapFallback />}>
          <MapView
            beaches={beaches}
            showRisks={showRisks}
            showMarkers={showMarkers}
            showGraticule={showGraticule}
            selectedBeachId={selectedBeachId}
            onSelectBeach={(id) => {
              setSelectedBeachId(id);
              if (id) setSearchOpen(false);
            }}
          />
        </Suspense>
      </div>

      {/* Coluna Flutuante Estilo Google Maps (Canto Superior Esquerdo) */}
      <div className="pointer-events-none absolute inset-x-3 top-3 z-30 flex max-h-[calc(100dvh-1.5rem)] flex-col gap-2 sm:left-4 sm:right-auto sm:w-104">
        {/* Barra de Busca e Navegação Principal */}
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

        {/* Barra de Abas Rápidas + Filtros de Camadas */}
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
        </div>

        {/* Aba / Gaveta Flutuante estilo Google Maps (renderiza busca na Home ou o <Outlet /> das rotas) */}
        {isPanelExpanded ? (
          <section
            aria-label="Painel de conteúdo flutuante"
            className="pointer-events-auto flex min-h-0 flex-1 flex-col overflow-hidden rounded-2xl border border-ocean-900/10 bg-white/95 shadow-2xl backdrop-blur"
          >
            {/* Cabeçalho fixo do card flutuante */}
            <div className="flex shrink-0 items-center justify-between border-b border-ocean-100 px-4 py-3">
              <Link
                to="/"
                onClick={() => setSearchOpen(false)}
                className="flex items-center gap-2"
              >
                <span className="grid size-7 place-items-center rounded-lg bg-ocean-950 text-white">
                  <Waves aria-hidden="true" className="size-4" />
                </span>
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

            {/* Corpo rolável da aba flutuante */}
            <div className="flex-1 overflow-y-auto p-4">
              {isHome ? (
                <div>
                  <h2 className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-ocean-500">
                    <span>Praias mapeadas</span>
                    <span>{filteredBeaches.length}</span>
                  </h2>

                  {filteredBeaches.length === 0 ? (
                    <div className="mt-2 rounded-xl border border-dashed border-ocean-200 bg-ocean-50/40 p-4 text-center">
                      <p className="text-xs font-bold text-ocean-900">
                        Nenhuma praia carregada no momento
                      </p>
                      <p className="mt-0.5 text-[11px] text-ocean-600">
                        Os dados reais do KML aparecerão aqui assim que
                        importados.
                      </p>
                    </div>
                  ) : (
                    <ul className="mt-2 grid gap-2">
                      {filteredBeaches.map((beach) => (
                        <li key={beach.id}>
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedBeachId(beach.id);
                              setSearchOpen(false);
                            }}
                            className="w-full rounded-xl border border-ocean-100 bg-white p-3 text-left transition hover:border-ocean-300 hover:bg-ocean-50/40"
                          >
                            <div className="flex items-start justify-between gap-2">
                              <div>
                                <p className="font-bold text-ocean-950">
                                  {beach.name}
                                </p>
                                <p className="text-xs text-ocean-600">
                                  {beach.municipality} · {beach.state}
                                </p>
                              </div>
                              <RiskBadge level={beach.riskLevel} />
                            </div>
                            <div className="mt-2">
                              <FlagBadge flag={beach.flag} />
                            </div>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ) : (
                /* Aqui entram Praias, Detalhe da Praia, Marés, Aprender, Sobre e SOS flutuando sobre o mapa! */
                <Outlet />
              )}
            </div>

            {/* Rodapé rápido do painel flutuante */}
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

      {/* Convite de instalação PWA no canto superior direito */}
      <div className="pointer-events-none absolute right-14 top-3 z-20 hidden max-w-sm md:block">
        <div className="pointer-events-auto">
          <InstallPrompt />
        </div>
      </div>

      {/* Aviso fixo mantido no canto inferior esquerdo */}
      <p className="pointer-events-auto absolute bottom-4 left-3 z-20 max-w-68 rounded-xl border border-amber-300 bg-amber-50/95 px-3 py-2 text-xs font-semibold text-amber-950 shadow-md backdrop-blur">
        {DEMO_DATA_NOTICE}
      </p>

      {/* Ficha rápida quando um marcador é clicado no mapa */}
      {selectedBeach ? (
        <BeachSheet
          beach={selectedBeach}
          onClose={() => setSelectedBeachId(null)}
        />
      ) : null}

      {/* Botão flutuante de SOS no canto inferior direito */}
      {!isSosRoute ? <SosFab /> : null}

      {/* Menu gaveta completo */}
      <AppMenu open={drawerOpen} onClose={() => setDrawerOpen(false)} />
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
