import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import type { FeatureCollection, Polygon } from "geojson";
import {
  ExternalLink,
  MapPin,
  MapPinned,
  Siren,
  Users,
  X,
} from "lucide-react";
import { MapView } from "@/features/map";
import type { MapFocus, MapMarker } from "@/features/map";
import { FLAG_COLORS } from "@/shared/lib/flags";
import { formatAccuracy, formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useBathingAreasAvailability } from "@/features/management/hooks/use-bathing-areas";
import { useManagementTeam } from "@/features/management/hooks/use-management-team";
import type { TeamMember } from "@/features/management/types";
import { StationSheet, usePublicStations } from "@/features/stations";
import { SignalSheet, useSignals } from "@/features/signals";
import { OccurrenceSheet, useOccurrences } from "@/features/occurrences";

const LIFEGUARD_PREFIX = "lifeguard:";
const STATION_PREFIX = "station:";
const SIGNAL_PREFIX = "signal:";
const OCCURRENCE_PREFIX = "occurrence:";

/** O mapa de situação não desenha zonas de risco nem geometria de praia. */
const EMPTY_ZONES: FeatureCollection<Polygon> = {
  type: "FeatureCollection",
  features: [],
};

const LAYERS = [
  {
    id: "lifeguards",
    label: "Guarda-vidas em plantão",
    hint: "posição atualizada a cada 60 s",
    icon: Users,
  },
  {
    id: "stations",
    label: "Postos e bases ativos",
    hint: "estruturas visíveis no mapa público",
    icon: MapPin,
  },
  {
    id: "signals",
    label: "Sinalizações vigentes",
    hint: "bandeira dentro da validade",
    icon: MapPin,
  },
  {
    id: "occurrences",
    label: "Ocorrências ativas",
    hint: "em atendimento ou CIOSP pendente",
    icon: Siren,
  },
] as const;

type LayerId = (typeof LAYERS)[number]["id"];

const ALL_LAYERS: Record<LayerId, boolean> = {
  lifeguards: true,
  stations: true,
  signals: true,
  occurrences: true,
};

/**
 * Mapa de situação da coordenação (RF40/ADR-026).
 *
 * <p>A home de `/gestao` é um mapa de tela cheia com camadas ligáveis em
 * memória: guarda-vidas em plantão (posição de até 60 s), postos/bases ativos,
 * sinalizações vigentes e ocorrências que ainda pedem ação. Todo pin e todo
 * item da lista lateral centralizam o mapa e abrem a ficha já existente.
 *
 * <p>A posição do operador só existe nesta rota autenticada e `no-store`; o
 * cliente não guarda coordenada em storage. As camadas valem para a sessão e
 * não são persistidas. Aba escondida não consulta nada.
 */
