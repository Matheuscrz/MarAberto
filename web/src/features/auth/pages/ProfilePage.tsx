import { BadgeCheck, KeyRound, Mail, Shield, User } from "lucide-react";
import { ChangePasswordForm } from "@/features/auth/components/ChangePasswordForm";
import { ConnectedDevicesCard } from "@/features/auth/components/ConnectedDevicesCard";
import { MfaSettingsCard } from "@/features/auth/components/MfaSettingsCard";
import { ROLE_AREA_LABEL, ROLE_LABEL } from "@/features/auth/lib/role-home";
import { useCurrentUser } from "@/features/auth/session";

/**
 * Página de perfil do operador autenticado (`/perfil`): mostra os dados da
 * conta, permite ativar/desativar MFA TOTP, gerenciar dispositivos conectados e
 * alterar a senha.
 */
export function ProfilePage() {
  const user = useCurrentUser();

  if (!user) {
    return null;
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <section className="rounded-2xl border border-ocean-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ocean-100 pb-4">
          <div className="flex items-center gap-3">
            <span
              aria-hidden="true"
              className="grid size-12 place-items-center rounded-2xl bg-ocean-950 text-sm font-black text-white"
            >
              {initialsOf(user.name)}
            </span>
            <div>
              <h1 className="text-lg font-black text-ocean-950">{user.name}</h1>
              <p className="text-xs text-ocean-600">
                {ROLE_LABEL[user.role]} · {ROLE_AREA_LABEL[user.role]}
              </p>
            </div>
          </div>

          <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-bold ${
              user.mfaEnabled
                ? "border-emerald-200 bg-emerald-50 text-emerald-900"
                : "border-ocean-200 bg-ocean-50 text-ocean-700"
            }`}
          >
            <Shield aria-hidden="true" className="size-3.5" />
            {user.mfaEnabled ? "MFA ativo" : "MFA desativado"}
          </span>
        </div>

        <dl className="mt-4 grid gap-3 sm:grid-cols-2">
          <div className="rounded-xl border border-ocean-100 bg-ocean-50/50 p-3">
            <dt className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-ocean-500">
              <User aria-hidden="true" className="size-3.5" />
              Perfil de acesso
            </dt>
            <dd className="mt-1 text-sm font-bold text-ocean-950">
              {ROLE_LABEL[user.role]}
            </dd>
          </div>

          {user.badgeNumber ? (
            <div className="rounded-xl border border-ocean-100 bg-ocean-50/50 p-3">
              <dt className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-ocean-500">
                <BadgeCheck aria-hidden="true" className="size-3.5" />
                Matrícula / Usuário
              </dt>
              <dd className="mt-1 font-mono text-sm font-bold text-ocean-950">
                {user.badgeNumber}
              </dd>
            </div>
          ) : null}

          {user.email ? (
            <div className="rounded-xl border border-ocean-100 bg-ocean-50/50 p-3 sm:col-span-2">
              <dt className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wider text-ocean-500">
                <Mail aria-hidden="true" className="size-3.5" />
                E-mail cadastrado
              </dt>
              <dd className="mt-1 text-sm font-bold text-ocean-950">
                {user.email}
              </dd>
            </div>
          ) : null}
        </dl>
      </section>

      <MfaSettingsCard className="" />

      <ConnectedDevicesCard />

      <section className="rounded-2xl border border-ocean-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2.5 border-b border-ocean-100 pb-3">
          <span className="grid size-9 place-items-center rounded-xl bg-ocean-950 text-white">
            <KeyRound aria-hidden="true" className="size-4" />
          </span>
          <div>
            <h2 className="text-sm font-black text-ocean-950">Alterar senha</h2>
            <p className="text-xs text-ocean-600">
              A troca encerra todas as sessões abertas e exige um novo login com
              a senha escolhida.
            </p>
          </div>
        </div>

        <div className="mt-4 max-w-md">
          <ChangePasswordForm pending={false} idPrefix="profile-password" />
        </div>
      </section>
    </div>
  );
}

function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}
