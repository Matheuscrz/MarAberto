import { useRef } from 'react';
import { NavLink } from 'react-router-dom';
import { BookOpen, Info, Map, Moon, Phone, Waves, X } from 'lucide-react';
import { useOverlay } from '../lib/overlay';

const items = [
  { to: '/', label: 'Início (mapa)', icon: Waves, end: true },
  { to: '/praias', label: 'Praias', icon: Map, end: false },
  { to: '/mares', label: 'Marés', icon: Moon, end: false },
  { to: '/aprender', label: 'Aprender', icon: BookOpen, end: false },
  { to: '/sobre', label: 'Sobre o projeto', icon: Info, end: false },
];

/**
 * Menu principal do banhista: gaveta com todas as telas. Aberto pelo botão de
 * hambúrguer no cabeçalho — navegação de uma mão só, pensada para uso na areia.
 */
export function AppMenu({ open, onClose }: { open: boolean; onClose: () => void }) {
  const panelRef = useRef<HTMLElement | null>(null);

  // Foco inicial, Escape/Tab e retorno de foco quando a gaveta fecha.
  useOverlay(open, panelRef, onClose);

  if (!open) {
    return null;
  }

  const close = () => {
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50">
      <button
        type="button"
        aria-label="Fechar menu"
        onClick={close}
        className="absolute inset-0 cursor-default bg-ocean-950/40 backdrop-blur-[2px]"
        tabIndex={-1}
      />
      <aside
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label="Menu de telas do MarAberto"
        className="absolute right-0 top-0 flex h-dvh w-80 max-w-[85vw] flex-col overflow-y-auto bg-white shadow-2xl outline-none"
      >
        <div className="flex items-center justify-between border-b border-ocean-900/10 px-4 py-3">
          <p className="flex items-center gap-2 text-lg font-black text-ocean-950">
            <Waves aria-hidden="true" className="size-5 text-ocean-600" />
            MarAberto
          </p>
          <button
            type="button"
            onClick={close}
            aria-label="Fechar menu"
            className="grid size-10 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-50"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>

        <nav aria-label="Telas" className="grid gap-1 p-3">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              onClick={close}
              className={({ isActive }) =>
                [
                  'flex min-h-12 items-center gap-3 rounded-xl px-3 font-semibold',
                  isActive
                    ? 'bg-ocean-100 text-ocean-900'
                    : 'text-ocean-700 hover:bg-ocean-50 hover:text-ocean-900',
                ].join(' ')
              }
            >
              <item.icon aria-hidden="true" className="size-5" />
              {item.label}
            </NavLink>
          ))}

          <NavLink
            to="/sos"
            onClick={close}
            className={({ isActive }) =>
              [
                'mt-2 flex min-h-14 items-center gap-3 rounded-xl px-3 font-black',
                isActive
                  ? 'bg-coral-700 text-white'
                  : 'bg-coral-600 text-white hover:bg-coral-500',
              ].join(' ')
            }
          >
            Abrir o SOS
          </NavLink>
        </nav>

        <div className="mt-auto border-t border-ocean-900/10 p-4">
          <p className="text-xs text-ocean-600">
            Em emergência real, o caminho mais rápido continua sendo o acionamento oficial.
          </p>
          <a
            href="tel:193"
            onClick={close}
            className="mt-2 inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-ocean-950 px-4 py-2 font-bold text-white"
          >
            <Phone aria-hidden="true" className="size-5" />
            Ligar 193
          </a>
        </div>
      </aside>
    </div>
  );
}
