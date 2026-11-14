import { ClipboardList, MapPin } from "lucide-react";
import { formatAccuracy, formatDateTime } from "@/shared/lib/format";
import {
  CIOSP_LABEL,
  NATURE_LABEL,
  ORIGIN_LABEL,
  STATUS_LABEL,
  type Occurrence,
  type OccurrenceStatus,
} from "@/features/occurrences/types";

interface OccurrenceListProps {
  readonly occurrences: readonly Occurrence[];
  readonly emptyMessage?: string;
}

/**
 * Lista de ocorrências do console (operador e coordenação).
 *
 * <p>O número interno é o que o guarda-vidas usa na ficha do CIOSP. A
 * coordenada aparece aqui porque a rota é autenticada e restrita ao escopo:
 * ela é a do chamado, não um histórico de deslocamento de ninguém.
 */
export function OccurrenceList({
  occurrences,
  emptyMessage,
}: OccurrenceListProps) {
  if (occurrences.length === 0) {
    return (
      <p className="rounded-xl border border-dashed border-ocean-300 bg-white p-4 text-center text-xs font-semibold text-ocean-600">
        {emptyMessage ??
          "Nenhuma ocorrência registrada. Ao assumir um chamado do SOS, a ficha do atendimento aparece aqui."}
      </p>
    );
  }

  return (
    <ul className="grid gap-2">
      {occurrences.map((occurrence) => (
        <li key={occurrence.id}>
          <OccurrenceCard occurrence={occurrence} />
        </li>
      ))}
    </ul>
  );
}

const STATUS_STYLE: Record<OccurrenceStatus, string> = {
  OPEN: "border-ocean-200 bg-ocean-50 text-ocean-700",
  IN_PROGRESS: "border-amber-300 bg-amber-50 text-amber-900",
  CLOSED: "border-emerald-200 bg-emerald-50 text-emerald-800",
  CANCELLED: "border-ocean-200 bg-ocean-50 text-ocean-600",
  FALSE_ALARM: "border-ocean-200 bg-ocean-50 text-ocean-600",
};

function OccurrenceCard({ occurrence }: { readonly occurrence: Occurrence }) {
  const place =
    occurrence.beachName ??
    (occurrence.latitude !== null
      ? "Fora do mapeamento"
      : "Local não informado");
  const sector = occurrence.sectorLabel
    ? ` · ${occurrence.sectorLabel}`
    : "";
  const ciosp =
    occurrence.ciospStatus === "PENDING"
      ? "border-amber-300 bg-amber-50 text-amber-900"
      : "border-ocean-200 bg-ocean-50 text-ocean-600";

  return (
    <article className="rounded-xl border border-ocean-900/10 bg-white p-3 text-xs shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex flex-wrap items-center gap-1.5 font-bold text-ocean-950">
          <span className="font-mono">#{occurrence.number}</span>
          {NATURE_LABEL[occurrence.nature]}
          <span className="rounded-full border border-ocean-200 bg-ocean-50 px-2 py-0.5 text-[10px] font-semibold text-ocean-700">
            {ORIGIN_LABEL[occurrence.origin]}
          </span>
        </p>
        <span
          className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-bold ${STATUS_STYLE[occurrence.status]}`}
        >
          {STATUS_LABEL[occurrence.status]}
        </span>
      </div>

      <p className="mt-1 flex flex-wrap items-center gap-x-1.5 text-ocean-600">
        <MapPin aria-hidden="true" className="size-3.5 shrink-0" />
        {place}
        {sector}
        <span aria-hidden="true">·</span>
        {formatDateTime(occurrence.occurredAt)}
        {occurrence.postName ? (
          <>
            <span aria-hidden="true">·</span>
            {occurrence.postName}
          </>
        ) : null}
      </p>

      <p className="mt-1 flex flex-wrap items-center gap-1.5 text-[10px] text-ocean-500">
        <span
          className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 font-bold ${ciosp}`}
        >
          {CIOSP_LABEL[occurrence.ciospStatus]}
        </span>
        {occurrence.latitude !== null && occurrence.longitude !== null ? (
          <span>
            {occurrence.latitude.toFixed(5)},{" "}
            {occurrence.longitude.toFixed(5)}
            {occurrence.accuracyMeters !== null
              ? ` · ${formatAccuracy(occurrence.accuracyMeters)}`
              : ""}
          </span>
        ) : null}
        {occurrence.acknowledgedAt ? (
          <span>
            Chamado assumido em {formatDateTime(occurrence.acknowledgedAt)}
          </span>
        ) : null}
      </p>
    </article>
  );
}

/** Estado vazio com ícone, para a ficha da tela de ocorrências. */
export function OccurrenceEmptyHint() {
  return (
    <p className="flex items-start gap-1.5 rounded-xl border border-dashed border-ocean-300 bg-white p-4 text-xs font-semibold text-ocean-600">
      <ClipboardList aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      A ficha do atendimento é aberta automaticamente quando você aceita um
      chamado do SOS.
    </p>
  );
}
