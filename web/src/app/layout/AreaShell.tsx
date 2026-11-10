import type { LucideIcon } from "lucide-react";
import { ArrowLeft, LogOut, Waves } from "lucide-react";
import { Link, NavLink, Outlet } from "react-router-dom";
import {
  DEMO_AUTH_ENABLED,
  ROLE_AREA_LABEL,
  ROLE_LABEL,
  useAuthSession,
  useLogout,
} from "@/features/auth";

export interface AreaNavItem {
  readonly to: string;
  readonly label: string;
  readonly icon: LucideIcon;
  /** `true` na rota índice, para não ficar ativa em todas as filhas. */
  readonly end?: boolean;
}

interface AreaShellProps {
  readonly title: string;
  readonly subtitle: string;
  readonly nav: readonly AreaNavItem[];
}

/**
 * Casca comum das áreas operacionais (`/operador`, `/gestao`, `/admin`):
 * cabeçalho com identificação do usuário, navegação por seção e saída. As
 * páginas em si ficam nas features de cada área; a composição mora no `app/`.
 */
export function AreaShell({ title, subtitle, nav }: AreaShellProps) {
  const user = useAuthSession((state) => state.user);
  const { logout, pending } = useLogout();

  return (
    <div className="min-h-dvh bg-ocean-50 text-ocean-950">
      <header className="border-b border-ocean-900/10 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-2.5">
            <span className="grid size-9 place-items-center rounded-xl bg-ocean-950 text-white">
              <Waves aria-hidden="true" className="size-5" />
            </span>
            <div>
              <p className="text-[11px] font-bold uppercase tracking-wider text-ocean-500">
                {user ? ROLE_AREA_LABEL[user.role] : "MarAberto"}
              </p>
              <p className="text-sm font-black leading-tight text-ocean-950">
                {title}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Link
              to="/"
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
            >
              <ArrowLeft aria-hidden="true" className="size-3.5" />
              Mapa público
            </Link>

            {user ? (
              <div className="flex items-center gap-2 rounded-xl border border-ocean-900/10 bg-ocean-50/60 px-2.5 py-1.5">
                <span
                  aria-hidden="true"
                  className="grid size-7 place-items-center rounded-full bg-ocean-950 text-[11px] font-black text-white"
                >
                  {initialsOf(user.name)}
                </span>
                <div className="leading-tight">
                  <p className="text-xs font-bold text-ocean-950">{user.name}</p>
                  <p className="text-[10px] text-ocean-600">
                    {ROLE_LABEL[user.role]}
                    {user.badgeNumber ? ` · ${user.badgeNumber}` : ""}
                  </p>
                </div>
              </div>
            ) : null}

            <button
              type="button"
              onClick={() => {
                logout();
              }}
              disabled={pending}
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
            >
              <LogOut aria-hidden="true" className="size-3.5" />
              {pending ? "Saindo..." : "Sair"}
            </button>
          </div>
        </div>

        {DEMO_AUTH_ENABLED ? (
          <p className="border-t border-amber-300/60 bg-amber-100 px-4 py-1.5 text-center text-[11px] font-semibold text-amber-950">
            Sessão de demonstração: os dados desta área são fictícios e não
            substituem o sistema oficial.
          </p>
        ) : null}
      </header>

      <div className="mx-auto max-w-6xl px-4 py-4 md:py-6">
        <nav
          aria-label={`Seções de ${title}`}
          className="mb-4 flex gap-1.5 overflow-x-auto pb-1"
        >
          {nav.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  [
                    "inline-flex min-h-9 shrink-0 items-center gap-1.5 rounded-xl border px-3 py-1.5 text-xs font-bold transition",
                    isActive
                      ? "border-ocean-950 bg-ocean-950 text-white shadow-sm"
                      : "border-ocean-900/10 bg-white text-ocean-800 hover:bg-ocean-100",
                  ].join(" ")
                }
              >
                <Icon aria-hidden="true" className="size-4" />
                {item.label}
              </NavLink>
            );
          })}
        </nav>

        <p className="mb-4 text-xs text-ocean-600">{subtitle}</p>

        <main>
          <Outlet />
        </main>
      </div>
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
