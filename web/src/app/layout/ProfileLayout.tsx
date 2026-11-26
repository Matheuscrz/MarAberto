import type { ReactNode } from "react";
import { ArrowLeft, LogOut } from "lucide-react";
import { Link } from "react-router-dom";
import {
  DEMO_AUTH_ENABLED,
  ROLE_AREA_LABEL,
  ROLE_LABEL,
  homePathForRole,
  useAuthSession,
  useLogout,
} from "@/features/auth";
import { closeShiftIfOpen } from "@/features/operator";

interface ProfileLayoutProps {
  readonly children: ReactNode;
}

/**
 * Casca da página de perfil (`/perfil`), compartilhada entre Guarda-vidas,
 * Coordenação e TI. Mantém o cabeçalho consistente com `AreaShell` e encerra o
 * plantão ativo antes do logout quando aplicável.
 */
export function ProfileLayout({ children }: ProfileLayoutProps) {
  const user = useAuthSession((state) => state.user);
  const { logout, pending } = useLogout();

  const handleLogout = async () => {
    if (user && user.role !== "ADMIN") {
      await closeShiftIfOpen();
    }
    await logout();
  };

  return (
    <div className="min-h-dvh bg-ocean-50 text-ocean-950">
      <header className="border-b border-ocean-900/10 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-2.5">
            <img
              src="/brand/mark-96.png"
              alt=""
              aria-hidden="true"
              className="size-9 rounded-xl"
            />
            <div>
              <p className="text-[11px] font-bold uppercase tracking-wider text-ocean-500">
                {user ? ROLE_AREA_LABEL[user.role] : "MarAberto"}
              </p>
              <p className="text-sm font-black leading-tight text-ocean-950">
                Meu perfil
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {user ? (
              <Link
                to={homePathForRole(user.role)}
                className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
              >
                <ArrowLeft aria-hidden="true" className="size-3.5" />
                Voltar para minha área
              </Link>
            ) : null}

            <Link
              to="/"
              className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-900/15 px-3 py-1.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
            >
              Mapa público
            </Link>

            {user ? (
              <span
                aria-current="page"
                className="flex items-center gap-2 rounded-xl border border-ocean-950 bg-ocean-50 px-2.5 py-1.5"
              >
                <span
                  aria-hidden="true"
                  className="grid size-7 place-items-center rounded-full bg-ocean-950 text-[11px] font-black text-white"
                >
                  {initialsOf(user.name)}
                </span>
                <span className="leading-tight">
                  <span className="block text-xs font-bold text-ocean-950">
                    {user.name}
                  </span>
                  <span className="block text-[10px] text-ocean-600">
                    {ROLE_LABEL[user.role]}
                    {user.badgeNumber ? ` · ${user.badgeNumber}` : ""}
                  </span>
                </span>
              </span>
            ) : null}

            <button
              type="button"
              onClick={() => {
                void handleLogout();
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

      <main className="mx-auto max-w-6xl px-4 py-6">{children}</main>
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
