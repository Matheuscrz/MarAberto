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
 * Registro do aparelho no Firebase Cloud Messaging (ADR-018 item 6).
 *
 * <p>O `google-services.json` é configuração do **app**: ele permite registrar o
 * aparelho e receber push, não enviar. O envio pelo servidor continua pendente e
 * exige uma chave de service account no backend, que este arquivo não toca.
 *
 * <p>Assim como `native.ts` e `platform.ts`, este é um ponto único de contato
 * com o plugin de push: as features chamam `startPushNotifications` e recebem
 * mensagens já normalizadas. No navegador nada acontece — o PWA não usa FCM.
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

/**
 * Log do token e das falhas de registro.
 *
 * <p>O token é o que se cola no console do Firebase para mandar uma mensagem de
 * teste; fora de desenvolvimento ele só aparece com `VITE_PUSH_DEBUG=true`,
 * para não poluir o log de um aparelho em produção.
 */
const DEBUG = import.meta.env.DEV || import.meta.env.VITE_PUSH_DEBUG === 'true';

let registration: NativePushRegistration | null = null;
let handlers: PushHandlers = {};
let permission: PushPermission = 'unsupported';
let token: string | null = null;

export function isPushSupported(): boolean {
  return isNativeApp();
}

export function readPushPermission(): PushPermission {
  return permission;
}

/** Token FCM do aparelho, quando o Firebase já respondeu. */
export function currentPushToken(): string | null {
  return token;
}

/**
 * Pede a permissão e registra o aparelho, uma vez por sessão do app.
 *
 * <p>Chamado dentro de um gesto do usuário (armar o alerta do SOS), porque o
 * iOS só mostra o pedido de permissão a partir de uma interação. Repetir a
 * chamada atualiza os ouvintes sem registrar de novo.
 */
export async function startPushNotifications(
  nextHandlers: PushHandlers = {},
): Promise<PushPermission> {
  if (!isNativeApp()) {
    permission = 'unsupported';
    return permission;
  }
  handlers = nextHandlers;

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

/** Sai do FCM e descarta o token (logout no aparelho). */
export async function stopPushNotifications(): Promise<void> {
  const current = registration;
  registration = null;
  token = null;
  handlers = {};
  permission = isNativeApp() ? 'default' : 'unsupported';
  await current?.stop().catch(() => undefined);
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
