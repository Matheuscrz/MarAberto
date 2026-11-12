import { useRef, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  FileUp,
  Loader2,
  MapPinned,
  RefreshCw,
  ShieldCheck,
  Upload,
} from "lucide-react";
import { Card, PageHeader } from "@/shared/components/Card";
import { ApiError } from "@/shared/lib/api-client";
import { isNativeApp } from "@/shared/lib/platform";
import { importBathingAreas } from "@/features/management/api/bathing-areas";
import type {
  FeatureAction,
  FeatureChange,
  ImportMode,
  ImportReport,
} from "@/features/management/types";

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const STATE_PATTERN = /^[A-Za-z]{2}$/;

const ACTION_LABEL: Record<FeatureAction, string> = {
  CREATED: "Novo",
  UPDATED: "Alterado",
  UNCHANGED: "Sem mudança",
  MISSING: "Ausente",
  REJECTED: "Rejeitado",
};

const ACTION_STYLE: Record<FeatureAction, string> = {
  CREATED: "border-emerald-300 bg-emerald-50 text-emerald-950",
  UPDATED: "border-amber-300 bg-amber-50 text-amber-950",
  UNCHANGED: "border-ocean-200 bg-ocean-50 text-ocean-700",
  MISSING: "border-red-300 bg-red-50 text-red-950",
  REJECTED: "border-red-300 bg-red-50 text-red-950",
};

const FIELD_LABEL: Record<string, string> = {
  name: "nome",
  display_order: "ordem",
  geometry: "geometria",
  beach_id: "área",
  active: "ativo",
};

/**
 * Importação/atualização do mapa de áreas de banho (ADR-016 + ADR-021).
 *
 * <p>Esta é a primeira aba da coordenação. O fluxo tem duas fases de propósito:
 * a análise (`dry-run`) mostra o que mudaria e a aplicação exige confirmação
 * explícita. O arquivo não substitui o mapa pela metade: o servidor aplica o
 * lote inteiro em uma transação, preserva os UUIDs e nunca apaga feições —
 * quando o modo é `SYNC`, o que sumiu do arquivo apenas fica inativo.
 *
 * <p>O upload multipart só funciona no navegador/PWA: no WebView o corpo vai
 * pelo HTTP nativo, que não serializa `FormData`. A importação é tarefa de
 * coordenação, feita em um computador.
 */
