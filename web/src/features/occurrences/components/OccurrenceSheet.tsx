import { useCallback, useEffect, useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle,
  Eye,
  Loader2,
  ShieldCheck,
  X,
} from "lucide-react";
import { useOverlay } from "@/shared/lib/overlay";
import { formatAccuracy, formatDateTime } from "@/shared/lib/format";
import {
  CIOSP_LABEL,
  NATURE_LABEL,
  ORIGIN_LABEL,
  OUTCOME_LABEL,
  STATUS_LABEL,
  isActiveOccurrence,
  type CloseOccurrenceInput,
  type OccurrenceDetail,
  type OccurrenceNature,
  type OccurrenceOutcome,
  type UpdateOccurrenceInput,
  type VictimView,
} from "@/features/occurrences/types";
import {
  closeOccurrence,
  fetchOccurrence,
  occurrenceErrorMessage,
  revealOccurrenceVictim,
  updateOccurrence,
} from "@/features/occurrences/api/occurrences";

interface OccurrenceSheetProps {
  readonly occurrenceId: string;
  readonly onClose: () => void;
  /** Chamado depois de encerrar/editar: as listas do console recarregam. */
  readonly onChanged: () => void;
}

/**
 * Ficha completa da ocorrência sobre o console.
 *
 * <p>Em atendimento: o guarda-vidas registra o relato, o desfecho e os dados
 * opcionais da vítima e encerra o atendimento. Encerrada: a coordenação (ou
 * quem atendeu) completa o que só apareceu depois — protocolo do CIOSP, vítima
 * identificada, correção do relato.
 */
