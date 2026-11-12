import { useEffect, useState } from 'react';
import { isNativeApp } from '@/shared/lib/platform';

/*
 * No app Capacitor o service worker não existe (WKWebView no iOS) ou não é
 * confiável (WebView do Android): o bundle já é local e o alerta do SOS usa
 * notificação nativa. Registrar ali só produziria erro silencioso.
 */
export function registerServiceWorker(): void {
  if (isNativeApp() || !('serviceWorker' in navigator) || import.meta.env.DEV) {
    return;
  }
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Falha de registro não pode impedir o uso do app.
    });
  });
}

export function isStandalone(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

/**
 * Observa o estado de conexão. `navigator.onLine` não é garantia de que o
 * backend está alcançável: é apenas um indício para orientar a interface.
 */
export function useOnlineStatus(): boolean {
  const [online, setOnline] = useState(() => navigator.onLine);

  useEffect(() => {
    const handleOnline = () => setOnline(true);
    const handleOffline = () => setOnline(false);
    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return online;
}
