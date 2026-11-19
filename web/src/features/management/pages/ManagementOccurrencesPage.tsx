import { useMemo, useState } from "react";
import { CalendarDays, ChevronDown, ClipboardList, RefreshCw } from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import {
  OccurrenceList,
  OccurrenceSheet,
  isActiveOccurrence,
  useOccurrences,
} from "@/features/occurrences";

type Filter = "ACTIVE" | "CLOSED" | "CIOSP";

/** Converte `YYYY-MM-DD` do input em instante ISO-8601 (início do dia local). */
function startOfDay(date: string): string | undefined {
  if (!date) return undefined;
  const parsed = new Date(`${date}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

/** Fim do dia local, exclusivo: o servidor compara com `occurred_at < to`. */
function endOfDay(date: string): string | undefined {
  if (!date) return undefined;
  const parsed = new Date(`${date}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return undefined;
  parsed.setDate(parsed.getDate() + 1);
  return parsed.toISOString();
}

/**
 * Visão da coordenação sobre as ocorrências (ADR-013).
 *
 * <p>A coordenação vê todas as ocorrências e pode editar qualquer ficha para
 * completar o que só apareceu depois (vítima identificada, protocolo do
 * CIOSP). O recorte é aplicado pelo servidor — situação e período viram
 * parâmetros da consulta —, então o histórico grande é consultado por página
 * em vez de baixar tudo para filtrar no navegador.
 */
export function ManagementOccurrencesPage() {
  const [filter, setFilter] = useState<Filter>("ACTIVE");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [selectedOccurrenceId, setSelectedOccurrenceId] = useState<
    string | null
  >(null);

  const query = useMemo(
    () => ({
      active: filter === "ACTIVE",
      closed: filter === "CLOSED",
      ciospPending: filter === "CIOSP",
      from: startOfDay(from),
      to: endOfDay(to),
    }),
    [filter, from, to],
  );

  const {
    occurrences,
    status,
    message,
    hasMore,
    loadingMore,
    loadMore,
    reload,
  } = useOccurrences(query);

  // Contagem da página já carregada: os totais por situação são do servidor e
  // mudam com o filtro, por isso aparecem como "nesta tela" e não como total.
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

  const periodLabel =
    from || to
      ? `Período: ${from ? formatDate(from) : "início"} até ${
          to ? formatDate(to) : "hoje"
        }`
      : "Todo o histórico";

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
        <SummaryTile label="Nesta tela" value={counts.total} />
        <SummaryTile label="Em atendimento" value={counts.inProgress} />
        <SummaryTile label="CIOSP pendente" value={counts.ciosp} />
      </dl>

      <div className="mb-3 grid gap-2 rounded-xl border border-ocean-200 bg-white p-2 sm:grid-cols-[auto_minmax(0,1fr)]">
        <div
          role="tablist"
          aria-label="Filtro das ocorrências"
          className="inline-flex flex-wrap rounded-xl border border-ocean-200 bg-white p-1"
        >
          <FilterButton
            active={filter === "ACTIVE"}
            onClick={() => setFilter("ACTIVE")}
            label="Em atendimento"
          />
          <FilterButton
            active={filter === "CLOSED"}
            onClick={() => setFilter("CLOSED")}
            label="Encerradas"
          />
          <FilterButton
            active={filter === "CIOSP"}
            onClick={() => setFilter("CIOSP")}
            label="CIOSP pendente"
          />
        </div>

        <div className="flex flex-wrap items-end gap-2">
          <label className="grid gap-1 text-[11px] font-bold text-ocean-700">
            De
            <input
              type="date"
              value={from}
              max={to || undefined}
              onChange={(event) => setFrom(event.target.value)}
              className="min-h-9 rounded-xl border border-ocean-200 bg-white px-2 text-xs font-medium text-ocean-950"
            />
          </label>
          <label className="grid gap-1 text-[11px] font-bold text-ocean-700">
            Até
            <input
              type="date"
              value={to}
              min={from || undefined}
              onChange={(event) => setTo(event.target.value)}
              className="min-h-9 rounded-xl border border-ocean-200 bg-white px-2 text-xs font-medium text-ocean-950"
            />
          </label>
          {from || to ? (
            <button
              type="button"
              onClick={() => {
                setFrom("");
                setTo("");
              }}
              className="min-h-9 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-800 hover:bg-ocean-50"
            >
              Limpar período
            </button>
          ) : null}
          {!from && !to ? (
            <p className="flex items-center gap-1 pb-2 text-[11px] text-ocean-500">
              <CalendarDays aria-hidden="true" className="size-3.5" />
              Sem período definido: a lista abre pelas mais recentes.
            </p>
          ) : null}
        </div>
      </div>

      <p className="mb-2 text-[11px] font-semibold text-ocean-600">
        {periodLabel} · {FILTER_HINT[filter]}
      </p>

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
        <>
          <OccurrenceList
            occurrences={occurrences}
            onSelect={(occurrence) => setSelectedOccurrenceId(occurrence.id)}
            emptyMessage={EMPTY_MESSAGE[filter]}
          />
          {hasMore ? (
            <div className="mt-3 text-center">
              <button
                type="button"
                onClick={loadMore}
                disabled={loadingMore}
                className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:cursor-progress disabled:opacity-70"
              >
                <ChevronDown aria-hidden="true" className="size-3.5" />
                {loadingMore ? "Carregando…" : "Carregar mais antigas"}
              </button>
              <p className="mt-1 text-[11px] text-ocean-500">
                A lista abre pelas mais recentes; o cursor do servidor busca as
                páginas anteriores sem repetir registros.
              </p>
            </div>
          ) : null}
        </>
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

const FILTER_HINT: Record<Filter, string> = {
  ACTIVE: "atendimentos abertos ou em curso e pendências de CIOSP.",
  CLOSED: "só atendimentos encerrados, com desfecho.",
  CIOSP: "só o que ainda falta transcrever para o CIOSP.",
};

const EMPTY_MESSAGE: Record<Filter, string> = {
  ACTIVE: "Nenhum atendimento em curso ou pendência de CIOSP no período.",
  CLOSED: "Nenhuma ocorrência encerrada no período.",
  CIOSP: "Nenhuma pendência de CIOSP no período.",
};

/** `YYYY-MM-DD` para exibição `dd/mm/aaaa`, sem depender do fuso do horário. */
function formatDate(date: string): string {
  const [year, month, day] = date.split("-");
  if (!year || !month || !day) return date;
  return `${day}/${month}/${year}`;
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