export function OccurrenceSheet({
  occurrenceId,
  onClose,
  onChanged,
}: OccurrenceSheetProps) {
  const dialogRef = useRef<HTMLDialogElement | null>(null);
  const [detail, setDetail] = useState<OccurrenceDetail | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">(
    "loading",
  );
  const [message, setMessage] = useState<string | null>(null);
  const [victim, setVictim] = useState<VictimView | null>(null);

  useOverlay(true, dialogRef, onClose);

  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      try {
        const next = await fetchOccurrence(occurrenceId);
        if (cancelled) return;
        setDetail(next);
        setVictim(next.victim);
        setStatus("ready");
      } catch (error) {
        if (cancelled) return;
        setMessage(occurrenceErrorMessage(error));
        setStatus("error");
      }
    };

    void run();

    return () => {
      cancelled = true;
    };
  }, [occurrenceId, attempt]);

  const retry = useCallback(() => {
    setStatus("loading");
    setMessage(null);
    setAttempt((value) => value + 1);
  }, []);

  const saved = useCallback(
    (next: OccurrenceDetail, successMessage: string) => {
      setDetail(next);
      setVictim(next.victim);
      setMessage(successMessage);
      onChanged();
    },
    [onChanged],
  );

  const occurrence = detail?.occurrence;
  const active = occurrence ? isActiveOccurrence(occurrence) : false;

  return (
    <dialog
      ref={dialogRef}
      open
      aria-modal="true"
      aria-labelledby="occurrence-sheet-title"
      className="fixed inset-0 z-50 m-0 h-dvh max-h-none w-screen max-w-none overflow-y-auto bg-ocean-950/50 p-0 backdrop-blur-sm sm:flex sm:justify-center sm:p-6"
    >
      <div className="mx-auto min-h-dvh w-full bg-ocean-50 text-ocean-950 shadow-2xl sm:min-h-0 sm:max-w-2xl sm:rounded-2xl sm:border sm:border-ocean-900/10">
        <header className="sticky top-0 z-10 flex items-start justify-between gap-3 border-b border-ocean-900/10 bg-ocean-50/95 px-4 py-3 backdrop-blur">
          <div>
            <h2
              id="occurrence-sheet-title"
              className="text-base font-black text-ocean-950"
            >
              {occurrence ? `Ocorrência #${occurrence.number}` : "Ocorrência"}
            </h2>
            <p className="text-[11px] font-semibold text-ocean-500">
              {occurrence
                ? `${NATURE_LABEL[occurrence.nature]} · ${ORIGIN_LABEL[occurrence.origin]}`
                : "Carregando a ficha…"}
            </p>
          </div>
          <button
            type="button"
            data-autofocus
            onClick={onClose}
            aria-label="Fechar a ficha da ocorrência"
            className="rounded-lg border border-ocean-200 bg-white p-2 text-ocean-700 transition hover:bg-ocean-100"
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </header>

        <div className="grid gap-4 p-4 text-xs">
          {message ? (
            <output className="flex items-start gap-1.5 rounded-xl border border-ocean-200 bg-white px-3 py-2 text-[11px] font-semibold text-ocean-800">
              {status === "error" ? (
                <AlertTriangle
                  aria-hidden="true"
                  className="mt-0.5 size-3.5 shrink-0 text-amber-600"
                />
              ) : (
                <CheckCircle
                  aria-hidden="true"
                  className="mt-0.5 size-3.5 shrink-0 text-emerald-600"
                />
              )}
              {message}
            </output>
          ) : null}

          {status === "loading" ? (
            <p className="flex items-center justify-center gap-2 rounded-xl border border-ocean-200 bg-white p-6 font-semibold text-ocean-600">
              <Loader2 aria-hidden="true" className="size-4 animate-spin" />
              Carregando a ficha…
            </p>
          ) : null}

          {status === "error" ? (
            <button
              type="button"
              onClick={retry}
              className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white"
            >
              Tentar de novo
            </button>
          ) : null}

          {detail && occurrence ? (
            <>
              <section aria-label="Dados do atendimento" className="grid gap-2">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="rounded-full border border-ocean-200 bg-white px-2 py-0.5 text-[10px] font-bold text-ocean-700">
                    {STATUS_LABEL[occurrence.status]}
                  </span>
                  <span className="rounded-full border border-ocean-200 bg-white px-2 py-0.5 text-[10px] font-bold text-ocean-700">
                    {CIOSP_LABEL[occurrence.ciospStatus]}
                  </span>
                  {occurrence.outcome ? (
                    <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-800">
                      {OUTCOME_LABEL[occurrence.outcome]}
                    </span>
                  ) : null}
                </div>
                <dl className="grid grid-cols-2 gap-2 rounded-xl border border-ocean-900/10 bg-white p-3">
                  <Info
                    label="Local"
                    value={
                      occurrence.beachName
                        ? `${occurrence.beachName}${
                            occurrence.sectorLabel
                              ? ` · ${occurrence.sectorLabel}`
                              : ""
                          }`
                        : occurrence.latitude !== null
                          ? "Fora do mapeamento"
                          : "Não informado"
                    }
                  />
                  <Info
                    label="Fato"
                    value={formatDateTime(occurrence.occurredAt)}
                  />
                  <Info
                    label="Responsável"
                    value={occurrence.responderName ?? "Não identificado"}
                  />
                  <Info
                    label="Posto / unidade"
                    value={occurrence.postName ?? "Não informado"}
                  />
                  {occurrence.latitude !== null &&
                  occurrence.longitude !== null ? (
                    <Info
                      label="Coordenada do chamado"
                      value={`${occurrence.latitude.toFixed(5)}, ${occurrence.longitude.toFixed(5)}${
                        occurrence.accuracyMeters !== null
                          ? ` · ${formatAccuracy(occurrence.accuracyMeters)}`
                          : ""
                      }`}
                    />
                  ) : null}
                  {occurrence.closedAt ? (
                    <Info
                      label="Encerrada em"
                      value={`${formatDateTime(occurrence.closedAt)}${
                        detail.closedByName
                          ? ` · ${detail.closedByName}`
                          : ""
                      }`}
                    />
                  ) : null}
                  {detail.referenceText ? (
                    <Info
                      label="Ponto de referência"
                      value={detail.referenceText}
                    />
                  ) : null}
                  {detail.externalProtocol ? (
                    <Info
                      label="Protocolo do canal"
                      value={detail.externalProtocol}
                    />
                  ) : null}
                </dl>
              </section>

              {detail.summary ? (
                <section className="rounded-xl border border-ocean-900/10 bg-white p-3">
                  <h3 className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
                    Relato do atendimento
                  </h3>
                  <p className="mt-1 whitespace-pre-line text-ocean-800">
                    {detail.summary}
                  </p>
                  {detail.outcomeNotes ? (
                    <p className="mt-2 rounded-lg bg-ocean-50 p-2 text-ocean-700">
                      {detail.outcomeNotes}
                    </p>
                  ) : null}
                </section>
              ) : null}

              <VictimPanel
                occurrenceId={occurrenceId}
                victim={victim ?? detail.victim}
                onRevealed={(revealed) => setVictim(revealed)}
              />

              {active ? (
                <CloseForm
                  detail={detail}
                  onSaved={(next) =>
                    saved(next, "Atendimento encerrado. A ocorrência saiu da lista em atendimento.")
                  }
                />
              ) : (
                <EditForm
                  detail={detail}
                  onSaved={(next) =>
                    saved(next, "Ficha atualizada. A correção entrou no histórico da ocorrência.")
                  }
                />
              )}
            </>
          ) : null}
        </div>
      </div>
    </dialog>
  );
}

