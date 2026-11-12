import { Capacitor } from '@capacitor/core';

/**
 * Plataforma em que o app está rodando.
 *
 * <p>O mesmo build roda no navegador (PWA), no WebView do Android e no do iOS.
 * Só este módulo e `native.ts` conhecem o Capacitor: as features continuam
 * importando apenas `shared/` e o PWA segue funcionando sem plugin quando a
 * plataforma é `web`.
 */
export type AppPlatform = 'android' | 'ios' | 'web';

export function appPlatform(): AppPlatform {
  const platform = Capacitor.getPlatform();
  return platform === 'android' || platform === 'ios' ? platform : 'web';
}

/** `true` dentro do app empacotado (WebView), `false` no navegador/PWA. */
export function isNativeApp(): boolean {
  return Capacitor.isNativePlatform();
}
