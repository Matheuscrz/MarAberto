import { useMemo, useState } from "react";
import {
  CheckCircle,
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
import type {
  OccurrenceNature,
  OccurrenceOrigin,
  OccurrenceOutcome,
  OperatorOccurrence,
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

const INITIAL_OCCURRENCES: readonly OperatorOccurrence[] = [];

/**
 * Registro da ocorrência pelo guarda-vidas (RF33/RN12): atendeu, registrou.
 * Cobre o chamado do 193, o que chegou por rádio/presencial e o atendimento
 * feito sem chamado (`FIELD_ATTENDED`). Estado local até `POST
 * /api/v1/occurrences` existir; `clientRequestId` já é gerado uma vez por
 * registro para a idempotência real.
 */
export function OperatorOccurrencesPage() {
  const [occurrences, setOccurrences] =
    useState<readonly OperatorOccurrence[]>(INITIAL_OCCURRENCES);
  const [origin, setOrigin] = useState<OccurrenceOrigin>("FIELD_ATTENDED");
  const [nature, setNature] = useState<OccurrenceNature>("RESCUE");
  const [outcome, setOutcome] = useState<OccurrenceOutcome>("RESOLVED");
  const [occurredAt, setOccurredAt] = useState(toLocalInput(new Date()));
  const [beachId, setBeachId] = useState("");
  const [sector, setSector] = useState("");
  const [reference, setReference] = useState("");
  const [summary, setSummary] = useState("");
  const [protocol, setProtocol] = useState("");
  const [position, setPosition] = useState<Position | null>(null);
  const [geoMessage, setGeoMessage] = useState<string | null>(null);

  // A ocorrência é registrada sobre uma área do mapeamento real, num raio de
  // até 30 km: o guarda-vidas escolhe entre o que está perto dele agora.
  const { beaches } = useNearbyBeaches();
  const effectiveBeachId = beachId || beaches[0]?.id || "";

  const counts = useMemo(() => {
    return occurrences.reduce(
      (acc, occurrence) => {
        acc.total += 1;
        if (occurrence.origin === "FIELD_ATTENDED") acc.field += 1;
        if (occurrence.origin === "CBM_193") acc.cbm += 1;
        if (occurrence.ciospStatus === "PENDING") acc.ciosp += 1;
        return acc;
      },
      { total: 0, field: 0, cbm: 0, ciosp: 0 },
    );
  }, [occurrences]);

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

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    const beach = beaches.find((item) => item.id === effectiveBeachId);
    if (!beach || !summary.trim()) return;

    const isClosedWithoutCiosp =
      outcome === "FALSE_ALARM" || outcome === "CANCELLED";

    const occurrence: OperatorOccurrence = {
      id: `occ-${Date.now()}`,
      number: occurrences.length + 1,
      clientRequestId: newRequestId(),
      origin,
      nature,
      outcome,
      ciospStatus: isClosedWithoutCiosp ? "NOT_REQUIRED" : "PENDING",
      occurredAt: new Date(occurredAt).toISOString(),
      beachId: beach.id,
      beachName: beach.name,
      sector: sector.trim() || undefined,
      reference: reference.trim() || beach.name,
      summary: summary.trim(),
      protocol: protocol.trim() || undefined,
      latitude: position?.latitude,
      longitude: position?.longitude,
      accuracyMeters: position?.accuracyMeters,
      createdAt: new Date().toISOString(),
    };

    setOccurrences((previous) => [occurrence, ...previous]);
    setOrigin("FIELD_ATTENDED");
    setNature("RESCUE");
    setOutcome("RESOLVED");
    setOccurredAt(toLocalInput(new Date()));
    setSector("");
    setReference("");
    setSummary("");
    setProtocol("");
    setPosition(null);
    setGeoMessage(null);
  };

  return (
    <div>
      <PageHeader
        title="Ocorrências"
        subtitle="Registre o atendimento do turno — inclusive o que você atendeu sem chamado no 193 ou no SOS — e prepare a transcrição para o CIOSP."
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800">
            <ClipboardList aria-hidden="true" className="size-3.5 text-ocean-600" />
            {counts.total} no turno
          </span>
        }
      />

      <dl className="mb-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
        <SummaryTile label="Registradas" value={counts.total} />
        <SummaryTile label="Em campo" value={counts.field} />
        <SummaryTile label="Do 193" value={counts.cbm} />
        <SummaryTile label="CIOSP pendente" value={counts.ciosp} />
      </dl>

      <form
        onSubmit={handleSubmit}
        className="mb-4 grid gap-3 rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm"
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-1.5 text-sm font-bold text-ocean-950">
            <Plus aria-hidden="true" className="size-4 text-ocean-600" />
            Nova ocorrência
          </h2>
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
            {position ? ` Precisão ${formatAccuracy(position.accuracyMeters)}.` : ""}
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
            Setor / trecho (opcional)
            <input
              type="text"
              maxLength={80}
              value={sector}
              onChange={(event) => setSector(event.target.value)}
              placeholder="Ex.: Setor Norte, em frente ao quiosque"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            Ponto de referência
            <input
              type="text"
              maxLength={160}
              value={reference}
              onChange={(event) => setReference(event.target.value)}
              placeholder="Ex.: ao lado do posto 02"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          {origin === "CBM_193" ? (
            <label className="block text-xs font-semibold text-ocean-800">
              Protocolo do 193 (opcional)
              <input
                type="text"
                maxLength={40}
                value={protocol}
                onChange={(event) => setProtocol(event.target.value)}
                placeholder="Nº informado pelo CBM"
                className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
              />
            </label>
          ) : null}

          <label className="block text-xs font-semibold text-ocean-800">
            Desfecho
            <select
              value={outcome}
              onChange={(event) =>
                setOutcome(event.target.value as OccurrenceOutcome)
              }
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            >
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

        <button
          type="submit"
          className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white transition hover:bg-ocean-900"
        >
          <Send aria-hidden="true" className="size-4" />
          Registrar ocorrência
        </button>
      </form>

      <h2 className="mb-2 text-xs font-bold uppercase tracking-wider text-ocean-500">
        Registradas no turno
      </h2>

      {occurrences.length === 0 ? (
        <p className="rounded-xl border border-dashed border-ocean-300 bg-white p-4 text-center text-xs font-semibold text-ocean-600">
          Nenhuma ocorrência registrada. Ao atender algo na praia, registre aqui
          para manter o histórico e a ficha do CIOSP.
        </p>
      ) : (
        <ul className="grid gap-2">
          {occurrences.map((occurrence) => (
            <li
              key={occurrence.id}
              className="rounded-xl border border-ocean-900/10 bg-white p-3 text-xs shadow-sm"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="flex flex-wrap items-center gap-1.5 font-bold text-ocean-950">
                  <span className="font-mono">#{occurrence.number}</span>
                  {NATURE_LABEL[occurrence.nature]}
                  <span className="rounded-full border border-ocean-200 bg-ocean-50 px-2 py-0.5 text-[10px] text-ocean-700">
                    {ORIGIN_LABEL[occurrence.origin]}
                  </span>
                </p>
                <span
                  className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[10px] font-bold ${
                    occurrence.ciospStatus === "PENDING"
                      ? "border-amber-300 bg-amber-50 text-amber-900"
                      : "border-ocean-200 bg-ocean-50 text-ocean-600"
                  }`}
                >
                  {occurrence.ciospStatus === "PENDING" ? (
                    "CIOSP pendente"
                  ) : (
                    <CheckCircle aria-hidden="true" className="size-3" />
                  )}
                  {occurrence.ciospStatus === "PENDING"
                    ? ""
                    : "Sem CIOSP"}
                </span>
              </div>
              <p className="mt-1 text-ocean-600">
                {occurrence.beachName}
                {occurrence.sector ? ` · ${occurrence.sector}` : ""} ·{" "}
                {formatDateTime(occurrence.occurredAt)} ·{" "}
                {OUTCOME_LABEL[occurrence.outcome]}
              </p>
              <p className="mt-1 text-ocean-700">{occurrence.summary}</p>
              <p className="mt-1 text-[10px] text-ocean-500">
                {occurrence.reference}
                {occurrence.latitude !== undefined &&
                occurrence.longitude !== undefined
                  ? ` · ${occurrence.latitude.toFixed(5)}, ${occurrence.longitude.toFixed(5)}`
                  : ""}
              </p>
            </li>
          ))}
        </ul>
      )}

      <p className="mt-3 text-[11px] leading-relaxed text-ocean-500">
        Esta tela ainda guarda o registro só no navegador. O envio real para
        `POST /api/v1/occurrences` e a ficha de transcrição do CIOSP entram na
        Etapa 6A.
      </p>
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
