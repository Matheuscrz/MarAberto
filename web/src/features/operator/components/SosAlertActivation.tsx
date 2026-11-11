import { BellRing, BellOff, Volume2 } from "lucide-react";
import type { AlertPermission } from "@/features/operator/lib/sos-alarm";

interface SosAlertActivationProps {
  readonly permission: AlertPermission;
  readonly soundReady: boolean;
  readonly activating: boolean;
  readonly onActivate: () => void;
}

/**
 * Estado do canal de alerta e o botão que o arma.
 *
 * <p>Áudio e notificação do sistema só são liberados dentro de um gesto do
 * usuário. O console arma sozinho ao abrir o plantão, mas se a pessoa entrou de
 * plantão em outro dispositivo, recarregou a página ou negou a permissão antes,
 * é aqui que ela conserta — sem precisar procurar configuração do navegador.
 */
export function SosAlertActivation({
  permission,
  soundReady,
  activating,
  onActivate,
}: SosAlertActivationProps) {
  const active = soundReady && permission === "granted";
  if (active) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-300 bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-900">
        <BellRing aria-hidden="true" className="size-3.5" />
        Som e notificação ativos
      </span>
    );
  }

  const blocked = permission === "denied";
  return (
    <button
      type="button"
      onClick={onActivate}
      disabled={activating}
      title={
        blocked
          ? "A notificação está bloqueada para este site; o som ainda pode ser liberado."
          : "Libera a sirene e a notificação do sistema neste aparelho."
      }
      className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-amber-400 bg-amber-50 px-3 py-1 text-xs font-bold text-amber-900 transition hover:bg-amber-100 disabled:cursor-progress disabled:opacity-70"
    >
      {blocked ? (
        <BellOff aria-hidden="true" className="size-3.5" />
      ) : (
        <Volume2 aria-hidden="true" className="size-3.5" />
      )}
      {activating
        ? "Ativando…"
        : blocked
          ? "Notificação bloqueada · ativar som"
          : "Ativar som e notificação"}
    </button>
  );
}