export function ImportMapPage() {
  const fileInput = useRef<HTMLInputElement>(null);
  const [stateCode, setStateCode] = useState("SE");
  const [file, setFile] = useState<File | null>(null);
  const [mode, setMode] = useState<ImportMode>("UPSERT");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState<"analyze" | "apply" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [showUnchanged, setShowUnchanged] = useState(false);

  const native = isNativeApp();

  /** Qualquer mudança no formulário invalida a análise anterior. */
  const resetReport = () => {
    setReport(null);
    setConfirmed(false);
  };

  const selectFile = (next: File | null) => {
    setFile(next);
    resetReport();
    setError(null);
  };

  const submit = async (phase: "analyze" | "apply") => {
    if (busy) return;
    if (!file) {
      setError("Escolha o arquivo KML exportado do QGIS.");
      return;
    }
    if (!STATE_PATTERN.test(stateCode.trim())) {
      setError("Informe a UF com duas letras (ex.: SE).");
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      setError("O arquivo passa de 5 MB. Exporte o KML sem imagens.");
      return;
    }

    setError(null);
    setBusy(phase);
    try {
      const result = await importBathingAreas({
        file,
        state: stateCode.trim().toUpperCase(),
        mode,
        dryRun: phase === "analyze",
        confirm: phase === "apply",
      });
      setReport(result);
      setConfirmed(false);
    } catch (cause) {
      setError(importErrorMessage(cause));
    } finally {
      setBusy(null);
    }
  };

  const changes = (report?.changes ?? []).filter(
    (change) => showUnchanged || change.action !== "UNCHANGED",
  );
  const pendingChanges =
    report !== null &&
    report.dryRun &&
    report.created + report.updated + report.missing > 0;

  return (
    <div>
      <PageHeader
        title="Importar mapa de áreas de banho"
        subtitle="Envie o KML exportado do QGIS. O arquivo atualiza as praias, rios e barragens e seus setores — nada é apagado."
      />

      {native ? (
        <p
          role="alert"
          className="mb-4 flex items-start gap-2 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-xs font-semibold text-amber-950"
        >
          <AlertTriangle aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          O envio de arquivos não está disponível no aplicativo. Abra a gestão
          pelo navegador para importar o mapa.
        </p>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <Card>
          <h2 className="flex items-center gap-2 text-sm font-black text-ocean-950">
            <Upload aria-hidden="true" className="size-4 text-ocean-600" />
            Arquivo e UF
          </h2>
          <p className="mt-1 text-xs leading-relaxed text-ocean-700">
            A UF é obrigatória mesmo quando o KML já traz o estado: ela entra na
            identidade de cada feição. Um arquivo de outro estado é recusado.
          </p>

          <form
            className="mt-4 grid gap-3"
            onSubmit={(event) => {
              event.preventDefault();
              void submit("analyze");
            }}
          >
            <div>
              <label
                htmlFor="import-state"
                className="block text-xs font-bold text-ocean-950"
              >
                UF do mapeamento
              </label>
              <input
                id="import-state"
                type="text"
                required
                maxLength={2}
                autoComplete="off"
                spellCheck={false}
                value={stateCode}
                onChange={(event) => {
                  setStateCode(event.target.value.toUpperCase());
                  resetReport();
                }}
                className="mt-1 h-10 w-24 rounded-xl border border-ocean-200 bg-white px-3 text-sm font-bold tracking-widest text-ocean-950 uppercase focus:border-ocean-600 focus:outline-none"
              />
            </div>

            <div>
              <label
                htmlFor="import-file"
                className="block text-xs font-bold text-ocean-950"
              >
                Arquivo KML
              </label>
              <input
                id="import-file"
                ref={fileInput}
                type="file"
                required
                accept=".kml,application/vnd.google-earth.kml+xml,application/xml,text/xml"
                onChange={(event) =>
                  selectFile(event.target.files?.[0] ?? null)
                }
                className="mt-1 block w-full cursor-pointer rounded-xl border border-dashed border-ocean-300 bg-ocean-50/60 px-3 py-2.5 text-xs font-semibold text-ocean-800 file:mr-3 file:rounded-lg file:border-0 file:bg-ocean-950 file:px-3 file:py-1.5 file:text-xs file:font-bold file:text-white hover:border-ocean-500"
              />
              <p className="mt-1 text-[11px] text-ocean-600">
                {file
                  ? `${file.name} · ${formatBytes(file.size)}`
                  : "Máximo de 5 MB. O QGIS exporta KML ou GeoJSON — use o KML."}
              </p>
            </div>

            <fieldset>
              <legend className="text-xs font-bold text-ocean-950">
                Como tratar o que sumiu do arquivo
              </legend>
              <div className="mt-1 grid gap-2">
                <ModeOption
                  checked={mode === "UPSERT"}
                  onSelect={() => {
                    setMode("UPSERT");
                    resetReport();
                  }}
                  title="Criar e atualizar (recomendado)"
                  description="Mantém tudo o que já existe. Feições ausentes do arquivo continuam ativas."
                />
                <ModeOption
                  checked={mode === "SYNC"}
                  onSelect={() => {
                    setMode("SYNC");
                    resetReport();
                  }}
                  title="Sincronizar"
                  description="Além de criar e atualizar, desativa o que sumiu do arquivo. A linha continua no banco e volta se a feição reaparecer."
                />
              </div>
            </fieldset>

            {error ? (
              <p
                role="alert"
                className="rounded-xl border border-coral-200 bg-coral-50 p-2.5 text-xs font-semibold text-coral-900"
              >
                {error}
              </p>
            ) : null}

            <button
              type="submit"
              disabled={busy !== null || native}
              className="inline-flex min-h-10 items-center justify-center gap-2 rounded-xl bg-ocean-950 px-4 text-sm font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
            >
              {busy === "analyze" ? (
                <Loader2 aria-hidden="true" className="size-4 animate-spin" />
              ) : (
                <RefreshCw aria-hidden="true" className="size-4" />
              )}
              {busy === "analyze" ? "Analisando..." : "Analisar sem aplicar"}
            </button>
          </form>
        </Card>

        <Card>
          <h2 className="flex items-center gap-2 text-sm font-black text-ocean-950">
            <MapPinned aria-hidden="true" className="size-4 text-ocean-600" />
            Resultado
          </h2>

          {!report ? (
            <div className="mt-8 grid place-items-center gap-2 rounded-xl border border-dashed border-ocean-300 px-4 py-10 text-center">
              <FileUp aria-hidden="true" className="size-7 text-ocean-400" />
              <p className="text-xs font-semibold text-ocean-600">
                Escolha o arquivo e clique em “Analisar sem aplicar”.
              </p>
              <p className="max-w-sm text-[11px] text-ocean-500">
                A análise não grava nada no mapa e não entra na auditoria. Ela
                mostra exatamente o que a aplicação faria.
              </p>
            </div>
          ) : (
            <ReportView
              report={report}
              changes={changes}
              showUnchanged={showUnchanged}
              onToggleUnchanged={() => setShowUnchanged((value) => !value)}
              confirmed={confirmed}
              onConfirm={setConfirmed}
              busy={busy}
              canApply={!native && pendingChanges === true}
              onApply={() => {
                void submit("apply");
              }}
              onAnalyzeAgain={() => {
                void submit("analyze");
              }}
            />
          )}
        </Card>
      </div>
    </div>
  );
}

interface ModeOptionProps {
  readonly checked: boolean;
  readonly onSelect: () => void;
  readonly title: string;
  readonly description: string;
}

function ModeOption({ checked, onSelect, title, description }: ModeOptionProps) {
  return (
    <label
      className={[
        "flex cursor-pointer items-start gap-2.5 rounded-xl border px-3 py-2 transition",
        checked
          ? "border-ocean-950 bg-ocean-50"
          : "border-ocean-200 bg-white hover:bg-ocean-50/60",
      ].join(" ")}
    >
      <input
        type="radio"
        name="import-mode"
        checked={checked}
        onChange={onSelect}
        className="mt-0.5 size-4 accent-ocean-950"
      />
      <span className="leading-tight">
        <span className="block text-xs font-bold text-ocean-950">{title}</span>
        <span className="block text-[11px] text-ocean-700">{description}</span>
      </span>
    </label>
  );
}

interface ReportViewProps {
  readonly report: ImportReport;
  readonly changes: readonly FeatureChange[];
  readonly showUnchanged: boolean;
  readonly onToggleUnchanged: () => void;
  readonly confirmed: boolean;
  readonly onConfirm: (confirmed: boolean) => void;
  readonly busy: "analyze" | "apply" | null;
  readonly canApply: boolean;
  readonly onApply: () => void;
  readonly onAnalyzeAgain: () => void;
}

function ReportView({
  report,
  changes,
  showUnchanged,
  onToggleUnchanged,
  confirmed,
  onConfirm,
  busy,
  canApply,
  onApply,
  onAnalyzeAgain,
}: ReportViewProps) {
  const unchanged = report.unchanged;
  const nothingToApply =
    report.created + report.updated + report.missing === 0;

  return (
    <div className="mt-3">
      <p className="flex items-start gap-2 rounded-xl border border-ocean-200 bg-ocean-50 px-3 py-2 text-[11px] text-ocean-800">
        <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        <span>
          {report.applied
            ? "Importação aplicada. O mapa público já foi atualizado."
            : "Análise concluída. Nada foi gravado no mapa."}{" "}
          Arquivo <strong>{report.sourceFile}</strong> · UF {report.state} ·{" "}
          {report.mode === "SYNC" ? "Sincronizar" : "Criar e atualizar"}
        </span>
      </p>

      <dl className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
        <Summary label="Novos" value={report.created} tone="emerald" />
        <Summary label="Alterados" value={report.updated} tone="amber" />
        <Summary label="Sem mudança" value={unchanged} tone="slate" />
        <Summary label="Ausentes" value={report.missing} tone="red" />
        <Summary label="Rejeitados" value={report.rejected} tone="red" />
      </dl>

      {report.rejected > 0 ? (
        <p className="mt-3 flex items-start gap-2 rounded-xl border border-red-300 bg-red-50 px-3 py-2 text-[11px] font-semibold text-red-950">
          <AlertTriangle aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          Feições rejeitadas não entram no mapa. Veja o motivo na lista abaixo e
          corrija o arquivo no QGIS antes de aplicar.
        </p>
      ) : null}

      {report.applied && nothingToApply ? (
        <p className="mt-3 flex items-start gap-2 rounded-xl border border-emerald-300 bg-emerald-50 px-3 py-2 text-[11px] font-semibold text-emerald-950">
          <CheckCircle2 aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          O arquivo é o mesmo que já está no mapa.
        </p>
      ) : null}

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-xs font-black text-ocean-950">
          Feições do arquivo
        </h3>
        <label className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-ocean-700">
          <input
            type="checkbox"
            checked={showUnchanged}
            onChange={onToggleUnchanged}
            className="size-3.5 accent-ocean-950"
          />
          Mostrar as sem mudança ({unchanged})
        </label>
      </div>

      <div className="mt-2 max-h-80 overflow-auto rounded-xl border border-ocean-900/10">
        <table className="w-full min-w-[34rem] border-collapse text-left text-[11px]">
          <thead className="sticky top-0 bg-ocean-50 text-ocean-700">
            <tr>
              <th scope="col" className="px-2.5 py-2 font-bold">
                Situação
              </th>
              <th scope="col" className="px-2.5 py-2 font-bold">
                Área de banho
              </th>
              <th scope="col" className="px-2.5 py-2 font-bold">
                Setor
              </th>
              <th scope="col" className="px-2.5 py-2 font-bold">
                Município
              </th>
              <th scope="col" className="px-2.5 py-2 font-bold">
                Detalhe
              </th>
            </tr>
          </thead>
          <tbody>
            {changes.length === 0 ? (
              <tr>
                <td
                  colSpan={5}
                  className="px-2.5 py-6 text-center font-semibold text-ocean-600"
                >
                  Nenhuma feição para mostrar.
                </td>
              </tr>
            ) : (
              changes.map((change) => (
                <tr
                  key={`${change.action}-${change.externalId}`}
                  className="border-t border-ocean-900/5 align-top"
                >
                  <td className="px-2.5 py-2">
                    <span
                      className={`inline-flex rounded-full border px-2 py-0.5 text-[10px] font-bold ${ACTION_STYLE[change.action]}`}
                    >
                      {ACTION_LABEL[change.action]}
                    </span>
                  </td>
                  <td className="px-2.5 py-2 font-semibold text-ocean-950">
                    {change.area ?? "—"}
                  </td>
                  <td className="px-2.5 py-2 text-ocean-800">
                    {change.sector ?? "Setor sem nome"}
                  </td>
                  <td className="px-2.5 py-2 text-ocean-700">
                    {change.municipality ?? "—"}
                  </td>
                  <td className="px-2.5 py-2 text-ocean-700">
                    {change.message
                      ? change.message
                      : change.changedFields.length > 0
                        ? change.changedFields
                            .map((field) => FIELD_LABEL[field] ?? field)
                            .join(", ")
                        : "—"}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {!report.applied ? (
        <div className="mt-3 rounded-xl border border-ocean-900/10 bg-ocean-50/70 p-3">
          <p className="text-[11px] font-semibold text-ocean-800">
            {nothingToApply
              ? "O mapa já está igual ao arquivo — não há o que aplicar."
              : "Aplicar substitui o mapa atual pelas feições deste arquivo e recalcula os limites das áreas afetadas. Os identificadores são preservados."}
          </p>
          <label className="mt-2 flex items-start gap-2 text-[11px] font-semibold text-ocean-950">
            <input
              type="checkbox"
              checked={confirmed}
              disabled={!canApply}
              onChange={(event) => onConfirm(event.target.checked)}
              className="mt-0.5 size-3.5 accent-ocean-950"
            />
            Confirmo que este é o mapeamento mais recente enviado pelo CBM.
          </label>
          <div className="mt-2 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={!canApply || !confirmed || busy !== null}
              onClick={onApply}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-emerald-700 disabled:opacity-50"
            >
              {busy === "apply" ? (
                <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
              ) : (
                <CheckCircle2 aria-hidden="true" className="size-3.5" />
              )}
              {busy === "apply" ? "Aplicando..." : "Aplicar importação"}
            </button>
            <button
              type="button"
              disabled={busy !== null}
              onClick={onAnalyzeAgain}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-100 disabled:opacity-50"
            >
              <RefreshCw aria-hidden="true" className="size-3.5" />
              Analisar de novo
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

interface SummaryProps {
  readonly label: string;
  readonly value: number;
  readonly tone: "emerald" | "amber" | "slate" | "red";
}

function Summary({ label, value, tone }: SummaryProps) {
  const toneStyle = {
    emerald: "border-emerald-300 bg-emerald-50 text-emerald-950",
    amber: "border-amber-300 bg-amber-50 text-amber-950",
    slate: "border-ocean-200 bg-ocean-50 text-ocean-900",
    red: "border-red-300 bg-red-50 text-red-950",
  }[tone];
  return (
    <div className={`rounded-xl border px-2.5 py-2 ${toneStyle}`}>
      <dt className="text-[10px] font-bold uppercase tracking-wide opacity-80">
        {label}
      </dt>
      <dd className="text-lg font-black leading-tight">{value}</dd>
    </div>
  );
}

function importErrorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    return (
      cause.problem?.detail ??
      cause.problem?.title ??
      cause.message ??
      "Não foi possível importar o arquivo."
    );
  }
  return "Não foi possível importar o arquivo.";
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
