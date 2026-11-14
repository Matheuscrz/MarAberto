import { Ban, Flag, RefreshCw } from "lucide-react";
import { Card } from "@/shared/components/Card";
import { FlagBadge } from "@/shared/components/Badges";
import { formatDateTime } from "@/shared/lib/format";
import { FLAG_COLORS, FLAG_LABELS } from "@/shared/lib/flags";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useBeachSignals } from "@/features/signals/hooks/use-signals";
import { SignalStateTag } from "@/features/signals/components/SignalStateTag";
import { SignalPhotos } from "@/features/signals/components/SignalPhotos";
import { signalState, type SignalState } from "@/features/signals/types";
interface BeachFlagCardProps {
  readonly beachId: string;
}

/**
 * Sinalização na ficha da praia (RF12/RN01).
 *
 * <p>Mostra a última bandeira de cada escopo. Bandeira vencida ou encerrada não
 * some da ficha: aparece com o selo de desatualizada e a data em que deixou de
 * valer, porque "sem sinalização" e "sinalização vencida" são coisas
 * diferentes para quem está decidindo entrar na água.
 */
export function BeachFlagCard({ beachId }: BeachFlagCardProps) {
  const { signals, status, message, reload } = useBeachSignals(beachId);
  const beachSignal = signals.find((signal) => signal.sectorId === null) ?? null;
  const sectorSignals = signals.filter((signal) => signal.sectorId !== null);

  if (status === "loading") {
    return (
      <Card>
        <output className="block text-xs font-semibold text-ocean-600">
          Carregando a sinalização
        </output>
      </Card>
    );
  }

  if (status === "error") {
    return (
      <Card className="border-dashed bg-ocean-50/40">
        <p className="text-xs font-bold text-ocean-950">
          Não foi possível carregar a sinalização
        </p>
        <p className="mt-1 text-[11px] text-ocean-700">
          {message ?? "Tente de novo em instantes."}
        </p>
        <button
          type="button"
          onClick={reload}
          className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
        >
          <RefreshCw aria-hidden="true" className="size-3.5" />
          Tentar de novo
        </button>
      </Card>
    );
  }

  return (
    <Card>
      <div className="flex items-center gap-1.5">
        <Flag aria-hidden="true" className="size-4 text-ocean-600" />
        <h2 className="text-sm font-black text-ocean-950">Sinalização</h2>
      </div>

      {signals.length === 0 ? (
        <p className="mt-2 flex items-start gap-1.5 text-xs leading-relaxed text-ocean-700">
          <Ban aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
          Nenhuma bandeira lançada para esta área. Confirme a condição no posto de
          guarda-vidas antes de entrar na água.
        </p>
      ) : (
        <>
          {beachSignal ? (
            <div className="mt-2">
              <FlagBadge flag={beachSignal.flag} />
              <p className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px] text-ocean-700">
                <SignalStateTag state={signalState(beachSignal)} compact />
                <span>
                  {validityText(
                    signalState(beachSignal),
                    formatDateTime(beachSignal.validUntil),
                  )}
                </span>
              </p>
              {beachSignal.notes ? (
                <p className="mt-1 text-[11px] leading-snug text-ocean-700">
                  {sanitizeText(beachSignal.notes, 300)}
                </p>
              ) : null}
              <SignalPhotos
                urls={beachSignal.photoUrls}
                label={beachSignal.beachName}
                compact
              />
            </div>
          ) : null}

          {sectorSignals.length > 0 ? (
            <ul className="mt-3 grid gap-2 border-t border-ocean-100 pt-2">
              {sectorSignals.map((signal) => (
                <li
                  key={signal.id}
                  className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1 text-[11px]"
                >
                  <span className="font-bold text-ocean-900">
                    {sanitizeText(signal.sectorLabel ?? "Setor", 80)}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span
                      aria-hidden="true"
                      className="size-2.5 rounded-full"
                      style={{ backgroundColor: FLAG_COLORS[signal.flag] }}
                    />
                    <span className="font-semibold text-ocean-800">
                      {FLAG_LABELS[signal.flag]}
                    </span>
                    <SignalStateTag state={signalState(signal)} compact />
                  </span>
                  <SignalPhotos
                    urls={signal.photoUrls}
                    label={signal.sectorLabel ?? signal.beachName}
                    compact
                  />
                </li>
              ))}
            </ul>
          ) : null}

          <p className="mt-2 text-[11px] leading-snug text-ocean-600">
            {beachSignal
              ? "Setor sem lançamento próprio segue a bandeira da praia."
              : "A área não tem bandeira própria; vale a do setor em que você está."}
          </p>
        </>
      )}
    </Card>
  );
}

function validityText(state: SignalState, validUntil: string): string {
  switch (state) {
    case "CURRENT":
      return `válida até ${validUntil}`;
    case "EXPIRED":
      return `venceu em ${validUntil}`;
    case "CLOSED":
      return "encerrada pelo guarda-vidas";
  }
}
