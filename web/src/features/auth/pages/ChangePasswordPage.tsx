import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { KeyRound, Loader2, ShieldCheck } from "lucide-react";
import { ApiError } from "@/shared/lib/api-client";
import { changePasswordRequest } from "@/features/auth/api";
import { DEMO_AUTH_ENABLED } from "@/features/auth/demo";
import { useAuthSession } from "@/features/auth/session";

/**
 * Troca obrigatória da senha temporária (ADR-027).
 *
 * <p>Enquanto `mustChangePassword` for verdadeiro o servidor responde 403 em
 * todas as rotas de negócio; esta é a única tela liberada. Ao concluir, o
 * backend revoga as sessões e limpa o cookie — por isso a sessão local também
 * é descartada e o operador volta ao login com a senha definitiva.
 */
export function ChangePasswordPage() {
  const navigate = useNavigate();
  const user = useAuthSession((state) => state.user);
  const clearSession = useAuthSession((state) => state.clearSession);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [showPasswords, setShowPasswords] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const pending = user?.mustChangePassword ?? false;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    setError(null);
    const validation = validate(currentPassword, newPassword, confirmation);
    if (validation) {
      setError(validation);
      return;
    }

    setSubmitting(true);
    try {
      if (DEMO_AUTH_ENABLED) {
        // No modo demo não há backend: só valida a política localmente.
        clearSession();
        navigate("/login?senha=alterada", { replace: true });
        return;
      }
      await changePasswordRequest({ currentPassword, newPassword });
      clearSession();
      navigate("/login?senha=alterada", { replace: true });
    } catch (cause) {
      setError(changePasswordErrorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-dvh flex-col bg-ocean-950 text-white">
      <header className="flex items-center gap-2 px-4 py-4 sm:px-8">
        <img
          src="/brand/mark-96.png"
          alt=""
          aria-hidden="true"
          className="size-9 rounded-xl"
        />
        <span className="text-base font-black tracking-tight">MarAberto</span>
      </header>

      <main className="flex flex-1 items-center justify-center px-4 pb-10">
        <section className="w-full max-w-md rounded-2xl border border-ocean-900/10 bg-white p-5 text-ocean-950 shadow-2xl sm:p-6">
          <div className="flex items-center gap-2.5 border-b border-ocean-100 pb-3">
            <span className="grid size-10 place-items-center rounded-xl bg-ocean-950 text-white">
              <KeyRound aria-hidden="true" className="size-5" />
            </span>
            <div>
              <h1 className="text-base font-black">
                {pending ? "Defina sua senha" : "Trocar senha"}
              </h1>
              <p className="text-[11px] text-ocean-600">
                {pending
                  ? "Sua senha temporária expira em 24 h e precisa ser trocada antes de continuar."
                  : "A troca encerra as sessões abertas e exige novo login."}
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

            <PasswordField
              id="current-password"
              label="Senha atual (temporária)"
              autoComplete="current-password"
              value={currentPassword}
              onChange={setCurrentPassword}
              visible={showPasswords}
            />
            <PasswordField
              id="new-password"
              label="Nova senha"
              autoComplete="new-password"
              value={newPassword}
              onChange={setNewPassword}
              visible={showPasswords}
            />
            <PasswordField
              id="confirm-password"
              label="Confirme a nova senha"
              autoComplete="new-password"
              value={confirmation}
              onChange={setConfirmation}
              visible={showPasswords}
            />

            <label className="flex items-center gap-2 text-[11px] font-semibold text-ocean-700">
              <input
                type="checkbox"
                checked={showPasswords}
                onChange={(event) => setShowPasswords(event.target.checked)}
                className="size-3.5 rounded border-ocean-300"
              />
              Mostrar as senhas
            </label>

            <p className="text-[11px] leading-relaxed text-ocean-500">
              A senha definitiva precisa ter pelo menos 12 caracteres, com
              letras e números, e ser diferente da temporária.
            </p>

            <button
              type="submit"
              disabled={submitting}
              className="mt-1 inline-flex h-11 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 text-sm font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
            >
              {submitting ? (
                <Loader2 aria-hidden="true" className="size-4 animate-spin" />
              ) : (
                <ShieldCheck aria-hidden="true" className="size-4" />
              )}
              {submitting ? "Salvando..." : "Salvar nova senha"}
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}

interface PasswordFieldProps {
  readonly id: string;
  readonly label: string;
  readonly autoComplete: string;
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly visible: boolean;
}

function PasswordField({
  id,
  label,
  autoComplete,
  value,
  onChange,
  visible,
}: PasswordFieldProps) {
  return (
    <div>
      <label htmlFor={id} className="block text-xs font-bold text-ocean-950">
        {label}
      </label>
      <input
        id={id}
        type={visible ? "text" : "password"}
        required
        autoComplete={autoComplete}
        maxLength={128}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1 h-10 w-full rounded-xl border border-ocean-200 bg-white px-3 text-sm text-ocean-950 focus:border-ocean-600 focus:outline-none"
      />
    </div>
  );
}

function validate(
  currentPassword: string,
  newPassword: string,
  confirmation: string,
): string | null {
  if (!currentPassword) {
    return "Informe a senha atual.";
  }
  if (newPassword.length < 12) {
    return "A nova senha precisa ter pelo menos 12 caracteres.";
  }
  if (!/[A-Za-z]/.test(newPassword) || !/[0-9]/.test(newPassword)) {
    return "A nova senha precisa ter letras e números.";
  }
  if (newPassword === currentPassword) {
    return "A nova senha precisa ser diferente da atual.";
  }
  if (newPassword !== confirmation) {
    return "A confirmação não confere com a nova senha.";
  }
  return null;
}

function changePasswordErrorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.status === 401) {
      return "Senha atual incorreta.";
    }
    if (cause.network) {
      return "Sem conexão com o servidor. Tente novamente em instantes.";
    }
    return cause.problem?.detail ?? cause.message;
  }
  if (cause instanceof Error) {
    return cause.message;
  }
  return "Não foi possível trocar a senha. Tente novamente.";
}
