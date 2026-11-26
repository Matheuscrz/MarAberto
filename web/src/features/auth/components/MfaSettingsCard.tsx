import { useState } from "react";
import { useNavigate } from "react-router-dom";
import QRCode from "qrcode";
import {
  Check,
  Copy,
  KeyRound,
  Loader2,
  ShieldCheck,
  ShieldOff,
} from "lucide-react";
import { ApiError } from "@/shared/lib/api-client";
import {
  confirmMfaRequest,
  disableMfaRequest,
  setupMfaRequest,
  type MfaSetupData,
} from "@/features/auth/api";
import { useAuthSession, useCurrentUser } from "@/features/auth/session";

interface MfaSettingsCardProps {
  readonly className?: string;
}

/**
 * Controle opcional de MFA (TOTP + códigos de recuperação) para qualquer
 * operador autenticado (LIFEGUARD, COORDINATOR e ADMIN). O QR Code é gerado
 * localmente a partir da URI `otpauth://` (sem chamadas externas, respeitando
 * a CSP).
 */
export function MfaSettingsCard({ className = "mt-6" }: MfaSettingsCardProps) {
  const navigate = useNavigate();
  const user = useCurrentUser();
  const clearSession = useAuthSession((s) => s.clearSession);
  const setMfaEnabled = useAuthSession((s) => s.setMfaEnabled);

  const mfaActive = Boolean(user?.mfaEnabled);
  const [setupData, setSetupData] = useState<MfaSetupData | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [secretCopied, setSecretCopied] = useState(false);
  const [confirmCode, setConfirmCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<readonly string[] | null>(
    null,
  );
  const [disabling, setDisabling] = useState(false);
  const [disablePassword, setDisablePassword] = useState("");
  const [disableCode, setDisableCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!user) {
    return null;
  }

  const handleStartSetup = async () => {
    setBusy(true);
    setError(null);
    setRecoveryCodes(null);
    setSecretCopied(false);
    try {
      const data = await setupMfaRequest();
      const qr = await QRCode.toDataURL(data.otpauthUri, {
        width: 220,
        margin: 2,
        errorCorrectionLevel: "M",
        color: {
          dark: "#0a2540",
          light: "#ffffff",
        },
      }).catch(() => null);
      setSetupData(data);
      setQrDataUrl(qr);
    } catch (cause) {
      setError(mfaErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const handleCopySecret = async () => {
    if (!setupData?.secret) return;
    try {
      await navigator.clipboard.writeText(setupData.secret);
      setSecretCopied(true);
      window.setTimeout(() => setSecretCopied(false), 2000);
    } catch {
      // Clipboard indisponível no contexto atual; a chave continua selecionável.
    }
  };

  const handleConfirm = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const result = await confirmMfaRequest(confirmCode.trim());
      setMfaEnabled(true);
      setSetupData(null);
      setQrDataUrl(null);
      setConfirmCode("");
      setRecoveryCodes(result.recoveryCodes);
    } catch (cause) {
      setError(mfaErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  const handleDisable = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await disableMfaRequest({
        currentPassword: disablePassword,
        code: disableCode.trim(),
      });
      setMfaEnabled(false);
      setDisabling(false);
      setDisablePassword("");
      setDisableCode("");
      clearSession("/login");
      navigate("/login", { replace: true });
    } catch (cause) {
      setError(mfaErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section
      className={`${className} rounded-2xl border border-ocean-200 bg-white p-4 shadow-sm`.trim()}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h2 className="flex items-center gap-1.5 text-sm font-bold text-ocean-950">
            <KeyRound aria-hidden="true" className="size-4 text-ocean-700" />
            Autenticação em dois fatores (MFA / TOTP) — opcional
          </h2>
          <p className="mt-0.5 text-xs text-ocean-600">
            {mfaActive
              ? "Ativa na sua conta. Um código do autenticador será solicitado ao entrar."
              : "Desativada. Você pode ativar opcionalmente com Google Authenticator, FreeOTP ou similar."}
          </p>
        </div>

        {!mfaActive && !setupData ? (
          <button
            type="button"
            disabled={busy}
            onClick={() => void handleStartSetup()}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl bg-ocean-950 px-3 py-1.5 text-xs font-bold text-white hover:bg-ocean-900 disabled:opacity-60"
          >
            {busy ? (
              <Loader2 aria-hidden="true" className="size-3.5 animate-spin" />
            ) : (
              <ShieldCheck aria-hidden="true" className="size-3.5" />
            )}
            Ativar MFA
          </button>
        ) : null}

        {mfaActive && !disabling ? (
          <button
            type="button"
            disabled={busy}
            onClick={() => {
              setDisabling(true);
              setError(null);
            }}
            className="inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-coral-300 bg-coral-50 px-3 py-1.5 text-xs font-bold text-coral-900 hover:bg-coral-100"
          >
            <ShieldOff aria-hidden="true" className="size-3.5" />
            Desativar MFA
          </button>
        ) : null}
      </div>

      {error ? (
        <p
          role="alert"
          className="mt-3 rounded-xl border border-coral-200 bg-coral-50 p-2.5 text-xs font-semibold text-coral-900"
        >
          {error}
        </p>
      ) : null}

      {setupData ? (
        <form
          onSubmit={(e) => void handleConfirm(e)}
          className="mt-3 grid gap-4 rounded-xl border border-ocean-100 bg-ocean-50/50 p-4"
        >
          <p className="text-xs text-ocean-800">
            Escaneie o QR Code no aplicativo autenticador (Google Authenticator,
            FreeOTP, Aegis ou similar) ou informe a chave manual abaixo. Em
            seguida, digite o código de 6 dígitos para confirmar:
          </p>

          <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center">
            {qrDataUrl ? (
              <img
                src={qrDataUrl}
                alt="QR Code para cadastrar o segundo fator no aplicativo autenticador"
                width={220}
                height={220}
                className="size-44 shrink-0 rounded-xl border border-ocean-200 bg-white p-2 shadow-sm"
              />
            ) : null}

            <div className="min-w-0 flex-1 space-y-2">
              <p className="text-[11px] font-bold uppercase tracking-wider text-ocean-600">
                Chave manual (Base32)
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <code className="break-all rounded-lg border border-ocean-200 bg-white px-3 py-2 font-mono text-xs font-bold text-ocean-950">
                  {setupData.secret}
                </code>
                <button
                  type="button"
                  onClick={() => void handleCopySecret()}
                  className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-ocean-200 bg-white px-2.5 text-xs font-bold text-ocean-800 transition hover:bg-ocean-50"
                >
                  {secretCopied ? (
                    <Check
                      aria-hidden="true"
                      className="size-3.5 text-emerald-600"
                    />
                  ) : (
                    <Copy aria-hidden="true" className="size-3.5" />
                  )}
                  {secretCopied ? "Copiada" : "Copiar chave"}
                </button>
              </div>
              <p className="text-[11px] leading-snug text-ocean-600">
                O QR Code é gerado localmente no seu navegador — o segredo não é
                enviado para serviços externos.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <input
              type="text"
              required
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={12}
              value={confirmCode}
              onChange={(e) => setConfirmCode(e.target.value)}
              placeholder="Código de 6 dígitos"
              aria-label="Código de 6 dígitos do autenticador"
              className="h-9 w-48 rounded-lg border border-ocean-200 bg-white px-3 font-mono text-xs text-ocean-950 focus:border-ocean-600 focus:outline-none"
            />
            <button
              type="submit"
              disabled={busy}
              className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-ocean-950 px-3 text-xs font-bold text-white hover:bg-ocean-900 disabled:opacity-60"
            >
              Confirmar ativação
            </button>
            <button
              type="button"
              onClick={() => {
                setSetupData(null);
                setQrDataUrl(null);
                setConfirmCode("");
                setError(null);
              }}
              className="h-9 rounded-lg px-2.5 text-xs font-semibold text-ocean-600 hover:bg-ocean-100"
            >
              Cancelar
            </button>
          </div>
        </form>
      ) : null}

      {recoveryCodes ? (
        <output className="mt-3 block rounded-xl border border-amber-300 bg-amber-50 p-3">
          <p className="text-xs font-bold text-amber-950">
            Códigos de recuperação (exibidos uma única vez — guarde em local
            seguro):
          </p>
          <ul className="mt-2 grid grid-cols-2 gap-1.5 font-mono text-xs font-bold text-amber-950 sm:grid-cols-4">
            {recoveryCodes.map((code) => (
              <li
                key={code}
                className="rounded border border-amber-200 bg-white px-2 py-1 text-center"
              >
                {code}
              </li>
            ))}
          </ul>
        </output>
      ) : null}

      {disabling ? (
        <form
          onSubmit={(e) => void handleDisable(e)}
          className="mt-3 grid gap-2 rounded-xl border border-coral-200 bg-coral-50/40 p-3 sm:grid-cols-3"
        >
          <input
            type="password"
            required
            value={disablePassword}
            onChange={(e) => setDisablePassword(e.target.value)}
            placeholder="Senha atual"
            aria-label="Senha atual"
            className="h-9 rounded-lg border border-ocean-200 bg-white px-3 text-xs text-ocean-950"
          />
          <input
            type="text"
            required
            value={disableCode}
            onChange={(e) => setDisableCode(e.target.value)}
            placeholder="Código TOTP ou recuperação"
            aria-label="Código TOTP ou recuperação"
            className="h-9 rounded-lg border border-ocean-200 bg-white px-3 font-mono text-xs text-ocean-950"
          />
          <div className="flex items-center gap-2">
            <button
              type="submit"
              disabled={busy}
              className="h-9 rounded-lg bg-coral-700 px-3 text-xs font-bold text-white hover:bg-coral-800 disabled:opacity-60"
            >
              Confirmar
            </button>
            <button
              type="button"
              onClick={() => setDisabling(false)}
              className="h-9 rounded-lg px-2 text-xs font-semibold text-ocean-600 hover:bg-ocean-100"
            >
              Cancelar
            </button>
          </div>
        </form>
      ) : null}
    </section>
  );
}

function mfaErrorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    return cause.problem?.detail ?? "Não foi possível atualizar o MFA.";
  }
  return "Não foi possível atualizar o MFA.";
}
