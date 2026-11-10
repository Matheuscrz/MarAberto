import { useState } from "react";
import {
  Link,
  Navigate,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import {
  ArrowLeft,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  ShieldCheck,
  User,
  Waves,
} from "lucide-react";
import { ApiError } from "@/shared/lib/api-client";
import { loginRequest } from "@/features/auth/api";
import {
  DEMO_ACCOUNTS,
  DEMO_AUTH_ENABLED,
  demoLogin,
} from "@/features/auth/demo";
import {
  ROLE_LABEL,
  homePathForRole,
  isInternalPath,
} from "@/features/auth/lib/role-home";
import { useAuthSession } from "@/features/auth/session";
import type { AuthSession } from "@/features/auth/types";

/**
 * Login único (`/login`) para as três áreas. O papel decide o destino:
 * guarda-vidas → `/operador`, coordenação → `/gestao`, TI → `/admin`.
 * `next` só aceita caminho interno (evita redirecionamento aberto).
 */
export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const status = useAuthSession((state) => state.status);
  const user = useAuthSession((state) => state.user);
  const setSession = useAuthSession((state) => state.setSession);

  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const next = searchParams.get("next");

  // Já autenticado: não faz sentido mostrar o formulário de novo.
  if (status === "authenticated" && user) {
    return (
      <Navigate
        to={isInternalPath(next) ? next : homePathForRole(user.role)}
        replace
      />
    );
  }

  const finishLogin = (session: AuthSession) => {
    setSession(session);
    const target = isInternalPath(next)
      ? next
      : homePathForRole(session.user.role);
    navigate(target, { replace: true });
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    setError(null);
    setSubmitting(true);

    try {
      if (DEMO_AUTH_ENABLED) {
        finishLogin(demoLogin({ identifier, password }));
        return;
      }
      finishLogin(await loginRequest({ identifier, password }));
    } catch (cause) {
      setError(loginErrorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-dvh flex-col bg-ocean-950 text-white">
      <header className="flex items-center justify-between px-4 py-4 sm:px-8">
        <Link to="/" className="flex items-center gap-2">
          <span className="grid size-9 place-items-center rounded-xl bg-white/10">
            <Waves aria-hidden="true" className="size-5 text-white" />
          </span>
          <span className="text-base font-black tracking-tight">MarAberto</span>
        </Link>
        <Link
          to="/"
          className="inline-flex items-center gap-1.5 rounded-xl border border-white/20 px-3 py-1.5 text-xs font-bold text-white/90 transition hover:bg-white/10"
        >
          <ArrowLeft aria-hidden="true" className="size-3.5" />
          Voltar ao mapa
        </Link>
      </header>

      <main className="flex flex-1 items-center justify-center px-4 pb-10">
        <div className="grid w-full max-w-3xl gap-4 md:grid-cols-[1fr_0.9fr]">
          <section className="rounded-2xl border border-ocean-900/10 bg-white p-5 text-ocean-950 shadow-2xl sm:p-6">
            <div className="flex items-center gap-2.5 border-b border-ocean-100 pb-3">
              <span className="grid size-10 place-items-center rounded-xl bg-ocean-950 text-white">
                <ShieldCheck aria-hidden="true" className="size-5" />
              </span>
              <div>
                <h1 className="text-base font-black">Acesso restrito</h1>
                <p className="text-[11px] text-ocean-600">
                  Operação, gestão e administração do MarAberto
                </p>
              </div>
            </div>

            <form onSubmit={handleSubmit} className="mt-4 grid gap-3">
              {error ? (
                <p
                  role="alert"
                  className="rounded-xl border border-coral-200 bg-coral-50 p-2.5 text-xs font-semibold text-coral-900"
                >
                  {error}
                </p>
              ) : null}

              <div>
                <label
                  htmlFor="login-identifier"
                  className="block text-xs font-bold text-ocean-950"
                >
                  Matrícula, e-mail ou usuário
                </label>
                <div className="relative mt-1">
                  <User
                    aria-hidden="true"
                    className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ocean-400"
                  />
                  <input
                    id="login-identifier"
                    type="text"
                    required
                    autoComplete="username"
                    autoCapitalize="none"
                    spellCheck={false}
                    maxLength={120}
                    value={identifier}
                    onChange={(event) => setIdentifier(event.target.value)}
                    placeholder="Ex.: operador@maraberto.dev"
                    className="h-10 w-full rounded-xl border border-ocean-200 bg-white pl-9 pr-3 text-sm text-ocean-950 focus:border-ocean-600 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label
                  htmlFor="login-password"
                  className="block text-xs font-bold text-ocean-950"
                >
                  Senha
                </label>
                <div className="relative mt-1">
                  <Lock
                    aria-hidden="true"
                    className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ocean-400"
                  />
                  <input
                    id="login-password"
                    type={showPassword ? "text" : "password"}
                    required
                    autoComplete="current-password"
                    maxLength={200}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="••••••••"
                    className="h-10 w-full rounded-xl border border-ocean-200 bg-white pl-9 pr-10 text-sm text-ocean-950 focus:border-ocean-600 focus:outline-none"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    aria-label={showPassword ? "Ocultar senha" : "Mostrar senha"}
                    aria-pressed={showPassword}
                    className="absolute right-1.5 top-1/2 grid size-8 -translate-y-1/2 place-items-center rounded-lg text-ocean-500 hover:bg-ocean-50 hover:text-ocean-800"
                  >
                    {showPassword ? (
                      <EyeOff aria-hidden="true" className="size-4" />
                    ) : (
                      <Eye aria-hidden="true" className="size-4" />
                    )}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={submitting}
                className="mt-1 inline-flex h-11 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 text-sm font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
              >
                {submitting ? (
                  <Loader2
                    aria-hidden="true"
                    className="size-4 animate-spin"
                  />
                ) : (
                  <ShieldCheck aria-hidden="true" className="size-4" />
                )}
                {submitting ? "Entrando..." : "Entrar"}
              </button>

              <p className="text-[11px] leading-relaxed text-ocean-600">
                A sessão fica apenas na memória deste navegador. Em emergência,
                ligue <strong>193</strong>: o MarAberto é apoio, não substitui o
                acionamento oficial.
              </p>
            </form>
          </section>

          <aside className="rounded-2xl border border-white/10 bg-white/5 p-5 text-white/90 backdrop-blur sm:p-6">
            <h2 className="text-sm font-black text-white">Perfis e áreas</h2>
            <ul className="mt-2 grid gap-1.5 text-xs text-white/75">
              <li>
                <strong className="text-white">Guarda-vidas</strong> · mesa de
                SOS e ocorrências (<code>/operador</code>)
              </li>
              <li>
                <strong className="text-white">Coordenação</strong> · equipe e
                ocorrências (<code>/gestao</code>)
              </li>
              <li>
                <strong className="text-white">TI</strong> · usuários, postos e
                configuração (<code>/admin</code>)
              </li>
            </ul>

            {DEMO_AUTH_ENABLED ? (
              <DemoAccounts
                onPick={(accountIdentifier, accountPassword) => {
                  setIdentifier(accountIdentifier);
                  setPassword(accountPassword);
                  setError(null);
                }}
              />
            ) : null}
          </aside>
        </div>
      </main>
    </div>
  );
}

function loginErrorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.status === 401) {
      return "Credenciais inválidas. Confira o identificador e a senha.";
    }
    if (cause.status === 403) {
      return (
        cause.problem?.detail ??
        "Esta conta está inativa. Procure a coordenação ou a TI."
      );
    }
    if (cause.network) {
      return "Sem conexão com o servidor. Tente novamente em instantes.";
    }
    return cause.problem?.detail ?? cause.message;
  }
  if (cause instanceof Error) {
    return cause.message;
  }
  return "Não foi possível entrar. Tente novamente.";
}

