import { useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle,
  ChevronDown,
  ClipboardList,
  Crosshair,
  MapPin,
  Plus,
  Send,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import {
  GeolocationError,
  getDevicePosition,
} from "@/shared/lib/geolocation";
import { formatAccuracy, formatDateTime } from "@/shared/lib/format";
import { newRequestId } from "@/shared/lib/id";
import { useNearbyBeaches } from "@/features/beaches";
import {
  OccurrenceList,
  OccurrenceSheet,
  createOccurrence,
  isActiveOccurrence,
  occurrenceErrorMessage,
  useOccurrences,
  type DuplicateCandidate,
} from "@/features/occurrences";
import type {
  OccurrenceNature,
  OccurrenceOrigin,
  OccurrenceOutcome,
} from "@/features/operator/types";
import {
  NATURE_LABEL,
  ORIGIN_LABEL,
  OUTCOME_LABEL,
} from "@/features/operator/types";

interface Position {
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters: number;
}

/**
 * Ocorrências do turno.
 *
 * <p>As do SOS chegam prontas do servidor: a ficha nasce no ack, já com número
 * interno, praia/setor e pendência de CIOSP. O registro manual (193, rádio,
 * presencial ou atendimento em campo) envia para `POST /api/v1/occurrences`
 * com idempotência por `clientRequestId` e alerta não bloqueante de duplicata.
 */
export function OperatorOccurrencesPage() {
  const {
    occurrences,
    status,
    message: loadMessage,
    hasMore,
    loadingMore,
    loadMore,
    reload,
  } = useOccurrences();
  const [tab, setTab] = useState<"active" | "closed">("active");
  const [selectedOccurrenceId, setSelectedOccurrenceId] = useState<
    string | null
  >(null);
  const [origin, setOrigin] = useState<OccurrenceOrigin>("FIELD_ATTENDED");
  const [nature, setNature] = useState<OccurrenceNature>("RESCUE");
  const [outcome, setOutcome] = useState<OccurrenceOutcome | "">("RESOLVED");
  const [occurredAt, setOccurredAt] = useState(toLocalInput(new Date()));
  const [beachId, setBeachId] = useState("");
  const [reference, setReference] = useState("");
  const [summary, setSummary] = useState("");
  const [protocol, setProtocol] = useState("");
  const [position, setPosition] = useState<Position | null>(null);
  const [geoMessage, setGeoMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [createdMessage, setCreatedMessage] = useState<string | null>(null);
  const [duplicates, setDuplicates] = useState<readonly DuplicateCandidate[]>(
    [],
  );
  const requestIdRef = useRef<string | null>(null);

  // A ocorrência é registrada sobre uma área do mapeamento real, num raio de
  // até 30 km: o guarda-vidas escolhe entre o que está perto dele agora.
  const { beaches } = useNearbyBeaches();
  const effectiveBeachId = beachId || beaches[0]?.id || "";

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

  const activeOccurrences = useMemo(
    () => occurrences.filter(isActiveOccurrence),
    [occurrences],
  );
  const closedOccurrences = useMemo(
    () => occurrences.filter((occurrence) => !isActiveOccurrence(occurrence)),
    [occurrences],
  );
  const shownOccurrences =
    tab === "active" ? activeOccurrences : closedOccurrences;

  const useMyLocation = async () => {
    setGeoMessage(null);
    try {
      const position = await getDevicePosition({
        enableHighAccuracy: true,
        timeout: 10_000,
        maximumAge: 0,
      });
      setPosition({
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyMeters: position.accuracyMeters,
      });
      setGeoMessage("Coordenada do atendimento preenchida com o GPS atual.");
    } catch (error) {
      setGeoMessage(
        error instanceof GeolocationError
          ? error.message
          : "Não foi possível obter a localização. Use a praia + ponto de referência.",
      );
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitError(null);
    setCreatedMessage(null);

    const trimmedSummary = summary.trim();
    const trimmedReference = reference.trim();
    if (!trimmedSummary) {
      setSubmitError("Informe o resumo do atendimento.");
      return;
    }
    if (!position && (!effectiveBeachId || !trimmedReference)) {
      setSubmitError(
        "Sem coordenada GPS, informe a praia e o ponto de referência.",
      );
      return;
    }

    const clientRequestId = requestIdRef.current ?? newRequestId();
    requestIdRef.current = clientRequestId;
    setSubmitting(true);

    try {
      const response = await createOccurrence({
        clientRequestId,
        origin,
        nature,
        occurredAt: new Date(occurredAt).toISOString(),
        latitude: position?.latitude,
        longitude: position?.longitude,
        accuracyMeters: position?.accuracyMeters,
        beachId: effectiveBeachId || undefined,
        referenceText: trimmedReference || undefined,
        summary: trimmedSummary,
        outcome: outcome || undefined,
        externalProtocol:
          origin === "CBM_193" || origin === "EXTERNAL_OTHER"
            ? protocol.trim() || undefined
            : undefined,
      });

      requestIdRef.current = null;
      setOrigin("FIELD_ATTENDED");
      setNature("RESCUE");
      setOutcome("RESOLVED");
      setOccurredAt(toLocalInput(new Date()));
      setReference("");
      setSummary("");
      setProtocol("");
      setPosition(null);
      setGeoMessage(null);
      setDuplicates(response.possibleDuplicates);
      setCreatedMessage(
        `Ocorrência #${response.detail.occurrence.number} registrada.`,
      );
      reload();
    } catch (error) {
      setSubmitError(occurrenceErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Ocorrências"
        subtitle="A ficha do chamado do SOS nasce quando você assume o atendimento. Encerre com o relato e o desfecho; a coordenação completa depois o que vier a mais (vítima, CIOSP)."
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800">
            <ClipboardList aria-hidden="true" className="size-3.5 text-ocean-600" />
            {counts.total} no turno
          </span>
        }
      />

      <dl className="mb-4 grid grid-cols-3 gap-2">
        <SummaryTile label="Atendimentos" value={counts.total} />
        <SummaryTile label="Em atendimento" value={counts.inProgress} />
        <SummaryTile label="CIOSP pendente" value={counts.ciosp} />
      </dl>

      <section className="mb-4">
        <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-xs font-bold uppercase tracking-wider text-ocean-500">
            Seus atendimentos
          </h2>
          <button
            type="button"
            onClick={reload}
            className="text-[11px] font-bold text-ocean-700 underline-offset-2 hover:underline"
          >
            Atualizar
          </button>
        </div>

        <div
          role="tablist"
          aria-label="Situação das ocorrências"
          className="mb-2 inline-flex rounded-xl border border-ocean-200 bg-white p-1"
        >
          <button
            type="button"
            role="tab"
            aria-selected={tab === "active"}
            onClick={() => setTab("active")}
            className={`rounded-lg px-3 py-1 text-[11px] font-bold transition ${
              tab === "active"
                ? "bg-ocean-950 text-white"
                : "text-ocean-700 hover:bg-ocean-50"
            }`}
          >
            Em atendimento ({activeOccurrences.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === "closed"}
            onClick={() => setTab("closed")}
            className={`rounded-lg px-3 py-1 text-[11px] font-bold transition ${
              tab === "closed"
                ? "bg-ocean-950 text-white"
                : "text-ocean-700 hover:bg-ocean-50"
            }`}
          >
            Atendidas ({closedOccurrences.length})
          </button>
        </div>

        {loadMessage ? (
          <output className="mb-2 block rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900">
            {loadMessage} A lista abaixo é a última que chegou.
          </output>
        ) : null}

        {status === "loading" && occurrences.length === 0 ? (
          <p className="rounded-xl border border-ocean-200 bg-white p-4 text-center text-xs font-semibold text-ocean-600">
            Carregando as ocorrências do turno…
          </p>
        ) : (
          <>
            <OccurrenceList
              occurrences={shownOccurrences}
              onSelect={(occurrence) => setSelectedOccurrenceId(occurrence.id)}
              emptyMessage={
                tab === "active"
                  ? "Nenhum atendimento em curso. Ao aceitar um SOS, a ficha aparece aqui para ser encerrada."
                  : "Nenhum atendimento encerrado ainda. Ao encerrar, a ficha sai da lista em atendimento e fica aqui com o desfecho."
              }
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
                  A lista abre pelas mais recentes; o cursor do servidor busca
                  as páginas anteriores sem repetir registros.
                </p>
              </div>
            ) : null}
          </>
        )}
      </section>

      {selectedOccurrenceId ? (
        <OccurrenceSheet
          occurrenceId={selectedOccurrenceId}
          onClose={() => setSelectedOccurrenceId(null)}
          onChanged={reload}
        />
      ) : null}

      <details className="rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm">
        <summary className="flex cursor-pointer items-center gap-1.5 text-sm font-bold text-ocean-950">
          <Plus aria-hidden="true" className="size-4 text-ocean-600" />
          Registrar ocorrência do 193, do rádio ou de campo
        </summary>
        <p className="mt-1 text-[11px] leading-relaxed text-ocean-500">
          Informe as coordenadas do atendimento ou selecione a praia com ponto de
          referência. Se houver registro próximo nos últimos 30 min, o sistema
          exibe um aviso sem bloquear o envio.
        </p>

        <form onSubmit={handleSubmit} className="mt-3 grid gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-xs font-bold uppercase tracking-wider text-ocean-500">
              Dados do atendimento
            </h3>
            <button
              type="button"
              onClick={useMyLocation}
              className="inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-ocean-200 bg-ocean-50 px-2.5 py-1 text-[11px] font-bold text-ocean-800 transition hover:bg-ocean-100"
            >
              <Crosshair aria-hidden="true" className="size-3.5" />
              Usar minha localização
            </button>
          </div>

          {geoMessage ? (
            <output className="block rounded-xl border border-ocean-200 bg-ocean-50 px-3 py-2 text-xs font-semibold text-ocean-800">
              {geoMessage}
              {position
                ? ` Precisão ${formatAccuracy(position.accuracyMeters)}.`
                : ""}
            </output>
          ) : null}

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block text-xs font-semibold text-ocean-800">
              Origem
              <select
                value={origin}
                onChange={(event) =>
                  setOrigin(event.target.value as OccurrenceOrigin)
                }
                className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
              >
                {Object.entries(ORIGIN_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-semibold text-ocean-800">
              Data e hora do fato
              <input
                type="datetime-local"
                required
                value={occurredAt}
                onChange={(event) => setOccurredAt(event.target.value)}
                className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
              />
            </label>

            <label className="block text-xs font-semibold text-ocean-800">
              Natureza
              <select
                value={nature}
                onChange={(event) =>
                  setNature(event.target.value as OccurrenceNature)
                }
                className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
              >
                {Object.entries(NATURE_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-semibold text-ocean-800">
              Praia
              <select
                value={effectiveBeachId}
                onChange={(event) => setBeachId(event.target.value)}
                disabled={beaches.length === 0}
                className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none disabled:bg-ocean-50"
              >
                {beaches.length === 0 ? (
                  <option value="">Nenhuma praia num raio de 30 km</option>
                ) : null}
                {beaches.map((beach) => (
                  <option key={beach.id} value={beach.id}>
                    {beach.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="block text-xs font-semibold text-ocean-800">
              Ponto de referência {position ? "(opcional)" : "(obrigatório sem GPS)"}
              <input
                type="text"
                maxLength={160}
                required={!position}
                value={reference}
                onChange={(event) => setReference(event.target.value)}
                placeholder="Ex.: ao lado do posto 02"
                className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
              />
            </label>

            {origin === "CBM_193" || origin === "EXTERNAL_OTHER" ? (
              <label className="block text-xs font-semibold text-ocean-800">
                {origin === "CBM_193"
                  ? "Protocolo do 193 (recomendado)"
                  : "Protocolo externo (opcional)"}
                <input
                  type="text"
                  maxLength={40}
                  value={protocol}
                  onChange={(event) => setProtocol(event.target.value)}
                  placeholder="Nº informado pelo canal"
                  className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
                />
              </label>
            ) : null}

            <label className="block text-xs font-semibold text-ocean-800">
              Desfecho (opcional se ainda em andamento)
              <select
                value={outcome}
                onChange={(event) =>
                  setOutcome(event.target.value as OccurrenceOutcome | "")
                }
                className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
              >
                <option value="">Ainda em atendimento</option>
                {Object.entries(OUTCOME_LABEL).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="block text-xs font-semibold text-ocean-800">
            Resumo do atendimento
            <textarea
              required
              maxLength={500}
              rows={3}
              value={summary}
              onChange={(event) => setSummary(event.target.value)}
              placeholder="O que aconteceu, quantas pessoas, como foi o atendimento."
              className="mt-1 w-full rounded-lg border border-ocean-200 px-3 py-2 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-ocean-500">
            <MapPin aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            Dados de pessoas são opcionais e cifrados. A localização precisa do
            atendimento é restrita à operação e não aparece em listagem pública.
          </p>

          {submitError ? (
            <output className="block rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs font-semibold text-red-800">
              {submitError}
            </output>
          ) : null}

          {createdMessage ? (
            <output className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-semibold text-emerald-900">
              <CheckCircle aria-hidden="true" className="size-4 shrink-0" />
              {createdMessage}
            </output>
          ) : null}

          {duplicates.length > 0 ? (
            <aside className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-xs text-amber-950">
              <p className="flex items-center gap-1.5 font-bold">
                <AlertTriangle aria-hidden="true" className="size-4 shrink-0 text-amber-700" />
                Possível duplicata próxima (≤ 200 m e ≤ 30 min)
              </p>
              <p className="mt-1 text-[11px] text-amber-900">
                Sua ocorrência foi registrada normalmente. Confira se algum dos
                registros abaixo trata do mesmo atendimento:
              </p>
              <ul className="mt-2 grid gap-1.5">
                {duplicates.map((candidate) => (
                  <li
                    key={`${candidate.kind}-${candidate.id}`}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-200 bg-white px-2.5 py-1.5 text-[11px]"
                  >
                    <span>
                      {candidate.kind === "OCCURRENCE" && candidate.number !== null
                        ? `Ocorrência #${candidate.number}`
                        : "Chamado SOS"}{" "}
                      · {Math.round(candidate.distanceMeters)} m ·{" "}
                      {formatDateTime(candidate.occurredAt)}
                    </span>
                    {candidate.kind === "OCCURRENCE" ? (
                      <button
                        type="button"
                        onClick={() => setSelectedOccurrenceId(candidate.id)}
                        className="font-bold text-ocean-800 underline-offset-2 hover:underline"
                      >
                        Abrir ficha
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            </aside>
          ) : null}

          <button
            type="submit"
            disabled={submitting}
            className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:cursor-progress disabled:opacity-70"
          >
            <Send aria-hidden="true" className="size-4" />
            {submitting ? "Registrando…" : "Registrar ocorrência"}
          </button>
        </form>
      </details>
    </div>
  );
}

function toLocalInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate(),
  )}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
