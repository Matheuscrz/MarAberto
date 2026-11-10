import { WifiOff } from 'lucide-react';
import { useOnlineStatus } from '@/shared/lib/pwa';

export function OfflineBanner() {
  const online = useOnlineStatus();

  if (online) {
    return null;
  }

  return (
    <div role="status" className="bg-amber-100 px-4 py-2 text-sm font-semibold text-amber-950">
      <div className="mx-auto flex max-w-6xl items-center gap-2">
        <WifiOff aria-hidden="true" className="size-4" />
        Você está offline. Conteúdo em cache pode estar desatualizado. Para emergência, ligue 193.
      </div>
    </div>
  );
}
