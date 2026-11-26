import { useState } from "react";
import {
  AlertTriangle,
  Check,
  Copy,
  KeyRound,
  Loader2,
  Search,
  ShieldCheck,
  UserPlus,
  Users,
} from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { ApiError } from "@/shared/lib/api-client";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import {
  createAdminUser,
  resetAdminUserPassword,
  updateAdminUser,
  type AdminUser,
  type ProvisionedAdminUser,
} from "@/features/admin/api/users";
import {
  useAdminUsers,
  type ActiveFilter,
  type AdminUserFilters,
} from "@/features/admin/hooks/use-admin-users";
import { MfaSettingsCard, ROLE_LABEL, useCurrentUser } from "@/features/auth";
import type { Role } from "@/features/auth/types";

/**
 * Cadastro de usuários da instalação (TI, RF20/ADR-027).
 *
 * <p>Não há convite por e-mail: o servidor gera a senha temporária e ela é
 * exibida **uma única vez** para a TI entregar presencialmente ou por canal
 * institucional. O operador troca a senha no primeiro acesso; até lá todas as
 * rotas de negócio respondem 403. Desativar nunca apaga o histórico.
 */
export function AdminUsersPage() {
  const currentUser = useCurrentUser();
  const {
    filters,
    items,
    total,
    hasMore,
    status,
    message,
    loadingMore,
    applyFilters,
    loadMore,
    reload,
  } = useAdminUsers();

  const [queryInput, setQueryInput] = useState(filters.q);
  const [provisioned, setProvisioned] = useState<ProvisionedAdminUser | null>(
    null,
  );
  const [feedback, setFeedback] = useState<string | null>(null);
  const [actionId, setActionId] = useState<string | null>(null);

  const applyFilterPatch = (patch: Partial<AdminUserFilters>) => {
    applyFilters({ ...filters, ...patch });
  };

  const handleFilterSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    applyFilterPatch({ q: queryInput });
  };

  const handleClearFilters = () => {
    setQueryInput("");
    applyFilters({ q: "", role: "", active: "all" });
  };

  const handleToggleActive = async (user: AdminUser) => {
    setFeedback(null);
    setActionId(user.id);
    try {
      await updateAdminUser(user.id, { active: !user.active });
      setFeedback(
        user.active
          ? `${user.name} foi desativado. As sessões abertas foram revogadas.`
          : `${user.name} foi reativado.`,
      );
      reload();
    } catch (error) {
      setFeedback(errorMessage(error));
    } finally {
      setActionId(null);
    }
  };

  const handleRoleChange = async (user: AdminUser, role: Role) => {
    if (role === user.role) return;
    setFeedback(null);
    setActionId(user.id);
    try {
      await updateAdminUser(user.id, { role });
      setFeedback(`Papel de ${user.name} alterado para ${ROLE_LABEL[role]}.`);
      reload();
    } catch (error) {
      setFeedback(errorMessage(error));
    } finally {
      setActionId(null);
    }
  };

  const handleReset = async (user: AdminUser) => {
    setFeedback(null);
    setActionId(user.id);
    try {
      const result = await resetAdminUserPassword(user.id);
      setProvisioned(result);
      reload();
    } catch (error) {
      setFeedback(errorMessage(error));
    } finally {
      setActionId(null);
    }
  };

  return (
    <div>
      <PageHeader
        title="Usuários"
        subtitle="Um login para as três áreas. A senha temporária é exibida uma única vez e deve ser trocada no primeiro acesso."
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800">
            <Users aria-hidden="true" className="size-3.5 text-emerald-600" />
            {total} {total === 1 ? "cadastrado" : "cadastrados"}
          </span>
        }
      />

      {feedback ? (
        <output className="mb-3 block rounded-xl border border-ocean-200 bg-ocean-50 px-3 py-2 text-xs font-semibold text-ocean-800">
          {sanitizeText(feedback, 240)}
        </output>
      ) : null}

      {provisioned ? (
        <TemporaryPasswordPanel
          provisioned={provisioned}
          onDismiss={() => setProvisioned(null)}
        />
      ) : null}

      <CreateUserForm
        onCreated={(result) => {
          setProvisioned(result);
          setFeedback("Operador cadastrado. Entregue a senha temporária agora.");
          reload();
        }}
        onError={(error) => setFeedback(errorMessage(error))}
      />

      <form
        onSubmit={handleFilterSubmit}
        className="mb-3 grid gap-2 rounded-2xl border border-ocean-900/10 bg-white p-3 shadow-sm sm:grid-cols-[1fr_auto_auto_auto]"
      >
        <label className="block text-xs font-semibold text-ocean-800">
          Buscar
          <input
            type="search"
            value={queryInput}
            onChange={(event) => setQueryInput(event.target.value)}
            maxLength={120}
            placeholder="E-mail, CPF exato ou prefixo da matrícula"
            className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
          />
        </label>

        <label className="block text-xs font-semibold text-ocean-800">
          Papel
          <select
            value={filters.role}
            onChange={(event) =>
              applyFilterPatch({ role: event.target.value as Role | "" })
            }
            className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-2 text-xs font-normal focus:border-ocean-600 focus:outline-none"
          >
            <option value="">Todos</option>
            <option value="LIFEGUARD">{ROLE_LABEL.LIFEGUARD}</option>
            <option value="COORDINATOR">{ROLE_LABEL.COORDINATOR}</option>
            <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
          </select>
        </label>

        <label className="block text-xs font-semibold text-ocean-800">
          Estado
          <select
            value={filters.active}
            onChange={(event) =>
              applyFilterPatch({ active: event.target.value as ActiveFilter })
            }
            className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-2 text-xs font-normal focus:border-ocean-600 focus:outline-none"
          >
            <option value="all">Todos</option>
            <option value="active">Ativos</option>
            <option value="inactive">Desativados</option>
          </select>
        </label>

        <div className="flex items-end gap-2">
          <button
            type="submit"
            className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-ocean-950 px-3 text-xs font-bold text-white transition hover:bg-ocean-900"
          >
            <Search aria-hidden="true" className="size-3.5" />
            Filtrar
          </button>
          <button
            type="button"
            onClick={handleClearFilters}
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
          Carregando usuários
        </output>
      ) : null}

      {status === "ready" && items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-ocean-300 bg-ocean-50/50 px-4 py-8 text-center">
          <p className="text-sm font-bold text-ocean-950">
            Nenhum usuário encontrado
          </p>
          <p className="mt-1 text-xs text-ocean-700">
            Ajuste os filtros ou cadastre o primeiro operador acima.
          </p>
        </div>
      ) : null}

      <ul className="grid gap-2">
        {items.map((user) => (
          <AdminUserRow
            key={user.id}
            user={user}
            isSelf={currentUser?.id === user.id}
            busy={actionId === user.id}
            onToggle={() => void handleToggleActive(user)}
            onRoleChange={(role) => void handleRoleChange(user, role)}
            onReset={() => void handleReset(user)}
          />
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
        Não existe exclusão de usuário: desativar preserva plantões, ocorrências
        e a trilha. A senha temporária expira em 24 h; se vencer antes do
        primeiro acesso, use “Redefinir senha”. Toda ação desta tela entra na
        auditoria.
      </p>

      <MfaSettingsCard />
    </div>
  );
}

function CreateUserForm({
  onCreated,
  onError,
}: {
  readonly onCreated: (result: ProvisionedAdminUser) => void;
  readonly onError: (error: unknown) => void;
}) {
  const [name, setName] = useState("");
  const [badgeNumber, setBadgeNumber] = useState("");
  const [email, setEmail] = useState("");
  const [cpf, setCpf] = useState("");
  const [role, setRole] = useState<Role>("LIFEGUARD");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      const result = await createAdminUser({
        name: name.trim(),
        badgeNumber: badgeNumber.trim() || undefined,
        email: email.trim(),
        cpf: cpf.trim(),
        role,
      });
      onCreated(result);
      setName("");
      setBadgeNumber("");
      setEmail("");
      setCpf("");
      setRole("LIFEGUARD");
    } catch (error) {
      onError(error);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-4 grid gap-3 rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm"
    >
      <h2 className="flex items-center gap-1.5 text-sm font-bold text-ocean-950">
        <UserPlus aria-hidden="true" className="size-4 text-ocean-600" />
        Cadastrar operador
      </h2>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Field label="Nome" required>
          <input
            type="text"
            required
            maxLength={120}
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Ex.: Sd. Almeida"
            className={INPUT_CLASS}
          />
        </Field>

        <Field label="Matrícula (opcional)">
          <input
            type="text"
            maxLength={32}
            value={badgeNumber}
            onChange={(event) => setBadgeNumber(event.target.value)}
            placeholder="BM-10492"
            className={INPUT_CLASS}
          />
        </Field>

        <Field label="E-mail institucional" required>
          <input
            type="email"
            required
            maxLength={120}
            autoComplete="off"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="nome@cbm.example"
            className={INPUT_CLASS}
          />
        </Field>

        <Field label="CPF" required>
          <input
            type="text"
            required
            inputMode="numeric"
            maxLength={14}
            autoComplete="off"
            value={cpf}
            onChange={(event) => setCpf(event.target.value)}
            placeholder="000.000.000-00"
            className={INPUT_CLASS}
          />
        </Field>

        <Field label="Papel inicial" required>
          <select
            value={role}
            onChange={(event) => setRole(event.target.value as Role)}
            className={INPUT_CLASS}
          >
            <option value="LIFEGUARD">{ROLE_LABEL.LIFEGUARD}</option>
            <option value="COORDINATOR">{ROLE_LABEL.COORDINATOR}</option>
            <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
          </select>
        </Field>
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
      >
        {submitting ? (
          <Loader2 aria-hidden="true" className="size-4 animate-spin" />
        ) : (
          <UserPlus aria-hidden="true" className="size-4" />
        )}
        {submitting ? "Cadastrando..." : "Cadastrar e gerar senha"}
      </button>

      <p className="text-[11px] leading-relaxed text-ocean-500">
        A conta nasce ativa com uma senha temporária de 24 h. O CPF fica
        cifrado e não é exibido de volta.
      </p>
    </form>
  );
}