interface DemoAccountsProps {
  readonly onPick: (identifier: string, password: string) => void;
}

function DemoAccounts({ onPick }: DemoAccountsProps) {
  return (
    <div className="mt-4 rounded-xl border border-amber-300/40 bg-amber-300/10 p-3">
      <p className="text-[11px] font-bold uppercase tracking-wider text-amber-200">
        Atalho de desenvolvimento
      </p>
      <p className="mt-1 text-[11px] leading-relaxed text-white/70">
        Preenche o formulário com uma conta local fictícia. Não envia nada ao
        servidor e só existe quando <code>VITE_DEMO_AUTH=true</code>.
      </p>
      <ul className="mt-2 grid gap-1.5">
        {DEMO_ACCOUNTS.map((account) => (
          <li key={account.identifier}>
            <button
              type="button"
              onClick={() => onPick(account.identifier, account.password)}
              className="flex w-full items-center justify-between gap-2 rounded-lg border border-white/15 bg-white/5 px-2.5 py-1.5 text-left text-[11px] font-semibold text-white/90 transition hover:bg-white/10"
            >
              <span>{ROLE_LABEL[account.user.role]}</span>
              <code className="text-white/60">{account.identifier}</code>
            </button>
          </li>
        ))}
      </ul>
      <p className="mt-2 text-[10px] text-white/50">
        Senha de demonstração: <code>demo1234</code>
      </p>
    </div>
  );
}
