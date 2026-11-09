import { useState } from 'react';
import { NavLink, Link, Outlet, useLocation } from 'react-router-dom';
import {
  BookOpen,
  Home,
  Map,
  Menu,
  Moon,
  Phone,
  ShieldCheck,
  Siren,
  Waves,
} from 'lucide-react';
import { OfflineBanner } from './OfflineBanner';
import { AppMenu } from './AppMenu';
import { SosFab } from './SosFab';

const navItems = [
  { to: '/', label: 'Início', icon: Home, end: true },
  { to: '/praias', label: 'Praias', icon: Map },
  { to: '/mares', label: 'Marés', icon: Moon },
  { to: '/aprender', label: 'Aprender', icon: BookOpen },
];

function navClass({ isActive }: { isActive: boolean }): string {
  return [
    'rounded-xl px-3 py-2 text-sm font-semibold transition',
    isActive
      ? 'bg-ocean-100 text-ocean-900'
      : 'text-ocean-700 hover:bg-ocean-50 hover:text-ocean-900',
  ].join(' ');
}

/**
 * Casca do PWA do banhista. Na home, o `main` vira full-bleed (mapa de tela
 * cheia estilo Google/Apple Maps) e o rodapé é omitido; nas demais telas o
 * conteúdo volta ao contêiner de leitura.
 */
export function Layout() {
  const { pathname } = useLocation();
  const isHome = pathname === '/';
  const isSosRoute = pathname.startsWith('/sos');
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <div className="flex min-h-dvh flex-col bg-sand-50 text-ocean-950">
      <a
        href="#conteudo"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-ocean-950 focus:px-4 focus:py-2 focus:text-white"
      >
        Pular para o conteúdo
      </a>

      <OfflineBanner />

      <header
        className={
          isHome
            ? // Home: cabeçalho flutuante sobre o mapa, estilo app de mapas.
              'absolute inset-x-0 top-0 z-40 border-b border-ocean-900/5 bg-white/80 backdrop-blur'
            : 'sticky top-0 z-40 border-b border-ocean-900/10 bg-white/90 backdrop-blur'
        }
      >
        <div className="mx-auto flex w-full max-w-6xl items-center gap-3 px-4 py-3">
          <Link to="/" className="flex items-center gap-2 rounded-lg focus-visible:outline-2">
            <span className="grid size-10 place-items-center rounded-xl bg-ocean-950 text-white">
              <Waves aria-hidden="true" className="size-6" />
            </span>
            <span className="text-lg font-black tracking-tight">MarAberto</span>
          </Link>

          <nav aria-label="Navegação principal" className="ml-auto hidden items-center gap-1 md:flex">
            {navItems.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={navClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          {!isSosRoute ? (
            <Link
              to="/sos"
              className="ml-auto inline-flex min-h-12 items-center gap-2 rounded-xl bg-coral-600 px-4 py-2 font-bold text-white shadow-sm transition hover:bg-coral-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-coral-700 md:ml-2"
            >
              <Siren aria-hidden="true" className="size-5" />
              SOS
            </Link>
          ) : null}

          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            aria-label="Abrir menu de telas"
            aria-haspopup="dialog"
            aria-expanded={menuOpen}
            className="grid size-11 place-items-center rounded-xl border border-ocean-200 bg-white text-ocean-800 hover:bg-ocean-50"
          >
            <Menu aria-hidden="true" className="size-5" />
          </button>
        </div>
      </header>

      <main
        id="conteudo"
        className={
          isHome
            ? // Ocupa a viewport inteira: o mapa corre por baixo do cabeçalho.
              'relative h-dvh'
            : 'mx-auto w-full max-w-6xl flex-1 px-4 pb-28 pt-6 md:pb-12'
        }
      >
        <Outlet />
      </main>

      {!isHome ? (
        <footer className="border-t border-ocean-900/10 bg-white">
          <div className="mx-auto grid w-full max-w-6xl gap-4 px-4 py-8 text-sm text-ocean-700 md:grid-cols-3">
            <div>
              <p className="flex items-center gap-2 font-bold text-ocean-950">
                <ShieldCheck aria-hidden="true" className="size-5 text-emerald-600" />
                Ferramenta de apoio
              </p>
              <p className="mt-2">
                O MarAberto não substitui o acionamento oficial. Em emergência, ligue{' '}
                <a className="font-bold text-coral-700 underline" href="tel:193">
                  193
                </a>
                .
              </p>
              <Link
                to="/sobre"
                className="mt-2 inline-flex min-h-11 items-center font-semibold text-ocean-700 underline hover:text-ocean-900"
              >
                Sobre o projeto
              </Link>
            </div>
            <div>
              <p className="font-bold text-ocean-950">Privacidade por padrão</p>
              <p className="mt-2">
                Sem trilha de localização. A posição do SOS é usada apenas para o atendimento e não
                fica no armazenamento do navegador.
              </p>
            </div>
            <div>
              <p className="font-bold text-ocean-950">Emergência</p>
              <a
                href="tel:193"
                className="mt-2 inline-flex min-h-12 items-center gap-2 rounded-xl bg-ocean-950 px-4 py-2 font-bold text-white"
              >
                <Phone aria-hidden="true" className="size-5" />
                Ligar 193
              </a>
            </div>
          </div>
        </footer>
      ) : null}

      <nav
        aria-label="Navegação inferior"
        className="fixed inset-x-0 bottom-0 z-40 border-t border-ocean-900/10 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
      >
        <div className="mx-auto grid max-w-md grid-cols-4">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  [
                    'flex min-h-16 flex-col items-center justify-center gap-1 text-xs font-semibold',
                    isActive ? 'text-ocean-900' : 'text-ocean-500',
                  ].join(' ')
                }
              >
                <Icon aria-hidden="true" className="size-5" />
                {item.label}
              </NavLink>
            );
          })}
        </div>
      </nav>

      {!isSosRoute ? <SosFab /> : null}
      <AppMenu open={menuOpen} onClose={() => setMenuOpen(false)} />
    </div>
  );
}
