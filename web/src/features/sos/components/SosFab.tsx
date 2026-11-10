import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Phone, Siren, X } from "lucide-react";
import { DEMO_DATA_NOTICE } from "@/shared/lib/notices";
import { useOverlay } from "@/shared/lib/overlay";

export function SosFab() {
  const [confirming, setConfirming] = useState(false);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const close = () => setConfirming(false);

  useOverlay(confirming, panelRef, close);

  return (
    <>
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className="fixed bottom-8 right-4 z-40 inline-flex size-16 items-center justify-center rounded-full bg-coral-600 text-white shadow-xl shadow-coral-700/30 ring-1 ring-coral-700 transition hover:bg-coral-500 focus-visible:outline-2"
        aria-label="Abrir SOS com confirmação"
      >
        <Siren aria-hidden="true" className="size-7" />
        <span className="sr-only">SOS</span>
      </button>

      {confirming ? (
        <div className="fixed inset-0 z-50 grid place-items-center p-4">
          <div
            aria-hidden="true"
            onClick={close}
            className="absolute inset-0 cursor-default bg-ocean-950/50"
          />
          <div
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="sos-confirm-title"
            className="relative w-full max-w-sm rounded-2xl border border-ocean-900/10 bg-white p-5 shadow-2xl outline-none"
          >
            <button
              type="button"
              onClick={close}
              aria-label="Fechar confirmação de SOS"
              className="absolute right-3 top-3 grid size-9 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-50"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
            <h2
              id="sos-confirm-title"
              className="flex items-center gap-2 pr-8 text-xl font-black text-ocean-950"
            >
              <Siren
                aria-hidden="true"
                className="size-6 shrink-0 text-coral-600"
              />
              Acionar o SOS?
            </h2>
            <p className="mt-2 text-sm text-ocean-700">
              O SOS envia sua localização para a central de guarda-vidas da
              instalação. Ele precisa de internet e não substitui o 193. Deseja
              continuar?
            </p>
            <p className="mt-2 text-xs text-amber-800">{DEMO_DATA_NOTICE}</p>
            <div className="mt-4 grid gap-2">
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  data-autofocus=""
                  onClick={close}
                  className="inline-flex min-h-12 items-center justify-center rounded-xl border-2 border-ocean-950 bg-white px-4 py-2 font-bold text-ocean-950"
                >
                  Cancelar
                </button>
                <Link
                  to="/sos"
                  onClick={close}
                  className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-coral-600 px-4 py-2 font-black text-white hover:bg-coral-500"
                >
                  <Siren aria-hidden="true" className="size-5" />
                  Abrir SOS
                </Link>
              </div>
              <a
                href="tel:193"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl bg-ocean-950 px-4 py-2 font-bold text-white"
              >
                <Phone aria-hidden="true" className="size-5" />
                Ligar 193 agora
              </a>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
