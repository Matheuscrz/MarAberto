import { useMemo, useState } from "react";
import {
  ClipboardList,
  ExternalLink,
  MapPin,
  Power,
  Radio,
  Siren,
  UserCheck,
  UserX,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { formatAccuracy, formatDateTime } from "@/shared/lib/format";
import type { TeamMember, TeamMemberStatus } from "@/features/management/types";

const INITIAL_TEAM: readonly TeamMember[] = [
  {
    id: "team-1",
    name: "Sgt. Silva",
    badgeNumber: "BM-10492",
    stationName: "Posto Principal",
    beachName: "Praia Principal",
    sector: "Setor Central",
    shift: "08:00 às 17:00",
    status: "ACTIVE",
    onShiftSince: new Date(Date.now() - 3 * 60 * 60_000).toISOString(),
    lastPosition: {
      latitude: -10.98521,
      longitude: -37.04512,
      accuracyMeters: 12,
      at: new Date(Date.now() - 4 * 60_000).toISOString(),
    },
    occurrencesToday: 3,
    fieldAttendedToday: 2,
    signalsToday: 1,
    ciospPending: 1,
    updatedAt: new Date(Date.now() - 42 * 60_000).toISOString(),
  },
  {
    id: "team-2",
    name: "Cb. Rocha",
    badgeNumber: "BM-20871",
    stationName: "Base Móvel Farol",
    beachName: "Praia do Farol",
    sector: "Orla Norte",
    shift: "08:00 às 17:00",
    status: "ACTIVE",
    onShiftSince: new Date(Date.now() - 2 * 60 * 60_000).toISOString(),
    lastPosition: {
      latitude: -10.96512,
      longitude: -37.03188,
      accuracyMeters: 28,
      at: new Date(Date.now() - 11 * 60_000).toISOString(),
    },
    occurrencesToday: 1,
    fieldAttendedToday: 1,
    signalsToday: 2,
    ciospPending: 0,
    updatedAt: new Date(Date.now() - 3 * 60 * 60_000).toISOString(),
  },
  {
    id: "team-3",
    name: "Sd. Almeida",
    badgeNumber: "BM-30455",
    stationName: "Posto das Conchas",
    beachName: "Praia das Conchas",
    shift: "12:00 às 21:00",
    status: "INACTIVE",
    occurrencesToday: 0,
    fieldAttendedToday: 0,
    signalsToday: 0,
    ciospPending: 0,
    updatedAt: new Date(Date.now() - 26 * 60 * 60_000).toISOString(),
  },
];

/**
 * Painel da coordenação (RF40/RN17): quem está de plantão, última posição
 * conhecida e o volume do dia (ocorrências, atendimentos em campo,
 * sinalizações e pendências no CIOSP). O cadastro é da TI — aqui não se cria
 * usuário. Estado local até `identity`/`shift_sessions` entrarem; a autorização
 * real é sempre do servidor.
 */
export function ManagementTeamPage() {
  const [team, setTeam] = useState<readonly TeamMember[]>(INITIAL_TEAM);

  const totals = useMemo(() => {
    return team.reduce(
      (acc, member) => {
        if (member.status === "ACTIVE") acc.onShift += 1;
        acc.occurrences += member.occurrencesToday;
        acc.field += member.fieldAttendedToday;
        acc.signals += member.signalsToday;
        acc.ciosp += member.ciospPending;
        return acc;
      },
      { onShift: 0, occurrences: 0, field: 0, signals: 0, ciosp: 0 },
    );
  }, [team]);

  const toggleStatus = (memberId: string) => {
    setTeam((previous) =>
      previous.map((member) => {
        if (member.id !== memberId) return member;
        const status: TeamMemberStatus =
          member.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
        return {
          ...member,
          status,
          onShiftSince:
            status === "ACTIVE" ? new Date().toISOString() : undefined,
          updatedAt: new Date().toISOString(),
        };
      }),
    );
  };

  return (
    <div>
      <PageHeader
        title="Equipe e campo"
        subtitle="Acompanhe quem está de plantão, a última posição conhecida e o volume do dia por praia/setor. O cadastro e o convite são feitos pela TI."
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800">
            <UserCheck aria-hidden="true" className="size-3.5 text-emerald-600" />
            {totals.onShift} em plantão
            <span className="text-ocean-400">·</span>
            {team.length - totals.onShift} fora
          </span>
        }
      />

      <dl className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
        <SummaryTile
          icon={ClipboardList}
          label="Ocorrências hoje"
          value={totals.occurrences}
          hint={`${totals.field} em campo`}
        />
        <SummaryTile
          icon={Siren}
          label="Sinalizações"
          value={totals.signals}
          hint="lançadas pela equipe"
        />
        <SummaryTile
          icon={Radio}
          label="CIOSP pendente"
          value={totals.ciosp}
          hint="aguardando protocolo"
        />
        <SummaryTile
          icon={MapPin}
          label="Em plantão"
          value={totals.onShift}
          hint={`${team.length} no escopo`}
        />
      </dl>

      <ul className="grid gap-2">
        {team.map((member) => {
          const isActive = member.status === "ACTIVE";
          return (
            <li
              key={member.id}
              className="rounded-2xl border border-ocean-900/10 bg-white p-3.5 shadow-sm"
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <span
                    aria-hidden="true"
                    className={`grid size-9 place-items-center rounded-full text-xs font-black text-white ${
                      isActive ? "bg-ocean-950" : "bg-ocean-300"
                    }`}
                  >
                    {initialsOf(member.name)}
                  </span>
                  <div>
                    <p className="flex flex-wrap items-center gap-1.5 text-sm font-bold text-ocean-950">
                      {member.name}
                      <span
                        className={`rounded-full border px-2 py-0.5 text-[10px] font-bold ${
                          isActive
                            ? "border-emerald-300 bg-emerald-50 text-emerald-900"
                            : "border-ocean-200 bg-ocean-50 text-ocean-600"
                        }`}
                      >
                        {isActive ? "Em plantão" : "Fora"}
                      </span>
                    </p>
                    <p className="text-[11px] text-ocean-600">
                      {member.badgeNumber} · {member.shift}
                    </p>
                    <p className="flex items-center gap-1 text-[11px] text-ocean-500">
                      <MapPin aria-hidden="true" className="size-3" />
                      {member.stationName} · {member.beachName}
                      {member.sector ? ` · ${member.sector}` : ""}
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => toggleStatus(member.id)}
                  className={[
                    "inline-flex min-h-9 items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold transition",
                    isActive
                      ? "border border-coral-300 bg-coral-50 text-coral-900 hover:bg-coral-100"
                      : "bg-ocean-950 text-white hover:bg-ocean-900",
                  ].join(" ")}
                >
                  {isActive ? (
                    <UserX aria-hidden="true" className="size-3.5" />
                  ) : (
                    <Power aria-hidden="true" className="size-3.5" />
                  )}
                  {isActive ? "Desativar" : "Ativar"}
                </button>
              </div>

              <div className="mt-3 grid gap-2 rounded-xl border border-ocean-100 bg-ocean-50/60 p-2.5 sm:grid-cols-2">
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
                    Última posição conhecida
                  </p>
                  {member.lastPosition ? (
                    <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[11px] text-ocean-700">
                      <span className="font-mono font-bold text-ocean-900">
                        {member.lastPosition.latitude.toFixed(5)},{" "}
                        {member.lastPosition.longitude.toFixed(5)}
                      </span>
                      <span>
                        {formatDateTime(member.lastPosition.at)} ·{" "}
                        {formatAccuracy(member.lastPosition.accuracyMeters)}
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
                    <p className="mt-0.5 text-[11px] text-ocean-500">
                      Sem posição no plantão atual.
                    </p>
                  )}
                  {member.onShiftSince ? (
                    <p className="mt-1 text-[10px] text-ocean-500">
                      Plantão desde {formatDateTime(member.onShiftSince)}
                    </p>
                  ) : null}
                </div>

                <div>
                  <p className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
                    Volume do dia
                  </p>
                  <div className="mt-1 flex flex-wrap gap-1.5 text-[10px] font-bold">
                    <Counter
                      label="ocorrências"
                      value={member.occurrencesToday}
                      tone="border-ocean-200 bg-white text-ocean-900"
                    />
                    <Counter
                      label="em campo"
                      value={member.fieldAttendedToday}
                      tone="border-emerald-200 bg-emerald-50 text-emerald-900"
                    />
                    <Counter
                      label="sinalizações"
                      value={member.signalsToday}
                      tone="border-sand-300 bg-sand-100 text-ocean-950"
                    />
                    <Counter
                      label="CIOSP"
                      value={member.ciospPending}
                      tone="border-amber-300 bg-amber-50 text-amber-900"
                    />
                  </div>
                </div>
              </div>
            </li>
          );
        })}
      </ul>

      <p className="mt-3 text-[11px] leading-relaxed text-ocean-500">
        A posição é a última conhecida durante o plantão e só aparece para a
        coordenação do escopo; não há rastreamento contínuo nem histórico de
        trajeto (RN17). Nenhuma alteração desta tela é enviada ao servidor
        enquanto `identity`/`shift_sessions` não estiverem integrados.
      </p>
    </div>
  );
}

function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

interface SummaryTileProps {
  readonly icon: typeof ClipboardList;
  readonly label: string;
  readonly value: number;
  readonly hint: string;
}

function SummaryTile({ icon: Icon, label, value, hint }: SummaryTileProps) {
  return (
    <div className="rounded-xl border border-ocean-200 bg-white p-3 text-ocean-900">
      <dt className="flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider text-ocean-500">
        <Icon aria-hidden="true" className="size-3" />
        {label}
      </dt>
      <dd className="text-xl font-black">{value}</dd>
      <dd className="text-[10px] text-ocean-500">{hint}</dd>
    </div>
  );
}

interface CounterProps {
  readonly label: string;
  readonly value: number;
  readonly tone: string;
}

function Counter({ label, value, tone }: CounterProps) {
  return (
    <span className={`rounded-full border px-2 py-0.5 ${tone}`}>
      {value} {label}
    </span>
  );
}
