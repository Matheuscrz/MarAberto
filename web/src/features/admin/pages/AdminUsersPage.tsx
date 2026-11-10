import { useMemo, useState } from "react";
import { MailPlus, ShieldCheck, UserRoundCheck } from "lucide-react";
import { PageHeader } from "@/shared/components/Card";
import { ROLE_LABEL } from "@/features/auth";
import { formatDateTime } from "@/shared/lib/format";
import {
  adminUsers as initialUsers,
  type AdminUser,
  type AdminUserStatus,
} from "@/features/admin/api/demo-users";
import type { Role } from "@/features/auth/types";

const STATUS_LABEL: Record<AdminUserStatus, string> = {
  ACTIVE: "Ativo",
  INVITED: "Convite pendente",
  INACTIVE: "Desativado",
};

const STATUS_STYLE: Record<AdminUserStatus, string> = {
  ACTIVE: "border-emerald-300 bg-emerald-50 text-emerald-900",
  INVITED: "border-amber-300 bg-amber-50 text-amber-900",
  INACTIVE: "border-ocean-200 bg-ocean-50 text-ocean-600",
};

/**
 * Cadastro de usuários da instalação (TI). O convite real, a ativação e a
 * auditoria dependem do módulo `identity`; aqui a lista é local e serve para
 * validar o fluxo de papéis do ADR-009.
 */
export function AdminUsersPage() {
  const [users, setUsers] = useState<readonly AdminUser[]>(initialUsers);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<Role>("LIFEGUARD");
  const [feedback, setFeedback] = useState<string | null>(null);

  const counts = useMemo(() => {
    const active = users.filter((user) => user.status === "ACTIVE").length;
    const invited = users.filter((user) => user.status === "INVITED").length;
    return { active, invited };
  }, [users]);

  const handleInvite = (event: React.FormEvent) => {
    event.preventDefault();
    const trimmedName = name.trim();
    const trimmedEmail = email.trim().toLocaleLowerCase("pt-BR");
    if (!trimmedName || !trimmedEmail) return;

    if (users.some((user) => user.email === trimmedEmail)) {
      setFeedback("Já existe um usuário com este e-mail nesta instalação.");
      return;
    }

    const newUser: AdminUser = {
      id: `user-${Date.now()}`,
      name: trimmedName,
      email: trimmedEmail,
      role,
      status: "INVITED",
    };

    setUsers((previous) => [newUser, ...previous]);
    setName("");
    setEmail("");
    setRole("LIFEGUARD");
    setFeedback(`Convite preparado para ${trimmedEmail} (demo, não enviado).`);
  };

  const toggleUser = (userId: string) => {
    setUsers((previous) =>
      previous.map((user) => {
        if (user.id !== userId) return user;
        if (user.status === "INACTIVE") {
          return { ...user, status: "ACTIVE" };
        }
        return { ...user, status: "INACTIVE" };
      }),
    );
  };

  return (
    <div>
      <PageHeader
        title="Usuários"
        subtitle="Um login para as três áreas. O papel define o destino depois da autenticação."
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-full border border-ocean-200 bg-white px-3 py-1 text-xs font-bold text-ocean-800">
            <UserRoundCheck
              aria-hidden="true"
              className="size-3.5 text-emerald-600"
            />
            {counts.active} ativos
            <span className="text-ocean-400">·</span>
            {counts.invited} convites
          </span>
        }
      />

      <form
        onSubmit={handleInvite}
        className="mb-4 grid gap-3 rounded-2xl border border-ocean-900/10 bg-white p-4 shadow-sm"
      >
        <h2 className="flex items-center gap-1.5 text-sm font-bold text-ocean-950">
          <MailPlus aria-hidden="true" className="size-4 text-ocean-600" />
          Convidar usuário
        </h2>

        {feedback ? (
          <output className="block rounded-xl border border-ocean-200 bg-ocean-50 px-3 py-2 text-xs font-semibold text-ocean-800">
            {feedback}
          </output>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-3">
          <label className="block text-xs font-semibold text-ocean-800">
            Nome
            <input
              type="text"
              required
              maxLength={120}
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Ex.: Sd. Almeida"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            E-mail institucional
            <input
              type="email"
              required
              maxLength={160}
              autoComplete="off"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="nome@cbm.example"
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            />
          </label>

          <label className="block text-xs font-semibold text-ocean-800">
            Papel inicial
            <select
              value={role}
              onChange={(event) => setRole(event.target.value as Role)}
              className="mt-1 h-9 w-full rounded-lg border border-ocean-200 bg-white px-3 text-xs font-normal focus:border-ocean-600 focus:outline-none"
            >
              <option value="LIFEGUARD">{ROLE_LABEL.LIFEGUARD}</option>
              <option value="COORDINATOR">{ROLE_LABEL.COORDINATOR}</option>
              <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
            </select>
          </label>
        </div>

        <button
          type="submit"
          className="inline-flex h-9 w-fit items-center gap-1.5 rounded-lg bg-ocean-950 px-4 text-xs font-bold text-white transition hover:bg-ocean-900"
        >
          <MailPlus aria-hidden="true" className="size-4" />
          Preparar convite
        </button>
      </form>

      <ul className="grid gap-2">
        {users.map((user) => (
          <li
            key={user.id}
            className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-ocean-900/10 bg-white p-3 text-xs shadow-sm"
          >
            <div>
              <p className="flex flex-wrap items-center gap-1.5 text-sm font-bold text-ocean-950">
                {user.name}
                <span
                  className={`rounded-full border px-2 py-0.5 text-[10px] font-bold ${STATUS_STYLE[user.status]}`}
                >
                  {STATUS_LABEL[user.status]}
                </span>
              </p>
              <p className="text-[11px] text-ocean-600">{user.email}</p>
              <p className="text-[11px] text-ocean-500">
                {ROLE_LABEL[user.role]}
                {user.lastAccessAt
                  ? ` · último acesso ${formatDateTime(user.lastAccessAt)}`
                  : " · nunca acessou"}
              </p>
            </div>

            {user.role !== "ADMIN" ? (
              <button
                type="button"
                onClick={() => toggleUser(user.id)}
                className={[
                  "inline-flex min-h-8 items-center gap-1.5 rounded-lg px-2.5 py-1 text-[11px] font-bold transition",
                  user.status === "INACTIVE"
                    ? "bg-ocean-950 text-white hover:bg-ocean-900"
                    : "border border-coral-300 bg-coral-50 text-coral-900 hover:bg-coral-100",
                ].join(" ")}
              >
                <ShieldCheck aria-hidden="true" className="size-3.5" />
                {user.status === "INACTIVE" ? "Reativar" : "Desativar"}
              </button>
            ) : null}
          </li>
        ))}
      </ul>

      <p className="mt-3 text-[11px] leading-relaxed text-ocean-500">
        A TI não edita dados de ocorrência nem vê coordenadas de vítimas. Contas
        administrativas não podem ser desativadas por esta tela para não deixar
        a instalação sem acesso.
      </p>
    </div>
  );
}
