import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
  AlertTriangle,
  CheckCircle,
  MapPin,
  MapPinned,
  RefreshCw,
  RotateCcw,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import {
  formatAccuracy,
  formatDateTime,
  formatDistance,
} from "@/shared/lib/format";
import { FLAG_COLORS, FLAG_LABELS } from "@/shared/lib/flags";
import { sanitizeText } from "@/shared/lib/sanitize";
import { sectorLabel, useBeachMap } from "@/features/beaches";
import { SignalPhotos } from "@/features/signals";
import type { UnmappedSignal, UnmappedSignalStatus } from "@/features/signals";
import { fetchBathingAreaOptions } from "@/features/management/api/bathing-areas";
import {
  decideUnmappedSignal,
  unmappedSignalErrorMessage,
} from "@/features/management/api/unmapped-signals";
import {
  useUnmappedSignals,
  type UnmappedSignalsFilter,
} from "@/features/management/hooks/use-unmapped-signals";
import type {
  BathingAreaOption,
  UnmappedSignalDecision,
} from "@/features/management/types";

const STATUS_LABEL: Record<UnmappedSignalStatus, string> = {
  PENDING: "Aguardando triagem",
  IDENTIFIED: "Identificada",
  NEEDS_MAPPING: "Precisa de mapeamento",
  DISCARDED: "Descartada",
};

const STATUS_STYLE: Record<UnmappedSignalStatus, string> = {
  PENDING: "border-amber-300 bg-amber-50 text-amber-900",
  IDENTIFIED: "border-emerald-300 bg-emerald-50 text-emerald-900",
  NEEDS_MAPPING: "border-sky-300 bg-sky-50 text-sky-900",
  DISCARDED: "border-ocean-200 bg-ocean-50 text-ocean-700",
};

type DecisionMode = Exclude<UnmappedSignalDecision, "PENDING"> | null;

/**
 * Triagem das sinalizações registradas fora do mapeamento (RF12/RN17).
 *
 * <p>O guarda-vidas pode estar numa praia nova, num trecho de rio sem KML ou
 * numa barragem que passou a receber banhistas. O registro chega aqui em vez de
 * ser recusado: a coordenação confere o ponto e as fotos, identifica uma área
 * existente, pede um novo mapeamento ou descarta o registro. Nada é publicado
 * no mapa enquanto a triagem não decide — e decidir também não publica
 * bandeira: a sinalização continua sendo lançada em campo.
 */
