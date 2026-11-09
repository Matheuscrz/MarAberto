import { useRef } from "react";
import { Link, NavLink } from "react-router-dom";
import {
  BookOpen,
  Info,
  Map,
  Moon,
  Phone,
  Siren,
  Waves,
  X,
} from "lucide-react";
import { useOverlay } from "../lib/overlay";

const items = [
  { to: "/", label: "Início (mapa)", icon: Waves, end: true },
  { to: "/praias", label: "Praias", icon: Map, end: false },
  { to: "/mares", label: "Marés", icon: Moon, end: false },
  { to: "/aprender", label: "Aprender", icon: BookOpen, end: false },
  { to: "/sobre", label: "Sobre o projeto", icon: Info, end: false },
];

/**
 * Menu principal do banhista: card flutuante sobre o mapa, no mesmo padrão
 * visual dos demais painéis (cantos arredondados, blur e margens). Abre do
 * mesmo lado do botão de hambúrguer, no canto superior esquerdo.
 */
export function AppMenu({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const panelRef = useRef<HTMLElement | null>(null);

  // Foco inicial, Escape/Tab e retorno de foco quando a gaveta fecha.
  useOverlay(open, panelRef, onClose);

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50">
      <button
        type="button"
        aria-label="Fechar menu"
        onClick={onClose}
        className="absolute inset-0 cursor-default bg-ocean-950/30 backdrop-blur-[2px]"
        tabIndex={-1}
      />

      <aside
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Menu de telas do MarAberto"
        className="absolute inset-y-3 left-3 flex w-[min(20rem,calc(100vw-1.5rem))] flex-col overflow-hidden rounded-2xl border border-ocean-900/10 bg-white/95 shadow-2xl backdrop-blur outline-none"
      >
        <div className="flex shrink-0 items-center justify-between border-b border-ocean-100 px-3 py-2.5">
          <Link
            to="/"
            onClick={onClose}
            className="flex items-center gap-2 rounded-lg focus-visible:outline-2"
          >
            <span className="grid size-7 place-items-center rounded-lg bg-ocean-950 text-white">
              <Waves aria-hidden="true" className="size-4" />
            </span>
            <span className="text-sm font-black text-ocean-950">MarAberto</span>
          </Link>
          <button
            type="button"
            data-autofocus=""
            onClick={onClose}
            aria-label="Fechar menu"
            className="grid size-9 place-items-center rounded-lg text-ocean-600 transition hover:bg-ocean-50 hover:text-ocean-900"
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>

        <nav
          aria-label="Telas"
          className="grid content-start gap-1 overflow-y-auto p-2"
        >
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={onClose}
                className={({ isActive }) =>
                  [
                    "flex min-h-11 items-center gap-2.5 rounded-xl px-3 text-sm font-semibold transition",
                    isActive
                      ? "bg-ocean-950 text-white"
                      : "text-ocean-700 hover:bg-ocean-50 hover:text-ocean-900",
                  ].join(" ")
                }
              >
                <Icon aria-hidden="true" className="size-4" />
                {item.label}
              </NavLink>
            );
          })}

          <NavLink
            to="/sos"
            onClick={onClose}
            className={({ isActive }) =>
              [
                "mt-1 flex min-h-12 items-center gap-2.5 rounded-xl px-3 text-sm font-black text-white transition",
                isActive
                  ? "bg-coral-700"
                  : "bg-coral-600 hover:bg-coral-500",
              ].join(" ")
            }
          >
            <Siren aria-hidden="true" className="size-4" />
            Abrir o SOS
          </NavLink>
        </nav>

        <div className="mt-auto shrink-0 border-t border-ocean-100 bg-sand-50/60 p-3">
          <p className="text-[11px] leading-snug text-ocean-600">
            Em emergência real, o caminho mais rápido continua sendo o
            acionamento oficial.
          </p>
          <a
            href="tel:193"
            onClick={onClose}
            className="mt-2 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-xl bg-ocean-950 px-3 text-sm font-bold text-white transition hover:bg-ocean-900"
          >
            <Phone aria-hidden="true" className="size-4" />
            Ligar 193
          </a>
        </div>
      </aside>
    </div>
  );
}
