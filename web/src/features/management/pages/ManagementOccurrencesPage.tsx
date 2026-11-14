import { useMemo } from "react";
import { ClipboardList, RefreshCw } from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { OccurrenceList, useOccurrences } from "@/features/occurrences";

/**
 * Visão da coordenação sobre as ocorrências (ADR-013).
 *
 * <p>A coordenação vê todas as ocorrências; o guarda-vidas vê as do seu posto,
 * praia e as que assumiu. O recorte é aplicado pelo servidor, não pela tela.
 * A ficha de transcrição do CIOSP e o encerramento com desfecho entram na
 * próxima fatia — aqui já aparecem o número interno e a pendência.
 */
export function ManagementOccurrencesPage() {
  const { occurrences, status, message, reload } = useOccurrences();

  const counts = useMemo(() => {
    return occurrences.reduce(
      (acc, occurrence) => {
        acc.total += 1;
        if (occurrence.status === "IN_PROGRESS") acc.inProgress += 1;
        if (occurrence.ciospStatus === "PENDING") acc.ciosp += 1;
        return acc;
      },
      { total: 0, inProgress: 0, ciosp: 0 },
    );
  }, [occurrences]);

  return (
    <div>
      <PageHeader
        title="Ocorrências da operação"
        subtitle="Atendimentos assumidos pela equipe, com o número interno e a pendência de transcrição para o CIOSP."
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
          occurrences={occurrences}
          emptyMessage="Nenhuma ocorrência no período. Ela aparece aqui assim que um guarda-vidas assume um chamado do SOS."
        />
      )}

      <p className="mt-3 flex items-start gap-1.5 text-[11px] leading-relaxed text-ocean-500">
        <ClipboardList aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
        A localização precisa do atendimento é restrita à operação e não aparece
        em listagem pública. Dados de pessoas são opcionais, cifrados e
        mascarados por padrão.
      </p>
    </div>
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