function AdminUserRow({
  user,
  isSelf,
  busy,
  onToggle,
  onRoleChange,
  onReset,
}: {
  readonly user: AdminUser;
  readonly isSelf: boolean;
  readonly busy: boolean;
  readonly onToggle: () => void;
  readonly onRoleChange: (role: Role) => void;
  readonly onReset: () => void;
}) {
  return (
    <li className="rounded-2xl border border-ocean-900/10 bg-white p-3.5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="flex flex-wrap items-center gap-1.5 text-sm font-bold text-ocean-950">
            {sanitizeText(user.name, 120)}
            {isSelf ? (
              <span className="rounded-full border border-ocean-300 bg-ocean-50 px-2 py-0.5 text-[10px] font-bold text-ocean-700">
                você
              </span>
            ) : null}
            <StatusBadge user={user} />
          </p>
          <p className="truncate text-[11px] text-ocean-600">
            {user.email ? sanitizeText(user.email, 160) : "sem e-mail"}
            {user.badgeNumber ? ` · ${sanitizeText(user.badgeNumber, 32)}` : ""}
          </p>
          <p className="text-[11px] text-ocean-500">
            {ROLE_LABEL[user.role]}
            {user.lastAccessAt
              ? ` · último acesso ${formatDateTime(user.lastAccessAt)}`
              : " · nunca acessou"}
            {` · criado em ${formatDateTime(user.createdAt)}`}
          </p>
          {user.mustChangePassword && user.temporaryPasswordExpiresAt ? (
            <p className="mt-0.5 text-[11px] font-semibold text-amber-800">
              Senha temporária válida até{" "}
              {formatDateTime(user.temporaryPasswordExpiresAt)}
            </p>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <label className="text-[10px] font-bold uppercase tracking-wide text-ocean-500">
            Papel
            <select
              value={user.role}
              disabled={busy || isSelf}
              onChange={(event) => onRoleChange(event.target.value as Role)}
              className="mt-0.5 block h-8 rounded-lg border border-ocean-200 bg-white px-2 text-[11px] font-semibold text-ocean-900 disabled:bg-ocean-50 disabled:text-ocean-500"
            >
              <option value="LIFEGUARD">{ROLE_LABEL.LIFEGUARD}</option>
              <option value="COORDINATOR">{ROLE_LABEL.COORDINATOR}</option>
              <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
            </select>
          </label>

          <button
            type="button"
            onClick={onReset}
            disabled={busy}
            className="inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-ocean-200 bg-white px-2.5 py-1 text-[11px] font-bold text-ocean-800 transition hover:bg-ocean-50 disabled:opacity-60"
          >
            <KeyRound aria-hidden="true" className="size-3.5" />
            Redefinir senha
          </button>

          <button
            type="button"
            onClick={onToggle}
            disabled={busy || isSelf}
            className={[
              "inline-flex min-h-8 items-center gap-1.5 rounded-lg px-2.5 py-1 text-[11px] font-bold transition disabled:opacity-50",
              user.active
                ? "border border-coral-300 bg-coral-50 text-coral-900 hover:bg-coral-100"
                : "bg-ocean-950 text-white hover:bg-ocean-900",
            ].join(" ")}
          >
            <ShieldCheck aria-hidden="true" className="size-3.5" />
            {user.active ? "Desativar" : "Reativar"}
          </button>
        </div>
      </div>
    </li>
  );
}

function StatusBadge({ user }: { readonly user: AdminUser }) {
  if (!user.active) {
    return (
      <span className="rounded-full border border-ocean-200 bg-ocean-50 px-2 py-0.5 text-[10px] font-bold text-ocean-600">
        Desativado
      </span>
    );
  }
  if (user.mustChangePassword) {
    return (
      <span className="rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-900">
        Troca de senha pendente
      </span>
    );
  }
  return (
    <span className="rounded-full border border-emerald-300 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-900">
      Ativo
    </span>
  );
}

function TemporaryPasswordPanel({
  provisioned,
  onDismiss,
}: {
  readonly provisioned: ProvisionedAdminUser;
  readonly onDismiss: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(provisioned.temporaryPassword);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2500);
    } catch {
      setCopied(false);
    }
  };

  return (
    <output className="mb-4 block rounded-2xl border border-amber-300 bg-amber-50 p-4 shadow-sm">
      <h2 className="flex items-center gap-1.5 text-sm font-bold text-amber-950">
        <AlertTriangle aria-hidden="true" className="size-4" />
        Senha temporária de {sanitizeText(provisioned.user.name, 120)}
      </h2>
      <p className="mt-1 text-[11px] leading-relaxed text-amber-900">
        Esta senha aparece <strong>uma única vez</strong>. Entregue ao operador
        por canal institucional; ela expira em{" "}
        {formatDateTime(provisioned.expiresAt)} e deve ser trocada no primeiro
        acesso.
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <code className="rounded-lg border border-amber-300 bg-white px-3 py-1.5 font-mono text-sm font-bold tracking-wide text-amber-950">
          {provisioned.temporaryPassword}
        </code>
        <button
          type="button"
          onClick={() => void copy()}
          className="inline-flex min-h-8 items-center gap-1.5 rounded-lg bg-amber-950 px-2.5 py-1 text-[11px] font-bold text-white transition hover:bg-amber-900"
        >
          {copied ? (
            <Check aria-hidden="true" className="size-3.5" />
          ) : (
            <Copy aria-hidden="true" className="size-3.5" />
          )}
          {copied ? "Copiada" : "Copiar"}
        </button>
        <button
          type="button"
          onClick={onDismiss}
          className="min-h-8 rounded-lg border border-amber-300 bg-white px-2.5 py-1 text-[11px] font-bold text-amber-950 hover:bg-amber-100"
        >
          Já entreguei
        </button>
      </div>
    </output>
  );
}

const INPUT_CLASS =
  "mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal text-ocean-950 focus:border-ocean-600 focus:outline-none";

function Field({
  label,
  required,
  children,
}: {
  readonly label: string;
  readonly required?: boolean;
  readonly children: React.ReactNode;
}) {
  return (
    <label className="block text-xs font-semibold text-ocean-800">
      {label}
      {required ? <span className="text-coral-600"> *</span> : null}
      {children}
    </label>
  );
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.network) {
      return "Sem conexão com o servidor. Tente novamente em instantes.";
    }
    return (
      error.problem?.detail ??
      error.problem?.title ??
      `Falha (${error.status}) ao executar a ação.`
    );
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Não foi possível concluir a ação.";
}