export function ManagementSituationPage() {
  const [layers, setLayers] = useState<Record<LayerId, boolean>>(ALL_LAYERS);
  const [selectedMarkerId, setSelectedMarkerId] = useState<string | null>(null);
  const [selectedShiftId, setSelectedShiftId] = useState<string | null>(null);
  const [focus, setFocus] = useState<MapFocus | null>(null);
  const initialFit = useRef(false);

  const team = useManagementTeam();
  const { stations } = usePublicStations();
  const { signals } = useSignals();
  const occurrences = useOccurrences({ active: true, pageSize: 100 });
  const areas = useBathingAreasAvailability();

  const markers = useMemo<readonly MapMarker[]>(() => {
    const result: MapMarker[] = [];

    if (layers.lifeguards) {
      for (const member of team.team?.members ?? []) {
        if (!member.lastPosition) continue;
        result.push({
          id: `${LIFEGUARD_PREFIX}${member.shiftId}`,
          kind: "LIFEGUARD",
          latitude: member.lastPosition.latitude,
          longitude: member.lastPosition.longitude,
          label: member.name,
          color: member.lastPosition.stale ? "#b45309" : "#0f766e",
        });
      }
    }

    if (layers.stations) {
      for (const station of stations) {
        result.push({
          id: `${STATION_PREFIX}${station.id}`,
          kind: station.kind === "MOBILE" ? "STATION_MOBILE" : "STATION_FIXED",
          latitude: station.latitude,
          longitude: station.longitude,
          label: station.name,
        });
      }
    }

    if (layers.signals) {
      for (const signal of signals) {
        result.push({
          id: `${SIGNAL_PREFIX}${signal.id}`,
          kind: "SIGNAL",
          latitude: signal.latitude,
          longitude: signal.longitude,
          label: signal.sectorLabel
            ? `${signal.beachName} · ${signal.sectorLabel}`
            : signal.beachName,
          color: FLAG_COLORS[signal.flag],
          hasPhoto: signal.photoUrls.length > 0,
        });
      }
    }

    if (layers.occurrences) {
      for (const occurrence of occurrences.occurrences) {
        if (occurrence.latitude === null || occurrence.longitude === null) {
          continue;
        }
        result.push({
          id: `${OCCURRENCE_PREFIX}${occurrence.id}`,
          kind: "OCCURRENCE",
          latitude: occurrence.latitude,
          longitude: occurrence.longitude,
          label: `Ocorrência ${occurrence.number}`,
        });
      }
    }

    return result;
  }, [layers, team.team, stations, signals, occurrences.occurrences]);

  const markersById = useMemo(
    () => new Map(markers.map((marker) => [marker.id, marker])),
    [markers],
  );

  // Primeira carga: enquadra o escopo em vez de abrir o Brasil inteiro.
  useEffect(() => {
    if (initialFit.current || focus !== null || markers.length === 0) return;
    initialFit.current = true;
    const latitudes = markers.map((marker) => marker.latitude);
    const longitudes = markers.map((marker) => marker.longitude);
    const minLat = Math.min(...latitudes);
    const maxLat = Math.max(...latitudes);
    const minLon = Math.min(...longitudes);
    const maxLon = Math.max(...longitudes);
    setFocus({
      kind: "SCOPE",
      id: "initial-scope",
      bbox: [
        minLon === maxLon ? minLon - 0.02 : minLon,
        minLat === maxLat ? minLat - 0.02 : minLat,
        minLon === maxLon ? maxLon + 0.02 : maxLon,
        minLat === maxLat ? maxLat + 0.02 : maxLat,
      ],
    });
  }, [focus, markers]);

  const selectedMember = useMemo(() => {
    if (!selectedShiftId) return null;
    return (
      team.team?.members.find((member) => member.shiftId === selectedShiftId) ??
      null
    );
  }, [selectedShiftId, team.team]);

  const selectedStation = useMemo(() => {
    if (!selectedMarkerId?.startsWith(STATION_PREFIX)) return null;
    const id = selectedMarkerId.slice(STATION_PREFIX.length);
    return stations.find((station) => station.id === id) ?? null;
  }, [selectedMarkerId, stations]);

  const selectedSignal = useMemo(() => {
    if (!selectedMarkerId?.startsWith(SIGNAL_PREFIX)) return null;
    const id = selectedMarkerId.slice(SIGNAL_PREFIX.length);
    return signals.find((signal) => signal.id === id) ?? null;
  }, [selectedMarkerId, signals]);

  const selectedOccurrenceId = useMemo(() => {
    if (!selectedMarkerId?.startsWith(OCCURRENCE_PREFIX)) return null;
    return selectedMarkerId.slice(OCCURRENCE_PREFIX.length);
  }, [selectedMarkerId]);

  const clearSelection = () => {
    setSelectedMarkerId(null);
    setSelectedShiftId(null);
  };

  const focusOnMarker = (marker: MapMarker) => {
    setFocus({
      kind: "POINT",
      id: marker.id,
      latitude: marker.latitude,
      longitude: marker.longitude,
      zoom: 15,
    });
  };

  const selectMarker = (id: string | null) => {
    if (!id) {
      clearSelection();
      return;
    }
    setSelectedMarkerId(id);

    if (id.startsWith(LIFEGUARD_PREFIX)) {
      setSelectedShiftId(id.slice(LIFEGUARD_PREFIX.length));
    } else {
      setSelectedShiftId(null);
    }

    const marker = markersById.get(id);
    if (marker) focusOnMarker(marker);
  };

  const selectMember = (member: TeamMember) => {
    setSelectedShiftId(member.shiftId);
    if (!member.lastPosition) {
      setSelectedMarkerId(null);
      return;
    }
    const markerId = `${LIFEGUARD_PREFIX}${member.shiftId}`;
    setSelectedMarkerId(markerId);
    focusOnMarker({
      id: markerId,
      kind: "LIFEGUARD",
      latitude: member.lastPosition.latitude,
      longitude: member.lastPosition.longitude,
      label: member.name,
    });
  };

  const toggleLayer = (id: LayerId) => {
    setLayers((current) => ({ ...current, [id]: !current[id] }));
  };

  const showImportInvite = areas.status === "ready" && areas.isEmpty;

  return (
    <div className="relative h-full min-h-[32rem] overflow-hidden rounded-2xl border border-ocean-900/10 bg-ocean-100">
      <div className="absolute inset-0">
        <MapView
          markers={markers}
          selectedMarkerId={selectedMarkerId}
          onSelectMarker={selectMarker}
          riskZones={EMPTY_ZONES}
          showRisks={false}
          showMarkers
          showGraticule={false}
          beachArea={null}
          beachSectors={null}
          focus={focus}
        />
      </div>

      {/* Camadas: estado só em memória (nada vai para storage). */}
      <section
        aria-label="Camadas do mapa de situação"
        className="pointer-events-auto absolute left-3 top-3 z-20 w-60 rounded-2xl border border-ocean-900/10 bg-white/95 p-2.5 shadow-xl backdrop-blur"
      >
        <h2 className="px-1 text-[10px] font-bold uppercase tracking-wider text-ocean-500">
          Camadas
        </h2>
        <ul className="mt-1 grid gap-0.5">
          {LAYERS.map((layer) => {
            const Icon = layer.icon;
            return (
              <li key={layer.id}>
                <button
                  type="button"
                  aria-pressed={layers[layer.id]}
                  onClick={() => toggleLayer(layer.id)}
                  className={[
                    "flex w-full items-center gap-2 rounded-xl px-2 py-1.5 text-left transition",
                    layers[layer.id]
                      ? "bg-ocean-950 text-white"
                      : "text-ocean-700 hover:bg-ocean-50",
                  ].join(" ")}
                >
                  <Icon aria-hidden="true" className="size-3.5 shrink-0" />
                  <span className="min-w-0">
                    <span className="block truncate text-[11px] font-bold">
                      {layer.label}
                    </span>
                    <span
                      className={[
                        "block truncate text-[10px]",
                        layers[layer.id] ? "text-ocean-200" : "text-ocean-500",
                      ].join(" ")}
                    >
                      {layer.hint}
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
        <p className="mt-1.5 px-1 text-[10px] leading-snug text-ocean-500">
          A posição do plantão é a última conhecida (RN17) e pode ter até 60 s
          de atraso.
        </p>
      </section>

      <TeamPanel
        members={team.team?.members ?? []}
        generatedAt={team.team?.generatedAt ?? null}
        message={team.message}
        selectedShiftId={selectedShiftId}
        selectedMember={selectedMember}
        onSelect={selectMember}
        onCloseMember={() => {
          setSelectedShiftId(null);
          setSelectedMarkerId(null);
        }}
      />

      {showImportInvite ? (
        <div className="pointer-events-auto absolute inset-0 z-30 grid place-items-center p-4">
          <div className="max-w-sm rounded-2xl border border-ocean-900/10 bg-white/95 p-5 text-center shadow-2xl backdrop-blur">
            <MapPinned
              aria-hidden="true"
              className="mx-auto size-8 text-ocean-600"
            />
            <h2 className="mt-2 text-lg font-black text-ocean-950">
              Nenhuma área de banho mapeada
            </h2>
            <p className="mt-1 text-sm text-ocean-700">
              O mapa de situação precisa do mapeamento importado para mostrar
              postos, sinalizações e ocorrências no lugar certo.
            </p>
            <Link
              to="/gestao/importar"
              className="mt-3 inline-flex min-h-11 items-center justify-center rounded-xl bg-ocean-950 px-4 text-sm font-bold text-white transition hover:bg-ocean-900"
            >
              Importar mapa
            </Link>
          </div>
        </div>
      ) : null}

      <div className="pointer-events-none absolute inset-0 z-40">
        {selectedStation ? (
          <StationSheet
            station={selectedStation}
            onClose={() => setSelectedMarkerId(null)}
          />
        ) : null}

        {selectedSignal ? (
          <SignalSheet
            signal={selectedSignal}
            onClose={() => setSelectedMarkerId(null)}
          />
        ) : null}

        {selectedOccurrenceId ? (
          <OccurrenceSheet
            occurrenceId={selectedOccurrenceId}
            onClose={() => setSelectedMarkerId(null)}
            onChanged={() => {
              occurrences.reload();
              team.reload();
            }}
          />
        ) : null}
      </div>
    </div>
  );
}

interface TeamPanelProps {
  readonly members: readonly TeamMember[];
  readonly generatedAt: string | null;
  readonly message: string | null;
  readonly selectedShiftId: string | null;
  readonly selectedMember: TeamMember | null;
  readonly onSelect: (member: TeamMember) => void;
  readonly onCloseMember: () => void;
}

/**
 * Lista lateral de equipe: é o índice do mapa. Clicar no nome centraliza no
 * guarda-vidas e abre os detalhes; a ordem vem do servidor (sem sinal há mais
 * tempo primeiro).
 */
function TeamPanel({
  members,
  generatedAt,
  message,
  selectedShiftId,
  selectedMember,
  onSelect,
  onCloseMember,
}: TeamPanelProps) {
  return (
    <section
      aria-label="Equipe em plantão"
      className="pointer-events-auto absolute inset-x-2 bottom-2 z-20 flex max-h-[42dvh] flex-col rounded-2xl border border-ocean-900/10 bg-white/95 shadow-xl backdrop-blur md:bottom-auto md:inset-x-auto md:right-3 md:top-3 md:max-h-[calc(100%-1.5rem)] md:w-84"
    >
      <header className="flex shrink-0 items-center justify-between gap-2 border-b border-ocean-100 px-3 py-2">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
            Equipe em plantão
          </p>
          <p className="text-xs font-bold text-ocean-950">
            {members.length} {members.length === 1 ? "operador" : "operadores"}
          </p>
        </div>
        <p className="text-right text-[10px] text-ocean-500">
          {generatedAt ? `Lido às ${formatTime(generatedAt)}` : "Consultando"}
        </p>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        {selectedMember ? (
          <MemberDetail member={selectedMember} onClose={onCloseMember} />
        ) : null}

        {message ? (
          <p
            role="alert"
            className="mb-1.5 rounded-xl border border-amber-300 bg-amber-50 px-2.5 py-1.5 text-[11px] font-semibold text-amber-950"
          >
            {sanitizeText(message, 160)}
          </p>
        ) : null}

        {members.length === 0 ? (
          <p className="px-1 py-3 text-center text-xs text-ocean-600">
            Nenhum plantão aberto agora. Quem abrir o console aparece aqui em
            até 15 s.
          </p>
        ) : (
          <ul className="grid gap-1">
            {members.map((member) => (
              <li key={member.shiftId}>
                <button
                  type="button"
                  onClick={() => onSelect(member)}
                  aria-pressed={member.shiftId === selectedShiftId}
                  className={[
                    "grid w-full gap-1 rounded-xl border px-2.5 py-2 text-left transition",
                    member.shiftId === selectedShiftId
                      ? "border-ocean-950 bg-ocean-950 text-white"
                      : "border-ocean-100 bg-white text-ocean-950 hover:border-ocean-300 hover:bg-ocean-50",
                  ].join(" ")}
                >
                  <span className="flex items-center justify-between gap-2">
                    <span className="truncate text-xs font-bold">
                      {sanitizeText(member.name, 80)}
                    </span>
                    <SignalBadge member={member} />
                  </span>
                  <span
                    className={[
                      "truncate text-[10px]",
                      member.shiftId === selectedShiftId
                        ? "text-ocean-200"
                        : "text-ocean-600",
                    ].join(" ")}
                  >
                    {[
                      member.stationName,
                      member.beachName,
                      member.sectorLabel,
                    ]
                      .filter(Boolean)
                      .join(" · ") || "Sem estrutura"}
                  </span>
                  <span
                    className={[
                      "text-[10px]",
                      member.shiftId === selectedShiftId
                        ? "text-ocean-300"
                        : "text-ocean-500",
                    ].join(" ")}
                  >
                    {member.lastPosition
                      ? member.lastPosition.stale
                        ? `Sem sinal há ${relativeTime(member.lastPosition.at)}`
                        : `Posição ${relativeTime(member.lastPosition.at)}`
                      : "Sem posição no turno"}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

function MemberDetail({
  member,
  onClose,
}: {
  readonly member: TeamMember;
  readonly onClose: () => void;
}) {
  return (
    <div className="mb-2 rounded-xl border border-ocean-200 bg-ocean-50/70 p-2.5">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-xs font-black text-ocean-950">
            {sanitizeText(member.name, 80)}
          </p>
          <p className="text-[10px] text-ocean-600">
            {member.badgeNumber ?? "Sem matrícula"} · plantão desde{" "}
            {formatTime(member.onShiftSince)}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Fechar detalhes do operador"
          className="grid size-6 shrink-0 place-items-center rounded-lg text-ocean-600 hover:bg-white"
        >
          <X aria-hidden="true" className="size-3.5" />
        </button>
      </div>

      {member.lastPosition ? (
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[10px] text-ocean-700">
          <span className="font-mono font-bold text-ocean-900">
            {member.lastPosition.latitude.toFixed(5)},{" "}
            {member.lastPosition.longitude.toFixed(5)}
          </span>
          <span>
            {formatDateTime(member.lastPosition.at ?? member.lastSeenAt)} ·{" "}
            {formatAccuracy(member.lastPosition.accuracyMeters ?? undefined)}
          </span>
          <a
            href={`https://www.google.com/maps/search/?api=1&query=${member.lastPosition.latitude},${member.lastPosition.longitude}`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 rounded-md border border-ocean-200 bg-white px-1.5 py-0.5 font-bold text-ocean-800 transition hover:bg-ocean-100"
          >
            <ExternalLink aria-hidden="true" className="size-3" />
            GPS
          </a>
        </div>
      ) : (
        <p className="mt-1.5 text-[10px] text-ocean-500">
          O console deste operador não enviou posição no turno atual.
        </p>
      )}

      <div className="mt-1.5 flex flex-wrap gap-1 text-[10px] font-bold">
        <Counter label="ocorrências" value={member.occurrencesToday} />
        <Counter label="em campo" value={member.fieldAttendedToday} />
        <Counter label="sinalizações" value={member.signalsToday} />
        <Counter label="CIOSP" value={member.ciospPending} />
      </div>
    </div>
  );
}

function SignalBadge({ member }: { member: TeamMember }) {
  const tone = !member.lastPosition
    ? "border-ocean-300 bg-ocean-100 text-ocean-700"
    : member.lastPosition.stale
      ? "border-amber-300 bg-amber-100 text-amber-900"
      : "border-emerald-300 bg-emerald-100 text-emerald-900";
  const label = !member.lastPosition
    ? "Sem GPS"
    : member.lastPosition.stale
      ? "Sem sinal"
      : "Em linha";
  return (
    <span
      className={`shrink-0 rounded-full border px-1.5 py-0.5 text-[9px] font-bold ${tone}`}
    >
      {label}
    </span>
  );
}

function Counter({ label, value }: { label: string; value: number }) {
  return (
    <span className="rounded-full border border-ocean-200 bg-white px-1.5 py-0.5 text-ocean-900">
      {value} {label}
    </span>
  );
}

/** `HH:mm` local; a lista usa o mesmo formato para todos os instantes. */
function formatTime(value: string): string {
  return new Date(value).toLocaleTimeString("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** Diferença legível a partir de um instante ISO; "agora" abaixo de 1 min. */
function relativeTime(value: string | null): string {
  if (!value) return "instantes";
  const elapsedMinutes = Math.max(
    0,
    Math.round((Date.now() - new Date(value).getTime()) / 60_000),
  );
  if (elapsedMinutes < 1) return "agora";
  if (elapsedMinutes < 60) return `${elapsedMinutes} min`;
  const hours = Math.floor(elapsedMinutes / 60);
  const minutes = elapsedMinutes % 60;
  return minutes === 0 ? `${hours} h` : `${hours} h ${minutes} min`;
}
