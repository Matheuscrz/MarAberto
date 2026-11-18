import {
  ClipboardList,
  ExternalLink,
  MapPin,
  Radio,
  Siren,
  UserCheck,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { formatAccuracy, formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useManagementTeam } from "@/features/management/hooks/use-management-team";
import type { TeamMember } from "@/features/management/types";

/**
 * Painel da coordenação (RF40/RN17): quem está de plantão, última posição
 * conhecida e o volume do dia (ocorrências, atendimentos em campo,
 * sinalizações e pendências no CIOSP). O cadastro é da TI — aqui não se cria
 * usuário nem se altera o turno.
 *
 * <p>A fonte é `GET /api/v1/management/team`, `no-store`, e a posição é a
 * última conhecida do plantão: não há rastreamento contínuo nem histórico de
 * trajeto. Plantão encerrado some da lista na leitura seguinte (15 s).
 */
export function ManagementTeamPage() {
  const { team, status, message, reload } = useManagementTeam();
  const members = team?.members ?? [];

  const totals = members.reduce(
    (acc, member) => {
      acc.occurrences += member.occurrencesToday;
      acc.field += member.fieldAttendedToday;
      acc.signals += member.signalsToday;
      acc.ciosp += member.ciospPending;
      return acc;
    },
    { occurrences: 0, field: 0, signals: 0, ciosp: 0 },
  );

  return (
    <div>
      <PageHeader
        title="Equipe e campo"
        subtitle="Acompanhe quem está de plantão, a última posição conhecida e o volume do dia por praia/setor. O cadastro e o convite são feitos pela TI."
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800">
            <UserCheck aria-hidden="true" className="size-3.5 text-emerald-600" />
            {members.length} em plantão
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
          value={members.length}
          hint="no escopo da coordenação"
        />
      </dl>

      {status === "loading" && members.length === 0 ? (
        <output className="block py-6 text-center text-sm font-semibold text-ocean-600">
          Carregando a equipe
        </output>
      ) : null}

      {message ? (
        <div
          role="alert"
          className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-950"
        >
          <span>{sanitizeText(message, 200)}</span>
          <button
            type="button"
            onClick={reload}
            className="rounded-lg border border-amber-300 bg-white px-2 py-1 font-bold text-amber-950 hover:bg-amber-100"
          >
            Tentar de novo
          </button>
        </div>
      ) : null}

      {members.length === 0 && status === "ready" ? (
        <div className="rounded-2xl border border-dashed border-ocean-300 bg-ocean-50/50 px-4 py-8 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Nenhum plantão aberto agora
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            Quem abrir o console de campo aparece aqui na próxima leitura (até
            15 s).
          </p>
        </div>
      ) : null}

      <ul className="grid gap-2">
        {members.map((member) => (
          <TeamMemberCard key={member.shiftId} member={member} />
        ))}
      </ul>

      <p className="mt-3 text-[11px] leading-relaxed text-ocean-500">
        A posição é a última conhecida durante o plantão e só aparece para a
        coordenação em rota autenticada e `no-store`; não há rastreamento
        contínuo nem histórico de trajeto (RN17). Posição mais velha que 30 min
        aparece como "sem sinal".
      </p>
    </div>
  );
}

function TeamMemberCard({ member }: { readonly member: TeamMember }) {
  const position = member.lastPosition;
  return (
    <li className="rounded-2xl border border-ocean-900/10 bg-white p-3.5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            className={`grid size-9 place-items-center rounded-full text-xs font-black text-white ${
              position && !position.stale ? "bg-ocean-950" : "bg-ocean-300"
            }`}
          >
            {initialsOf(member.name)}
          </span>
          <div>
            <p className="flex flex-wrap items-center gap-1.5 text-sm font-bold text-ocean-950">
              {sanitizeText(member.name, 80)}
              <span
                className={`rounded-full border px-2 py-0.5 text-[10px] font-bold ${
                  !position
                    ? "border-ocean-200 bg-ocean-50 text-ocean-600"
                    : position.stale
                      ? "border-amber-300 bg-amber-50 text-amber-900"
                      : "border-emerald-300 bg-emerald-50 text-emerald-900"
                }`}
              >
                {!position
                  ? "Sem GPS"
                  : position.stale
                    ? "Sem sinal"
                    : "Em plantão"}
              </span>
            </p>
            <p className="text-[11px] text-ocean-600">
              {member.badgeNumber ?? "Sem matrícula"} · desde{" "}
              {formatDateTime(member.onShiftSince)}
            </p>
            <p className="flex items-center gap-1 text-[11px] text-ocean-500">
              <MapPin aria-hidden="true" className="size-3" />
              {[member.stationName, member.beachName, member.sectorLabel]
                .filter(Boolean)
                .join(" · ") || "Sem estrutura vinculada"}
            </p>
          </div>
        </div>
      </div>

      <div className="mt-3 grid gap-2 rounded-xl border border-ocean-100 bg-ocean-50/60 p-2.5 sm:grid-cols-2">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
            Última posição conhecida
          </p>
          {position ? (
            <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[11px] text-ocean-700">
              <span className="font-mono font-bold text-ocean-900">
                {position.latitude.toFixed(5)}, {position.longitude.toFixed(5)}
              </span>
              <span>
                {formatDateTime(position.at ?? member.lastSeenAt)} ·{" "}
                {formatAccuracy(position.accuracyMeters ?? undefined)}
              </span>
              <a
                href={`https://www.google.com/maps/search/?api=1&query=${position.latitude},${position.longitude}`}
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
          <p className="mt-1 text-[10px] text-ocean-500">
            Último sinal de vida em {formatDateTime(member.lastSeenAt)}
            {member.lastPosition?.stale
              ? " · posição vencida, mostrada como sem sinal"
              : ""}
          </p>
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
