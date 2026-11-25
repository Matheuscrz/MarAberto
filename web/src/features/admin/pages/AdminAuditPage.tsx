import { useState } from "react";
import {
  CheckCircle2,
  Loader2,
  RefreshCw,
  ScrollText,
  Search,
  ShieldAlert,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { ApiError } from "@/shared/lib/api-client";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import {
  fetchAuditIntegrity,
  type AuditEntry,
  type AuditIntegrity,
} from "@/features/admin/api/audit";
import {
  useAdminAudit,
  type AuditFilters,
} from "@/features/admin/hooks/use-admin-audit";

const ACTION_LABEL: Readonly<Record<string, string>> = {
  USER_CREATED: "Usuário cadastrado",
  USER_ACTIVATED: "Usuário reativado",
  USER_DEACTIVATED: "Usuário desativado",
  USER_ROLE_CHANGED: "Papel alterado",
  USER_PASSWORD_RESET: "Senha redefinida pela TI",
  PASSWORD_CHANGED: "Senha trocada pelo operador",
  STATION_ACTIVATED: "Estrutura ativada",
  STATION_DEACTIVATED: "Estrutura desativada",
  SHIFT_CLOSED_IDLE: "Plantão encerrado por inatividade",
  IMPORT_APPLIED: "Importação de mapa aplicada",
  UNMAPPED_SIGNAL_DECIDED: "Sinalização em triagem decidida",
  OCCURRENCE_CLOSED: "Ocorrência encerrada",
  OCCURRENCE_EDITED: "Ocorrência editada",
  PERSON_REVEALED: "Dados da vítima revelados",
};

const ENTITY_LABEL: Readonly<Record<string, string>> = {
  USER: "Usuário",
  OCCURRENCE: "Ocorrência",
  STATION: "Estrutura",
  UNMAPPED_SIGNAL: "Sinalização",
  BATHING_AREA_IMPORT: "Importação",
  SHIFT: "Plantão",
};

/**
 * Trilha de auditoria da instalação (RNF06/ADR-027).
 *
 * <p>Somente `ADMIN`. A tabela é append-only e encadeada por hash; a conferência
 * de integridade recalcula a cadeia no servidor e aponta a primeira linha
 * divergente. A consulta é paginada por cursor e filtrada no backend.
 */
export function AdminAuditPage() {
  const {
    filters,
    items,
    hasMore,
    status,
    message,
    loadingMore,
    applyFilters,
    loadMore,
    reload,
  } = useAdminAudit();

  const [form, setForm] = useState<AuditFilters>(filters);
  const [integrity, setIntegrity] = useState<AuditIntegrity | null>(null);
  const [checking, setChecking] = useState(false);
  const [integrityError, setIntegrityError] = useState<string | null>(null);

  const update = (patch: Partial<AuditFilters>) =>
    setForm((previous) => ({ ...previous, ...patch }));

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    applyFilters(form);
  };

  const handleClear = () => {
    const empty: AuditFilters = {
      traceId: "",
      actorId: "",
      action: "",
      entityType: "",
      from: "",
      to: "",
    };
    setForm(empty);
    applyFilters(empty);
  };

  const checkIntegrity = async () => {
    setChecking(true);
    setIntegrityError(null);
    try {
      setIntegrity(await fetchAuditIntegrity());
    } catch (error) {
      setIntegrityError(errorMessage(error));
    } finally {
      setChecking(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Auditoria"
        subtitle="Registro imutável de quem fez o quê. Consulte por ator, ação, entidade, requisição e período."
        actions={
          <button
            type="button"
            onClick={() => void checkIntegrity()}
            disabled={checking}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-200 bg-white px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:opacity-60"
          >
            {checking ? (
              <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
            ) : (
              <RefreshCw aria-hidden="true" className="size-3.5" />
            )}
            Conferir integridade
          </button>
        }
      />

      {integrity ? (
        <div
          className={[
            "mb-3 flex items-start gap-2 rounded-xl border px-3 py-2 text-xs font-semibold",
            integrity.valid
              ? "border-emerald-300 bg-emerald-50 text-emerald-950"
              : "border-coral-300 bg-coral-50 text-coral-950",
          ].join(" ")}
        >
          {integrity.valid ? (
            <CheckCircle2 aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          ) : (
            <ShieldAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          )}
          <span>
            {integrity.valid
              ? `Cadeia íntegra: ${integrity.checked} registro(s) conferido(s).`
              : `Divergência na linha ${integrity.firstInvalidId ?? "desconhecida"}. Preserve o banco e acione a TI.`}
          </span>
        </div>
      ) : null}

      {integrityError ? (
        <p
          role="alert"
          className="mb-3 rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-950"
        >
          {sanitizeText(integrityError, 200)}
        </p>
      ) : null}

      <form
        onSubmit={handleSubmit}
        className="mb-4 grid gap-2 rounded-2xl border border-ocean-900/10 bg-white p-3 shadow-sm sm:grid-cols-2 lg:grid-cols-3"
      >
        <FilterField label="Ação">
          <input
            type="text"
            value={form.action}
            onChange={(event) => update({ action: event.target.value })}
            placeholder="Ex.: USER_DEACTIVATED"
            className={INPUT_CLASS}
          />
        </FilterField>

        <FilterField label="Ator (UUID)">
          <input
            type="text"
            value={form.actorId}
            onChange={(event) => update({ actorId: event.target.value })}
            placeholder="00000000-0000-0000-0000-000000000000"
            className={INPUT_CLASS}
          />
        </FilterField>

        <FilterField label="Requisição (traceId)">
          <input
            type="text"
            value={form.traceId}
            onChange={(event) => update({ traceId: event.target.value })}
            placeholder="X-Request-Id da resposta"
            className={INPUT_CLASS}
          />
        </FilterField>

        <FilterField label="Tipo da entidade">
          <select
            value={form.entityType}
            onChange={(event) => update({ entityType: event.target.value })}
            className={INPUT_CLASS}
          >
            <option value="">Todas</option>
            {Object.entries(ENTITY_LABEL).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </FilterField>

        <FilterField label="De">
          <input
            type="date"
            value={form.from}
            onChange={(event) => update({ from: event.target.value })}
            className={INPUT_CLASS}
          />
        </FilterField>

        <FilterField label="Até">
          <input
            type="date"
            value={form.to}
            onChange={(event) => update({ to: event.target.value })}
            className={INPUT_CLASS}
          />
        </FilterField>

        <div className="flex items-end gap-2 sm:col-span-2 lg:col-span-3">
          <button
            type="submit"
            className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900"
          >
            <Search aria-hidden="true" className="size-3.5" />
            Filtrar
          </button>
          <button
            type="button"
            onClick={handleClear}
            className="h-9 rounded-lg border border-ocean-200 px-3 text-xs font-bold text-ocean-700 hover:bg-ocean-50"
          >
            Limpar
          </button>
        </div>
      </form>

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

      {status === "loading" && items.length === 0 ? (
        <output className="block py-6 text-center text-sm font-semibold text-ocean-600">
          Consultando a trilha
        </output>
      ) : null}

      {status === "ready" && items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-ocean-300 bg-ocean-50/50 px-4 py-8 text-center">
          <ScrollText aria-hidden="true" className="mx-auto size-6 text-ocean-400" />
          <p className="mt-2 text-sm font-bold text-ocean-950">
            Nenhum registro para estes filtros
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            A trilha começa a registrar a partir das ações desta versão.
          </p>
        </div>
      ) : null}

      <ul className="grid gap-2">
        {items.map((entry) => (
          <AuditEntryCard key={entry.id} entry={entry} />
        ))}
      </ul>

      {hasMore ? (
        <div className="mt-3 text-center">
          <button
            type="button"
            onClick={() => void loadMore()}
            disabled={loadingMore}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-200 bg-white px-4 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:opacity-60"
          >
            {loadingMore ? (
              <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
            ) : null}
            Carregar mais
          </button>
        </div>
      ) : null}

      <p className="mt-3 text-[11px] leading-relaxed text-ocean-500">
        A trilha é append-only: a aplicação só insere e o banco recusa
        `UPDATE`/`DELETE`. O detalhe não guarda PII nem coordenadas de operador;
        o IP aparece apenas como índice cego.
      </p>
    </div>
  );
}

function AuditEntryCard({ entry }: { readonly entry: AuditEntry }) {
  const actionLabel = ACTION_LABEL[entry.action] ?? entry.action;
  const entityLabel = ENTITY_LABEL[entry.entityType] ?? entry.entityType;
  const details = Object.entries(entry.detail ?? {});

  return (
    <li className="rounded-2xl border border-ocean-900/10 bg-white p-3.5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-bold text-ocean-950">
            {sanitizeText(actionLabel, 120)}
          </p>
          <p className="text-[11px] text-ocean-600">
            {entityLabel}
            {entry.entityId ? ` · ${sanitizeText(entry.entityId, 64)}` : ""}
            {entry.actorRole ? ` · por ${sanitizeText(entry.actorRole, 32)}` : ""}
          </p>
        </div>
        <time
          dateTime={entry.occurredAt}
          className="text-[11px] font-semibold text-ocean-500"
        >
          {formatDateTime(entry.occurredAt)}
        </time>
      </div>

      <dl className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-ocean-600">
        <div>
          <dt className="inline font-semibold text-ocean-500">Ator: </dt>
          <dd className="inline font-mono">
            {entry.actorId ? sanitizeText(entry.actorId, 64) : "sistema"}
          </dd>
        </div>
        {entry.traceId ? (
          <div>
            <dt className="inline font-semibold text-ocean-500">Requisição: </dt>
            <dd className="inline font-mono">
              {sanitizeText(entry.traceId, 64)}
            </dd>
          </div>
        ) : null}
        <div>
          <dt className="inline font-semibold text-ocean-500">Registro: </dt>
          <dd className="inline font-mono">#{entry.id}</dd>
        </div>
      </dl>

      {details.length > 0 ? (
        <ul className="mt-2 flex flex-wrap gap-1.5">
          {details.map(([key, value]) => (
            <li
              key={key}
              className="rounded-lg border border-ocean-100 bg-ocean-50 px-2 py-0.5 text-[10px] text-ocean-700"
            >
              <span className="font-bold">{sanitizeText(key, 40)}</span>
              {": "}
              {sanitizeText(renderDetail(value), 120)}
            </li>
          ))}
        </ul>
      ) : null}
    </li>
  );
}

function renderDetail(value: unknown): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

const INPUT_CLASS =
  "mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal text-ocean-950 focus:border-ocean-600 focus:outline-none";

function FilterField({
  label,
  children,
}: {
  readonly label: string;
  readonly children: React.ReactNode;
}) {
  return (
    <label className="block text-xs font-semibold text-ocean-800">
      {label}
      {children}
    </label>
  );
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao conferir a integridade.`)
    );
  }
  return "Não foi possível conferir a integridade agora.";
}