export function ManagementUnmappedSignalsPage() {
  const [filter, setFilter] = useState<UnmappedSignalsFilter>("PENDING");
  const { signals, status, message, reload } = useUnmappedSignals(filter);
  const [areas, setAreas] = useState<readonly BathingAreaOption[]>([]);
  const [areasError, setAreasError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const next = await fetchBathingAreaOptions();
        if (!cancelled) {
          setAreas(next);
          setAreasError(null);
        }
      } catch (error) {
        if (!cancelled) setAreasError(unmappedSignalErrorMessage(error));
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div>
      <PageHeader
        title="Áreas não identificadas"
        subtitle="Sinalizações registradas em pontos que ainda não estão no mapeamento. Identifique a área, peça um novo KML ou descarte o registro."
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

      <div className="mb-3 flex flex-wrap gap-1.5">
        {(
          [
            { value: "PENDING", label: "Aguardando triagem" },
            { value: "ALL", label: "Todos os registros" },
          ] as const
        ).map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => setFilter(option.value)}
            aria-pressed={filter === option.value}
            className={[
              "min-h-8 rounded-full px-3 py-1 text-xs font-bold transition",
              filter === option.value
                ? "bg-ocean-950 text-white"
                : "border border-ocean-200 bg-white text-ocean-800 hover:bg-ocean-50",
            ].join(" ")}
          >
            {option.label}
          </button>
        ))}
      </div>

      {areasError ? (
        <output className="mb-2 block rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900">
          {areasError} A identificação em área existente fica indisponível até a
          lista carregar.
        </output>
      ) : null}

      {message ? (
        <output className="mb-2 block rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-900">
          {message} A lista abaixo é a última que chegou.
        </output>
      ) : null}

      {status === "loading" && signals.length === 0 ? (
        <p className="rounded-xl border border-ocean-200 bg-white p-4 text-center text-xs font-semibold text-ocean-600">
          Carregando os registros…
        </p>
      ) : signals.length === 0 ? (
        <Card className="border-dashed bg-ocean-50/40">
          <p className="text-sm font-bold text-ocean-950">
            {filter === "PENDING"
              ? "Nenhum registro aguardando triagem"
              : "Nenhuma sinalização fora do mapeamento"}
          </p>
          <p className="mt-1 text-[11px] leading-snug text-ocean-700">
            Um lançamento por localização aparece aqui quando o ponto não está
            em nenhuma área de banho ativa. Nada é publicado no mapa até a
            triagem.
          </p>
        </Card>
      ) : (
        <ul className="grid gap-2">
          {signals.map((signal) => (
            <li key={signal.id}>
              <UnmappedSignalCard
                signal={signal}
                areas={areas}
                onDecided={reload}
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

interface UnmappedSignalCardProps {
  readonly signal: UnmappedSignal;
  readonly areas: readonly BathingAreaOption[];
  readonly onDecided: () => void;
}

function UnmappedSignalCard({
  signal,
  areas,
  onDecided,
}: UnmappedSignalCardProps) {
  const [mode, setMode] = useState<DecisionMode>(null);
  const [beachId, setBeachId] = useState("");
  const [sectorId, setSectorId] = useState("");
  const [notes, setNotes] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pending = signal.status === "PENDING";
  const effectiveBeachId =
    beachId || signal.nearestBeachId || areas[0]?.id || "";
  const beachMap = useBeachMap(mode === "IDENTIFIED" ? effectiveBeachId : null);

  const sectors = useMemo(
    () =>
      [...(beachMap.map?.sectors ?? [])].sort(
        (a, b) => a.displayOrder - b.displayOrder,
      ),
    [beachMap.map],
  );
  const effectiveSectorId = sectors.some((sector) => sector.id === sectorId)
    ? sectorId
    : "";

  const chooseMode = (next: DecisionMode) => {
    setMode((current) => (current === next ? null : next));
    setError(null);
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (mode === null) return;
    if (mode === "IDENTIFIED" && !effectiveBeachId) {
      setError("Escolha a área de banho que corresponde ao local.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await decideUnmappedSignal(signal.id, {
        decision: mode,
        beachId: mode === "IDENTIFIED" ? effectiveBeachId : null,
        sectorId: mode === "IDENTIFIED" ? effectiveSectorId || null : null,
        notes,
      });
      setMode(null);
      setNotes("");
      setSectorId("");
      onDecided();
    } catch (caught) {
      setError(unmappedSignalErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const reopen = async () => {
    setBusy(true);
    setError(null);
    try {
      await decideUnmappedSignal(signal.id, { decision: "PENDING" });
      onDecided();
    } catch (caught) {
      setError(unmappedSignalErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="grid gap-2">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="flex items-center gap-1.5 text-sm font-black text-ocean-950">
            <span
              aria-hidden="true"
              className="size-3.5 rounded-full border border-ocean-900/10"
              style={{ backgroundColor: FLAG_COLORS[signal.flag] }}
            />
            {FLAG_LABELS[signal.flag]}
            <span className="text-[11px] font-bold text-ocean-500">
              registrada em {formatDateTime(signal.createdAt)}
            </span>
          </p>
          <p className="mt-1 text-[11px] text-ocean-600">
            Matrícula {sanitizeText(signal.reportedByBadge ?? "não informada", 40)}
            {signal.reportedPostName
              ? ` • ${sanitizeText(signal.reportedPostName, 80)}`
              : ""}
          </p>
        </div>
        <span
          className={[
            "rounded-full border px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wide",
            STATUS_STYLE[signal.status],
          ].join(" ")}
        >
          {STATUS_LABEL[signal.status]}
        </span>
      </div>

      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-ocean-700">
        <span className="inline-flex items-center gap-1">
          <MapPin aria-hidden="true" className="size-3.5 text-ocean-500" />
          {signal.latitude.toFixed(5)}, {signal.longitude.toFixed(5)}
        </span>
        <span>
          {formatAccuracy(signal.accuracyMeters ?? undefined)}
        </span>
        <span className="inline-flex items-center gap-1">
          <MapPinned aria-hidden="true" className="size-3.5 text-ocean-500" />
          {signal.nearestBeachName
            ? `Área mais próxima: ${sanitizeText(signal.nearestBeachName, 80)} (${formatDistance(signal.nearestBeachDistanceMeters)})`
            : "Nenhuma área mapeada próxima"}
        </span>
      </p>

      {signal.notes ? (
        <p className="rounded-xl bg-ocean-50/60 px-2.5 py-1.5 text-[11px] leading-snug text-ocean-800">
          {sanitizeText(signal.notes, 400)}
        </p>
      ) : null}

      <SignalPhotos
        urls={signal.photoUrls}
        label={`área não identificada ${signal.reportedByBadge ?? ""}`.trim()}
        compact
      />

      {signal.status === "IDENTIFIED" || signal.status === "NEEDS_MAPPING" ? (
        <p className="text-[11px] leading-snug text-ocean-700">
          <span className="font-bold">
            {signal.status === "IDENTIFIED"
              ? `${sanitizeText(signal.beachName ?? "Área identificada", 80)}${
                  signal.sectorLabel
                    ? ` · ${sanitizeText(signal.sectorLabel, 80)}`
                    : " · praia inteira"
                }`
              : "Marcada como local que precisa entrar no mapeamento."}
          </span>
          {signal.decisionNotes
            ? ` — ${sanitizeText(signal.decisionNotes, 500)}`
            : ""}
          {signal.decidedAt ? (
            <span className="text-ocean-500">
              {" "}
              (decidida em {formatDateTime(signal.decidedAt)})
            </span>
          ) : null}
        </p>
      ) : null}

      {signal.status === "DISCARDED" ? (
        <p className="text-[11px] leading-snug text-ocean-700">
          Descartada
          {signal.decisionNotes
            ? ` — ${sanitizeText(signal.decisionNotes, 500)}`
            : ""}
          {signal.decidedAt ? (
            <span className="text-ocean-500">
              {" "}
              em {formatDateTime(signal.decidedAt)}
            </span>
          ) : null}
        </p>
      ) : null}

      {error ? (
        <output className="flex items-start gap-1.5 rounded-xl border border-coral-200 bg-coral-50 px-2.5 py-1.5 text-[11px] font-semibold text-ocean-950">
          <AlertTriangle
            aria-hidden="true"
            className="mt-0.5 size-3.5 shrink-0 text-coral-600"
          />
          {error}
        </output>
      ) : null}

      {pending ? (
        <form onSubmit={submit} className="grid gap-2 border-t border-ocean-100 pt-2">
          <fieldset className="grid gap-1">
            <legend className="text-[11px] font-bold text-ocean-800">
              O que fazer com o registro
            </legend>
            <div className="flex flex-wrap gap-1.5">
              <DecisionOption
                checked={mode === "IDENTIFIED"}
                onSelect={() => chooseMode("IDENTIFIED")}
                label="Identificar na área"
              />
              <DecisionOption
                checked={mode === "NEEDS_MAPPING"}
                onSelect={() => chooseMode("NEEDS_MAPPING")}
                label="Precisa de mapeamento"
              />
              <DecisionOption
                checked={mode === "DISCARDED"}
                onSelect={() => chooseMode("DISCARDED")}
                label="Descartar"
              />
            </div>
          </fieldset>

          {mode === "IDENTIFIED" ? (
            areas.length === 0 ? (
              <p className="text-[11px] font-semibold text-coral-700">
                Nenhuma área de banho ativa carregada. Atualize a página ou
                importe o mapa antes de identificar.
              </p>
            ) : (
              <div className="grid gap-1.5 sm:grid-cols-2">
                <label className="grid gap-1 text-[11px] font-bold text-ocean-800">
                  Área de banho
                  <select
                    value={effectiveBeachId}
                    onChange={(event) => {
                      setBeachId(event.target.value);
                      setSectorId("");
                    }}
                    className="min-h-9 rounded-xl border border-ocean-200 bg-white px-2.5 text-xs font-medium text-ocean-950"
                  >
                    {areas.map((area) => (
                      <option key={area.id} value={area.id}>
                        {area.name} — {area.municipality}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="grid gap-1 text-[11px] font-bold text-ocean-800">
                  Setor
                  <select
                    value={effectiveSectorId}
                    onChange={(event) => setSectorId(event.target.value)}
                    className="min-h-9 rounded-xl border border-ocean-200 bg-white px-2.5 text-xs font-medium text-ocean-950"
                  >
                    <option value="">Praia inteira</option>
                    {sectors.map((sector) => (
                      <option key={sector.id} value={sector.id}>
                        {sectorLabel(sector)}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            )
          ) : null}

          {mode !== null ? (
            <>
              <label className="grid gap-1 text-[11px] font-bold text-ocean-800">
                Justificativa (opcional)
                <input
                  type="text"
                  value={notes}
                  maxLength={500}
                  onChange={(event) => setNotes(event.target.value)}
                  placeholder="Ex.: é o setor sul da praia do Restinho"
                  className="min-h-9 rounded-xl border border-ocean-200 bg-white px-2.5 text-xs font-medium text-ocean-950 placeholder:text-ocean-400"
                />
              </label>
              <button
                type="submit"
                disabled={busy}
                className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
              >
                <CheckCircle aria-hidden="true" className="size-3.5" />
                {busy ? "Registrando" : "Confirmar decisão"}
              </button>
            </>
          ) : null}
        </form>
      ) : (
        <button
          type="button"
          disabled={busy}
          onClick={() => void reopen()}
          className="inline-flex min-h-9 w-fit items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:opacity-60"
        >
          <RotateCcw aria-hidden="true" className="size-3.5" />
          {busy ? "Reabrindo" : "Reabrir para triagem"}
        </button>
      )}
    </Card>
  );
}

interface DecisionOptionProps {
  readonly checked: boolean;
  readonly onSelect: () => void;
  readonly label: string;
}

function DecisionOption({ checked, onSelect, label }: DecisionOptionProps) {
  return (
    <label
      className={[
        "inline-flex cursor-pointer items-center gap-1.5 rounded-xl border px-2.5 py-1 text-[11px] font-bold transition",
        checked
          ? "border-ocean-950 bg-ocean-950 text-white"
          : "border-ocean-200 bg-white text-ocean-800 hover:bg-ocean-50",
      ].join(" ")}
    >
      <input
        type="radio"
        name="unmapped-decision"
        checked={checked}
        onChange={onSelect}
        className="size-3.5 accent-ocean-950"
      />
      {label}
    </label>
  );
}
