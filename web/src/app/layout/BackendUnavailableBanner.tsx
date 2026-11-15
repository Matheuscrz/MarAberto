import { useEffect } from "react";
import { ServerCrash } from "lucide-react";
import { apiRequest } from "@/shared/lib/api-client";
import { useBackendStatus } from "@/shared/lib/backend-status";
import { useOnlineStatus } from "@/shared/lib/pwa";

/** Intervalo da sondagem enquanto o backend está fora do ar. */
const PROBE_INTERVAL_MS = 15_000;

/**
 * Aviso de backend indisponível.
 *
 * <p>O mapa pode continuar desenhando o que estava em cache. O problema é o
 * silêncio: sem bandeira, sem posto e sem aviso, a praia parece segura. O texto
 * diz explicitamente que a ausência de sinalização não é prova de segurança e
 * repete o 193. Quando o backend volta a responder, o aviso desaparece.
 */
export function BackendUnavailableBanner() {
  const status = useBackendStatus();
  const online = useOnlineStatus();

  // Com a rede do aparelho caída, o OfflineBanner já explica o motivo e evita
  // duas faixas dizendo a mesma coisa.
  const visible = status === "unavailable" && online;

  useEffect(() => {
    if (status !== "unavailable") {
      return;
    }
    let cancelled = false;

    // Qualquer resposta marca disponível no próprio cliente HTTP; a chamada
    // aqui é só o empurrão periódico para descobrir a volta do servidor sem
    // depender de outra tela estar consultando.
    const probe = () => {
      if (cancelled) {
        return;
      }
      void apiRequest<unknown>("/api/v1/flags", { method: "GET" }).catch(() => {
        // Continua indisponível; o próximo ciclo tenta de novo.
      });
    };

    probe();
    const timer = window.setInterval(probe, PROBE_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [status]);

  if (!visible) {
    return null;
  }

  return (
    <output
      aria-live="assertive"
      className="pointer-events-auto block rounded-xl border border-coral-800/40 bg-coral-700 px-3 py-2.5 text-sm font-semibold text-white shadow-lg"
    >
      <span className="flex items-start gap-2">
        <ServerCrash aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        <span>
          O sistema do MarAberto está indisponível. As bandeiras e os avisos do
          mapa podem estar desatualizados — a ausência de sinalização{" "}
          <strong>não significa que a praia está segura</strong>. Em uma
          emergência, ligue 193.
        </span>
      </span>
    </output>
  );
}
