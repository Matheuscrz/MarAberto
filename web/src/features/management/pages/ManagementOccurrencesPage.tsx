import { useMemo, useState } from "react";
import { ClipboardList, RefreshCw } from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import {
  OccurrenceList,
  OccurrenceSheet,
  isActiveOccurrence,
  useOccurrences,
} from "@/features/occurrences";

type Filter = "ACTIVE" | "CLOSED" | "CIOSP";

/**
 * Visão da coordenação sobre as ocorrências (ADR-013).
 *
 * <p>A coordenação vê todas as ocorrências e pode editar qualquer ficha para
 * completar o que só apareceu depois (vítima identificada, protocolo do
 * CIOSP). O recorte é aplicado pelo servidor; aqui ficam apenas os filtros de
 * leitura.
 */
export function ManagementOccurrencesPage() {
  const { occurrences, status, message, reload } = useOccurrences();
  const [filter, setFilter] = useState<Filter>("ACTIVE");
  const [selectedOccurrenceId, setSelectedOccurrenceId] = useState<
    string | null
  >(null);

  const counts = useMemo(() => {
    return occurrences.reduce(
      (acc, occurrence) => {
        acc.total += 1;
        if (isActiveOccurrence(occurrence)) acc.inProgress += 1;
        if (occurrence.ciospStatus === "PENDING") acc.ciosp += 1;
        return acc;
      },
      { total: 0, inProgress: 0, ciosp: 0 },
    );
  }, [occurrences]);

  const shown = useMemo(() => {
    if (filter === "ACTIVE") return occurrences.filter(isActiveOccurrence);
    if (filter === "CLOSED")
      return occurrences.filter((occurrence) => !isActiveOccurrence(occurrence));
    return occurrences.filter(
      (occurrence) => occurrence.ciospStatus === "PENDING",
    );
  }, [occurrences, filter]);

  return (
    <div>
      <PageHeader
        title="Ocorrências da operação"
        subtitle="Atendimentos da equipe, com o desfecho, o relato e a pendência de transcrição para o CIOSP. A coordenação pode completar qualquer ficha."
        actions={
          <button
            type="button"
            onClick={reload}
            className="inline-flex items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
          >
            <RefreshCw aria-hidden="true" className="size-3.5 text-ocean-600" />
            Atualizar
          </button>
        }
      />

      <dl className="mb-4 grid grid-cols-3 gap-2">
        <SummaryTile label="Ocorrências" value={counts.total} />
        <SummaryTile label="Em atendimento" value={counts.inProgress} />
        <SummaryTile label="CIOSP pendente" value={counts.ciosp} />
      </dl>

      <div
        role="tablist"
        aria-label="Filtro das ocorrências"
        className="mb-3 inline-flex flex-wrap rounded-xl border border-ocean-200 bg-white p-1"
      >
        <FilterButton
          active={filter === "ACTIVE"}
          onClick={() => setFilter("ACTIVE")}
          label={`Em atendimento (${counts.inProgress})`}
        />
        <FilterButton
          active={filter === "CLOSED"}
          onClick={() => setFilter("CLOSED")}
          label={`Encerradas (${counts.total - counts.inProgress})`}
        />
        <FilterButton
          active={filter === "CIOSP"}
          onClick={() => setFilter("CIOSP")}
          label={`CIOSP pendente (${counts.ciosp})`}
        />
      </div>

      {message ? (
        <output className="mb-2 block rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900">
          {message} A lista abaixo é a última que chegou.
        </output>
      ) : null}

      {status === "loading" && occurrences.length === 0 ? (
        <p className="rounded-xl border border-ocean-200 bg-white p-4 text-center text-xs font-semibold text-ocean-600">
          Carregando as ocorrências…
        </p>
      ) : (
        <OccurrenceList
          occurrences={shown}
          onSelect={(occurrence) => setSelectedOccurrenceId(occurrence.id)}
          emptyMessage={
            filter === "ACTIVE"
              ? "Nenhum atendimento em curso no momento."
              : filter === "CLOSED"
                ? "Nenhuma ocorrência encerrada no período."
                : "Nenhuma pendência de CIOSP no período."
          }
        />
      )}

      {selectedOccurrenceId ? (
        <OccurrenceSheet
          occurrenceId={selectedOccurrenceId}
          onClose={() => setSelectedOccurrenceId(null)}
          onChanged={reload}
        />
      ) : null}

      <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-relaxed text-ocean-500">
        <ClipboardList aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
        A localização precisa do atendimento é restrita à operação e não aparece
        em listagem pública. Dados de vítima são opcionais, cifrados e
        mascarados por padrão; revelar exige motivo e gera auditoria.
      </p>
    </div>
  );
}

function FilterButton({
  active,
  onClick,
  label,
}: {
  readonly active: boolean;
  readonly onClick: () => void;
  readonly label: string;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={`rounded-lg px-3 py-1 text-[11px] font-bold transition ${
        active
          ? "bg-ocean-950 text-white"
          : "text-ocean-700 hover:bg-ocean-50"
      }`}
    >
      {label}
    </button>
  );
}

interface SummaryTileProps {
  readonly label: string;
  readonly value: number;
}

function SummaryTile({ label, value }: SummaryTileProps) {
  return (
    <div className="rounded-xl border border-ocean-200 bg-white p-3 text-center text-ocean-900">
      <dt className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
        {label}
      </dt>
      <dd className="text-xl font-black">{value}</dd>
    </div>
  );
}