function Info({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div>
      <dt className="text-[10px] font-bold uppercase tracking-wider text-ocean-500">
        {label}
      </dt>
      <dd className="font-semibold text-ocean-900">{value}</dd>
    </div>
  );
}

interface FormProps {
  readonly detail: OccurrenceDetail;
  readonly onSaved: (detail: OccurrenceDetail) => void;
}

const INPUT_CLASS =
  "mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none";
const TEXTAREA_CLASS =
  "mt-1 w-full rounded-lg border border-ocean-200 bg-white px-3 py-2 text-xs font-normal focus:border-ocean-600 focus:outline-none";

function CloseForm({ detail, onSaved }: FormProps) {
  const [outcome, setOutcome] = useState<OccurrenceOutcome>("RESOLVED");
  const [nature, setNature] = useState<OccurrenceNature>(
    detail.occurrence.nature,
  );
  const [summary, setSummary] = useState(detail.summary ?? "");
  const [outcomeNotes, setOutcomeNotes] = useState(detail.outcomeNotes ?? "");
  const [ciospProtocol, setCiospProtocol] = useState("");
  const [externalProtocol, setExternalProtocol] = useState(
    detail.externalProtocol ?? "",
  );
  const [victimName, setVictimName] = useState("");
  const [victimAge, setVictimAge] = useState("");
  const [victimContact, setVictimContact] = useState("");
  const [victimCondition, setVictimCondition] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const input: CloseOccurrenceInput = {
      outcome,
      nature,
      summary: summary.trim(),
      outcomeNotes: outcomeNotes.trim() || undefined,
      ciospProtocol: ciospProtocol.trim() || undefined,
      externalProtocol: externalProtocol.trim() || undefined,
      victimName: victimName.trim() || undefined,
      victimAge: victimAge.trim() || undefined,
      victimContact: victimContact.trim() || undefined,
      victimCondition: victimCondition.trim() || undefined,
    };
    try {
      const next = await closeOccurrence(detail.occurrence.id, input);
      onSaved(next);
    } catch (caught) {
      setError(occurrenceErrorMessage(caught));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form
      onSubmit={submit}
      className="grid gap-3 rounded-xl border border-amber-300 bg-amber-50/40 p-3"
    >
      <div>
        <h3 className="text-xs font-black uppercase tracking-wider text-amber-900">
          Encerrar atendimento
        </h3>
        <p className="mt-0.5 text-[11px] text-ocean-600">
          O relato abaixo é o registro do que aconteceu. A ocorrência sai da
          lista em atendimento e o chamado do SOS é encerrado.
        </p>
      </div>

      {error ? (
        <output className="rounded-lg border border-coral-300 bg-coral-50 px-2.5 py-1.5 text-[11px] font-semibold text-coral-900">
          {error}
        </output>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-semibold text-ocean-800">
          Desfecho
          <select
            required
            value={outcome}
            onChange={(event) =>
              setOutcome(event.target.value as OccurrenceOutcome)
            }
            className={INPUT_CLASS}
          >
            {Object.entries(OUTCOME_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-xs font-semibold text-ocean-800">
          Natureza
          <select
            value={nature}
            onChange={(event) =>
              setNature(event.target.value as OccurrenceNature)
            }
            className={INPUT_CLASS}
          >
            {Object.entries(NATURE_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-xs font-semibold text-ocean-800">
          Protocolo do CIOSP (se já houver)
          <input
            type="text"
            maxLength={40}
            value={ciospProtocol}
            onChange={(event) => setCiospProtocol(event.target.value)}
            placeholder="Nº gerado no CIOSP"
            className={INPUT_CLASS}
          />
        </label>
        <label className="block text-xs font-semibold text-ocean-800">
          Protocolo do 193 / canal
          <input
            type="text"
            maxLength={40}
            value={externalProtocol}
            onChange={(event) => setExternalProtocol(event.target.value)}
            placeholder="Opcional"
            className={INPUT_CLASS}
          />
        </label>
      </div>

      <label className="block text-xs font-semibold text-ocean-800">
        O que aconteceu
        <textarea
          required
          rows={3}
          maxLength={2000}
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          placeholder="Relato do fato e do atendimento prestado."
          className={TEXTAREA_CLASS}
        />
      </label>

      <label className="block text-xs font-semibold text-ocean-800">
        Observações do desfecho
        <textarea
          rows={2}
          maxLength={2000}
          value={outcomeNotes}
          onChange={(event) => setOutcomeNotes(event.target.value)}
          placeholder="Encaminhamento, orientação à vítima, pendências."
          className={TEXTAREA_CLASS}
        />
      </label>

      <fieldset className="rounded-xl border border-ocean-200 bg-white p-3">
        <legend className="px-1 text-[10px] font-bold uppercase tracking-wider text-ocean-500">
          Dados da vítima (opcionais, cifrados)
        </legend>
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-ocean-800">
            Nome
            <input
              type="text"
              maxLength={120}
              value={victimName}
              onChange={(event) => setVictimName(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
          <label className="block text-xs font-semibold text-ocean-800">
            Idade
            <input
              type="text"
              maxLength={20}
              value={victimAge}
              onChange={(event) => setVictimAge(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
          <label className="block text-xs font-semibold text-ocean-800">
            Contato
            <input
              type="text"
              maxLength={80}
              value={victimContact}
              onChange={(event) => setVictimContact(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
          <label className="block text-xs font-semibold text-ocean-800">
            Condição
            <input
              type="text"
              maxLength={500}
              value={victimCondition}
              onChange={(event) => setVictimCondition(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
        </div>
        <p className="mt-2 flex items-start gap-1.5 text-[10px] leading-relaxed text-ocean-500">
          <ShieldCheck aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          Dados de vítima ficam cifrados e mascarados; ver exige motivo e gera
          auditoria.
        </p>
      </fieldset>

      <button
        type="submit"
        disabled={saving || summary.trim().length === 0}
        className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {saving ? (
          <Loader2 aria-hidden="true" className="size-4 animate-spin" />
        ) : (
          <CheckCircle aria-hidden="true" className="size-4" />
        )}
        {saving ? "Encerrando…" : "Encerrar atendimento"}
      </button>
    </form>
  );
}

function EditForm({ detail, onSaved }: FormProps) {
  const [nature, setNature] = useState<OccurrenceNature>(
    detail.occurrence.nature,
  );
  const [summary, setSummary] = useState(detail.summary ?? "");
  const [outcomeNotes, setOutcomeNotes] = useState(detail.outcomeNotes ?? "");
  const [referenceText, setReferenceText] = useState(
    detail.referenceText ?? "",
  );
  const [externalProtocol, setExternalProtocol] = useState(
    detail.externalProtocol ?? "",
  );
  const [ciospProtocol, setCiospProtocol] = useState(
    detail.ciospProtocol ?? "",
  );
  const [victimName, setVictimName] = useState("");
  const [victimAge, setVictimAge] = useState("");
  const [victimContact, setVictimContact] = useState("");
  const [victimCondition, setVictimCondition] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    const input: UpdateOccurrenceInput = {
      nature,
      summary: summary.trim() || undefined,
      outcomeNotes: outcomeNotes.trim() || undefined,
      referenceText: referenceText.trim() || undefined,
      externalProtocol: externalProtocol.trim() || undefined,
      ciospProtocol: ciospProtocol.trim() || undefined,
      victimName: victimName.trim() || undefined,
      victimAge: victimAge.trim() || undefined,
      victimContact: victimContact.trim() || undefined,
      victimCondition: victimCondition.trim() || undefined,
    };
    try {
      const next = await updateOccurrence(detail.occurrence.id, input);
      onSaved(next);
    } catch (caught) {
      setError(occurrenceErrorMessage(caught));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form
      onSubmit={submit}
      className="grid gap-3 rounded-xl border border-ocean-900/10 bg-white p-3"
    >
      <div>
        <h3 className="text-xs font-black uppercase tracking-wider text-ocean-700">
          Completar ou corrigir a ficha
        </h3>
        <p className="mt-0.5 text-[11px] text-ocean-600">
          Use para registrar o protocolo do CIOSP, a vítima que foi identificada
          depois ou corrigir o relato. Cada alteração entra no histórico.
        </p>
      </div>

      {error ? (
        <output className="rounded-lg border border-coral-300 bg-coral-50 px-2.5 py-1.5 text-[11px] font-semibold text-coral-900">
          {error}
        </output>
      ) : null}

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-xs font-semibold text-ocean-800">
          Natureza
          <select
            value={nature}
            onChange={(event) =>
              setNature(event.target.value as OccurrenceNature)
            }
            className={INPUT_CLASS}
          >
            {Object.entries(NATURE_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-xs font-semibold text-ocean-800">
          Protocolo do CIOSP
          <input
            type="text"
            maxLength={40}
            value={ciospProtocol}
            onChange={(event) => setCiospProtocol(event.target.value)}
            placeholder="Preencha quando o CBM gerar o número"
            className={INPUT_CLASS}
          />
        </label>
        <label className="block text-xs font-semibold text-ocean-800">
          Protocolo do 193 / canal
          <input
            type="text"
            maxLength={40}
            value={externalProtocol}
            onChange={(event) => setExternalProtocol(event.target.value)}
            className={INPUT_CLASS}
          />
        </label>
        <label className="block text-xs font-semibold text-ocean-800">
          Ponto de referência
          <input
            type="text"
            maxLength={160}
            value={referenceText}
            onChange={(event) => setReferenceText(event.target.value)}
            className={INPUT_CLASS}
          />
        </label>
      </div>

      <label className="block text-xs font-semibold text-ocean-800">
        Relato do atendimento
        <textarea
          rows={3}
          maxLength={2000}
          value={summary}
          onChange={(event) => setSummary(event.target.value)}
          className={TEXTAREA_CLASS}
        />
      </label>

      <label className="block text-xs font-semibold text-ocean-800">
        Observações do desfecho
        <textarea
          rows={2}
          maxLength={2000}
          value={outcomeNotes}
          onChange={(event) => setOutcomeNotes(event.target.value)}
          className={TEXTAREA_CLASS}
        />
      </label>

      <fieldset className="rounded-xl border border-ocean-200 bg-ocean-50/60 p-3">
        <legend className="px-1 text-[10px] font-bold uppercase tracking-wider text-ocean-500">
          Acrescentar dados de vítima
        </legend>
        <p className="mb-2 text-[10px] text-ocean-600">
          Os dados já gravados não são exibidos aqui. Preencha somente o que
          precisa ser incluído ou corrigido.
        </p>
        <div className="grid gap-2 sm:grid-cols-2">
          <label className="block text-xs font-semibold text-ocean-800">
            Nome
            <input
              type="text"
              maxLength={120}
              value={victimName}
              onChange={(event) => setVictimName(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
          <label className="block text-xs font-semibold text-ocean-800">
            Idade
            <input
              type="text"
              maxLength={20}
              value={victimAge}
              onChange={(event) => setVictimAge(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
          <label className="block text-xs font-semibold text-ocean-800">
            Contato
            <input
              type="text"
              maxLength={80}
              value={victimContact}
              onChange={(event) => setVictimContact(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
          <label className="block text-xs font-semibold text-ocean-800">
            Condição
            <input
              type="text"
              maxLength={500}
              value={victimCondition}
              onChange={(event) => setVictimCondition(event.target.value)}
              className={INPUT_CLASS}
            />
          </label>
        </div>
      </fieldset>

      <button
        type="submit"
        disabled={saving}
        className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {saving ? (
          <Loader2 aria-hidden="true" className="size-4 animate-spin" />
        ) : (
          <CheckCircle aria-hidden="true" className="size-4" />
        )}
        {saving ? "Salvando…" : "Salvar alterações"}
      </button>
    </form>
  );
}

function VictimPanel({
  occurrenceId,
  victim,
  onRevealed,
}: {
  readonly occurrenceId: string;
  readonly victim: VictimView;
  readonly onRevealed: (victim: VictimView) => void;
}) {
  const [reason, setReason] = useState("");
  const [revealing, setRevealing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!victim.recorded) {
    return (
      <p className="rounded-xl border border-dashed border-ocean-300 bg-white p-3 text-[11px] font-semibold text-ocean-500">
        Nenhum dado de vítima registrado. Se a vítima for identificada depois,
        use “Completar ou corrigir a ficha”.
      </p>
    );
  }

  const reveal = async (event: React.FormEvent) => {
    event.preventDefault();
    setRevealing(true);
    setError(null);
    try {
      const revealed = await revealOccurrenceVictim(
        occurrenceId,
        reason.trim(),
      );
      onRevealed(revealed);
    } catch (caught) {
      setError(occurrenceErrorMessage(caught));
    } finally {
      setRevealing(false);
    }
  };

  return (
    <section className="rounded-xl border border-ocean-200 bg-white p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-ocean-500">
          <ShieldCheck aria-hidden="true" className="size-3.5" />
          Dados da vítima
        </h3>
        {victim.revealed ? (
          <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-800">
            Revelado e auditado
          </span>
        ) : null}
      </div>

      {victim.revealed ? (
        <dl className="mt-2 grid grid-cols-2 gap-2">
          <Info label="Nome" value={victim.name ?? "—"} />
          <Info label="Idade" value={victim.age ?? "—"} />
          <Info label="Contato" value={victim.contact ?? "—"} />
          <Info label="Condição" value={victim.condition ?? "—"} />
        </dl>
      ) : (
        <>
          <p className="mt-1 text-[11px] text-ocean-600">
            Há dados registrados, mas eles estão protegidos. A revelação exige
            motivo e fica no histórico da ocorrência.
          </p>
          <details className="mt-2">
            <summary className="flex cursor-pointer items-center gap-1.5 text-[11px] font-bold text-ocean-700">
              <Eye aria-hidden="true" className="size-3.5" />
              Revelar dados da vítima
            </summary>
            <form onSubmit={reveal} className="mt-2 grid gap-2">
              {error ? (
                <output className="rounded-lg border border-coral-300 bg-coral-50 px-2.5 py-1.5 text-[11px] font-semibold text-coral-900">
                  {error}
                </output>
              ) : null}
              <label className="block text-xs font-semibold text-ocean-800">
                Motivo
                <input
                  type="text"
                  required
                  maxLength={200}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="Ex.: preenchimento da ficha do CIOSP"
                  className={INPUT_CLASS}
                />
              </label>
              <button
                type="submit"
                disabled={revealing || reason.trim().length === 0}
                className="inline-flex h-8 w-fit items-center gap-1.5 rounded-lg border border-ocean-300 bg-ocean-50 px-3 text-[11px] font-bold text-ocean-800 disabled:opacity-60"
              >
                {revealing ? (
                  <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
                ) : (
                  <Eye aria-hidden="true" className="size-3.5" />
                )}
                {revealing ? "Revelando…" : "Revelar"}
              </button>
            </form>
          </details>
        </>
      )}
    </section>
  );
}
