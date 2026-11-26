import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Loader2, ShieldCheck } from "lucide-react";
import { ApiError } from "@/shared/lib/api-client";
import { changePasswordRequest } from "@/features/auth/api";
import { DEMO_AUTH_ENABLED } from "@/features/auth/demo";
import { useAuthSession } from "@/features/auth/session";

interface ChangePasswordFormProps {
  /** `true` quando a conta tem senha temporária pendente de troca obrigatória. */
  readonly pending?: boolean;
  readonly idPrefix?: string;
}

/**
 * Formulário reutilizável de troca de senha (obrigatória no primeiro acesso em
 * `/trocar-senha` ou voluntária na página de perfil `/perfil`).
 */
export function ChangePasswordForm({
  pending = false,
  idPrefix = "password",
}: ChangePasswordFormProps) {
  const navigate = useNavigate();
  const clearSession = useAuthSession((state) => state.clearSession);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [showPasswords, setShowPasswords] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    setError(null);
    const validation = validatePasswordChange(
      currentPassword,
      newPassword,
      confirmation,
    );
    if (validation) {
      setError(validation);
      return;
    }

    setSubmitting(true);
    try {
      if (DEMO_AUTH_ENABLED) {
        clearSession("/login?senha=alterada");
        navigate("/login?senha=alterada", { replace: true });
        return;
      }
      await changePasswordRequest({ currentPassword, newPassword });
      clearSession("/login?senha=alterada");
      navigate("/login?senha=alterada", { replace: true });
    } catch (cause) {
      setError(changePasswordErrorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="grid gap-3">
      {error ? (
        <p
          role="alert"
          className="rounded-xl border border-coral-200 bg-coral-50 p-2.5 text-xs font-semibold text-coral-900"
        >
          {error}
        </p>
      ) : null}

      <PasswordField
        id={`${idPrefix}-current`}
        label={pending ? "Senha atual (temporária)" : "Senha atual"}
        autoComplete="current-password"
        value={currentPassword}
        onChange={setCurrentPassword}
        visible={showPasswords}
      />
      <PasswordField
        id={`${idPrefix}-new`}
        label="Nova senha"
        autoComplete="new-password"
        value={newPassword}
        onChange={setNewPassword}
        visible={showPasswords}
      />
      <PasswordField
        id={`${idPrefix}-confirm`}
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
        A senha precisa ter pelo menos 12 caracteres, com letras e números, e
        ser diferente da senha atual.
      </p>

      <button
        type="submit"
        disabled={submitting}
        className="mt-1 inline-flex h-11 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-4 text-sm font-bold text-white transition hover:bg-ocean-900 disabled:opacity-60"
      >
        {submitting ? (
          <Loader2 aria-hidden="true" className="size-4 animate-spin" />
        ) : (
          <ShieldCheck aria-hidden="true" className="size-4" />
        )}
        {submitting ? "Salvando..." : "Salvar nova senha"}
      </button>
    </form>
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

function validatePasswordChange(
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
