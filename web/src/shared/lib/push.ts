import { apiRequest, apiRequestVoid } from '@/shared/lib/api-client';
import {
  nativePushPermission,
  nativePushRequestPermission,
  nativeRegisterPush,
  type NativePermission,
  type NativePushMessage,
  type NativePushRegistration,
} from '@/shared/lib/native';
import { isNativeApp } from '@/shared/lib/platform';

/**
 * Registro de push do operador:
 * - No app Capacitor Android: registra o token FCM (via `google-services.json`)
 *   no backend (`POST /api/v1/push/subscriptions`).
 * - No PWA/navegador: obtém a chave pública VAPID (`GET /api/v1/push/vapid-public-key`),
 *   inscreve o `PushManager` do Service Worker e envia `endpoint`, `p256dh` e `auth`.
 */

export type PushPermission = 'unsupported' | 'default' | 'granted' | 'denied';

export interface PushMessage {
  readonly title: string;
  readonly body: string;
  readonly data: Record<string, string>;
}

export interface PushHandlers {
  /** Mensagem recebida com o app aberto. */
  readonly onMessage?: (message: PushMessage) => void;
  /** Toque na notificação, com o app fechado ou em segundo plano. */
  readonly onAction?: (message: PushMessage) => void;
}

interface VapidPublicKeyResponse {
  readonly enabled: boolean;
  readonly publicKey: string | null;
}

const DEBUG = import.meta.env.DEV || import.meta.env.VITE_PUSH_DEBUG === 'true';

let registration: NativePushRegistration | null = null;
let webPushEndpoint: string | null = null;
let handlers: PushHandlers = {};
let permission: PushPermission = 'unsupported';
let token: string | null = null;

export function isPushSupported(): boolean {
  if (isNativeApp()) {
    return true;
  }
  return (
    typeof window !== 'undefined' &&
    'Notification' in window &&
    'serviceWorker' in navigator &&
    'PushManager' in window
  );
}

export function readPushPermission(): PushPermission {
  return permission;
}

/** Token FCM do aparelho, quando o Firebase já respondeu. */
export function currentPushToken(): string | null {
  return token;
}

/**
 * Pede a permissão e registra o aparelho ou navegador, uma vez por sessão.
 *
 * <p>Chamado dentro de um gesto do usuário (armar o alerta do SOS). Repetir a
 * chamada atualiza os ouvintes sem registrar de novo.
 */
export async function startPushNotifications(
  nextHandlers: PushHandlers = {},
): Promise<PushPermission> {
  handlers = nextHandlers;
  if (!isNativeApp()) {
    return startWebPush();
  }

  const current = await nativePushPermission().catch(() => 'denied' as const);
  permission = asPushPermission(current);
  if (permission === 'default') {
    const requested = await nativePushRequestPermission().catch(
      () => 'denied' as const,
    );
    permission = asPushPermission(requested);
  }
  if (permission !== 'granted') {
    if (DEBUG) {
      console.info(`[push] permissão ${permission}; aparelho não registrado`);
    }
    return permission;
  }

  if (registration) {
    return permission;
  }
  try {
    registration = await nativeRegisterPush({
      onToken: (value) => {
        token = value;
        if (DEBUG) {
          console.info('[push] token FCM:', value);
        }
        void apiRequestVoid('/api/v1/push/subscriptions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ channel: 'FCM', token: value }),
        }).catch((error) => {
          if (DEBUG) {
            console.warn('[push] falha ao enviar token FCM ao servidor:', error);
          }
        });
      },
      onTokenError: (message) => {
        if (DEBUG) {
          console.warn('[push] falha no registro:', message);
        }
      },
      onMessage: (message) => handlers.onMessage?.(toPushMessage(message)),
      onAction: (message) => handlers.onAction?.(toPushMessage(message)),
    });
  } catch (error) {
    if (DEBUG) {
      console.warn('[push] plugin indisponível:', error);
    }
    permission = 'unsupported';
  }
  return permission;
}

async function startWebPush(): Promise<PushPermission> {
  if (!isPushSupported()) {
    permission = 'unsupported';
    return permission;
  }

  const current = Notification.permission as PushPermission;
  permission = current;
  if (permission === 'default') {
    permission = (await Notification.requestPermission().catch(
      () => 'denied' as const,
    )) as PushPermission;
  }
  if (permission !== 'granted') {
    return permission;
  }

  if (webPushEndpoint) {
    return permission;
  }

  try {
    const vapid = await apiRequest<VapidPublicKeyResponse>(
      '/api/v1/push/vapid-public-key',
      { method: 'GET' },
    );
    if (!vapid.enabled || !vapid.publicKey) {
      return permission;
    }

    const swRegistration = await navigator.serviceWorker.ready;
    let subscription = await swRegistration.pushManager.getSubscription();
    if (!subscription) {
      subscription = await swRegistration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapid.publicKey),
      });
    }
    const json = subscription.toJSON();
    const p256dh = json.keys?.p256dh;
    const auth = json.keys?.auth;
    if (!subscription.endpoint || !p256dh || !auth) {
      return permission;
    }

    await apiRequestVoid('/api/v1/push/subscriptions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        channel: 'WEB_PUSH',
        endpoint: subscription.endpoint,
        p256dh,
        auth,
      }),
    });
    webPushEndpoint = subscription.endpoint;
  } catch (error) {
    if (DEBUG) {
      console.warn('[push] falha ao registrar Web Push (VAPID):', error);
    }
  }
  return permission;
}

/** Remove a inscrição de push do servidor e descarta o token/subscription (logout). */
export async function stopPushNotifications(): Promise<void> {
  const current = registration;
  const currentToken = token;
  const currentEndpoint = webPushEndpoint;

  registration = null;
  token = null;
  webPushEndpoint = null;
  handlers = {};
  permission = isPushSupported() ? 'default' : 'unsupported';

  if (currentToken) {
    await apiRequestVoid('/api/v1/push/subscriptions', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: currentToken }),
    }).catch(() => undefined);
  }
  if (currentEndpoint) {
    await apiRequestVoid('/api/v1/push/subscriptions', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ endpoint: currentEndpoint }),
    }).catch(() => undefined);
    if ('serviceWorker' in navigator) {
      const sw = await navigator.serviceWorker.getRegistration().catch(() => undefined);
      const sub = await sw?.pushManager.getSubscription().catch(() => undefined);
      await sub?.unsubscribe().catch(() => undefined);
    }
  }
  await current?.stop().catch(() => undefined);
}

export function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const buffer = new ArrayBuffer(raw.length);
  const output = new Uint8Array(buffer);
  for (let i = 0; i < raw.length; i += 1) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

function toPushMessage(message: NativePushMessage): PushMessage {
  return {
    title: message.title,
    body: message.body,
    data: message.data,
  };
}

/** O plugin responde `prompt`; a interface trata como `default`. */
function asPushPermission(value: NativePermission): PushPermission {
  return value === 'prompt' ? 'default' : value;
}
