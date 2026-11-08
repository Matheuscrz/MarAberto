import { useEffect, useState } from 'react';
import { Download, X } from 'lucide-react';
import { isStandalone } from '../lib/pwa';
import { useAppStore } from '../store/app-store';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export function InstallPrompt() {
  const [event, setEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const dismissed = useAppStore((state) => state.installPromptDismissed);
  const dismissInstallPrompt = useAppStore((state) => state.dismissInstallPrompt);

  useEffect(() => {
    const handler = (nativeEvent: Event) => {
      nativeEvent.preventDefault();
      setEvent(nativeEvent as BeforeInstallPromptEvent);
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  if (!event || dismissed || isStandalone()) {
    return null;
  }

  const install = async () => {
    await event.prompt();
    await event.userChoice;
    setEvent(null);
  };

  return (
    <div className="flex items-center gap-3 rounded-xl border border-ocean-200 bg-ocean-50 px-4 py-3 text-sm text-ocean-900">
      <Download aria-hidden="true" className="size-5 shrink-0" />
      <p className="flex-1">
        Instale o MarAberto para abrir mais rápido na praia. O SOS continua exigindo conexão.
      </p>
      <button
        type="button"
        onClick={install}
        className="min-h-10 rounded-lg bg-ocean-950 px-3 py-1.5 font-bold text-white"
      >
        Instalar
      </button>
      <button
        type="button"
        aria-label="Dispensar convite de instalação"
        onClick={dismissInstallPrompt}
        className="grid size-10 place-items-center rounded-lg text-ocean-600 hover:bg-ocean-100"
      >
        <X aria-hidden="true" className="size-4" />
      </button>
    </div>
  );
}
