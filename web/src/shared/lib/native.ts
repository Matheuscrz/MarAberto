import { CapacitorHttp } from '@capacitor/core';
import { KeepAwake } from '@capacitor-community/keep-awake';
import { Geolocation } from '@capacitor/geolocation';
import { LocalNotifications } from '@capacitor/local-notifications';
import { SplashScreen } from '@capacitor/splash-screen';
import { appPlatform } from '@/shared/lib/platform';

/**
 * Adaptadores dos plugins nativos (Capacitor, ADR-019).
 *
 * <p>Este é o único módulo, além de `platform.ts`, que importa `@capacitor/*`.
 * Nenhuma feature fala com plugin diretamente: o restante do app usa
 * `shared/lib/geolocation.ts` e `shared/lib/pwa.ts`/`sos-alarm`, que escolhem
 * entre esta implementação e a do navegador.
 */

export type NativePermission = 'granted' | 'denied' | 'prompt';

export type NativeFailure = 'denied' | 'timeout' | 'unavailable';

export class NativePluginError extends Error {
  readonly reason: NativeFailure;

  constructor(reason: NativeFailure) {
    super(reason);
    this.name = 'NativePluginError';
    this.reason = reason;
  }
}

export interface NativeDevicePosition {
  readonly latitude: number;
  readonly longitude: number;
  readonly accuracyMeters: number;
}

/** Posição pontual pelo GPS do aparelho, com permissão pedida no gesto. */
export async function nativeDevicePosition(options: {
  readonly timeout?: number;
  readonly maximumAge?: number;
} = {}): Promise<NativeDevicePosition> {
  let permission = await Geolocation.checkPermissions();
  if (permission.location !== 'granted') {
    permission = await Geolocation.requestPermissions({
      permissions: ['location'],
    });
  }
  if (permission.location !== 'granted') {
    throw new NativePluginError('denied');
  }

  try {
    const position = await Geolocation.getCurrentPosition({
      enableHighAccuracy: true,
      timeout: options.timeout ?? 10_000,
      maximumAge: options.maximumAge ?? 0,
    });
    return {
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
      accuracyMeters: position.coords.accuracy,
    };
  } catch (error) {
    throw new NativePluginError(failureOf(error));
  }
}

function failureOf(error: unknown): NativeFailure {
  const message = error instanceof Error ? error.message.toLowerCase() : '';
  if (message.includes('denied') || message.includes('permission')) {
    return 'denied';
  }
  if (message.includes('timeout') || message.includes('timed out')) {
    return 'timeout';
  }
  return 'unavailable';
}

const SOS_CHANNEL_ID = 'sos';

function normalizePermission(value: string): NativePermission {
  if (value === 'granted' || value === 'denied') {
    return value;
  }
  return 'prompt';
}

export async function nativeNotificationPermission(): Promise<NativePermission> {
  const status = await LocalNotifications.checkPermissions();
  return normalizePermission(status.display);
}

export async function nativeRequestNotificationPermission(): Promise<NativePermission> {
  const status = await LocalNotifications.requestPermissions();
  return normalizePermission(status.display);
}

/**
 * Identificador estável de 32 bits a partir do `callId`.
 *
 * <p>O plugin exige inteiro; o hash permite fechar a notificação do mesmo
 * chamado sem guardar nada no aparelho.
 */
export function sosNotificationId(callId: string): number {
  let hash = 2_166_136_261;
  for (let index = 0; index < callId.length; index += 1) {
    hash ^= callId.charCodeAt(index);
    hash = Math.imul(hash, 16_777_619);
  }
  return (hash >>> 0) % 2_147_483_647 + 1;
}

export interface NativeNotificationInput {
  readonly id: number;
  readonly title: string;
  readonly body: string;
  readonly data?: Record<string, string>;
}

/** Notificação nativa persistente (o service worker não existe no WebView). */
export async function nativeShowNotification(
  input: NativeNotificationInput,
): Promise<void> {
  await ensureSosChannel();
  await LocalNotifications.schedule({
    notifications: [
      {
        id: input.id,
        title: input.title,
        body: input.body,
        channelId: SOS_CHANNEL_ID,
        // Fica na central até o chamado ser assumido: `closeSosNotification`
        // cancela pelo mesmo id.
        ongoing: true,
        autoCancel: true,
        extra: input.data,
      },
    ],
  });
}

export async function nativeCancelNotification(id: number): Promise<void> {
  await LocalNotifications.cancel({ notifications: [{ id }] });
}

async function ensureSosChannel(): Promise<void> {
  if (appPlatform() !== 'android') {
    return;
  }
  try {
    await LocalNotifications.createChannel({
      id: SOS_CHANNEL_ID,
      name: 'SOS',
      description: 'Chamados de emergência despachados para o seu plantão.',
      importance: 5,
      visibility: 1,
      vibration: true,
      lights: true,
      lightColor: '#DC2626',
    });
  } catch {
    // Canal já existe ou a versão do Android não usa canais; o schedule
    // continua funcionando com o canal padrão.
  }
}

export async function nativeKeepAwake(): Promise<boolean> {
  try {
    await KeepAwake.keepAwake();
    return true;
  } catch {
    return false;
  }
}

export async function nativeAllowSleep(): Promise<void> {
  try {
    await KeepAwake.allowSleep();
  } catch {
    // Tela já liberada pelo sistema: nada a fazer.
  }
}

export interface NativeHttpRequestInput {
  readonly url: string;
  readonly method?: string;
  readonly headers?: Record<string, string>;
  /** Corpo já serializado (as chamadas do app são JSON ou vazio). */
  readonly data?: string;
  readonly connectTimeout?: number;
  readonly readTimeout?: number;
}

export interface NativeHttpResponse {
  readonly status: number;
  readonly headers: Record<string, string>;
  readonly data: unknown;
  readonly url: string;
}

/**
 * HTTP nativo (sem CORS), transporte de todas as chamadas do app.
 *
 * <p>O plugin core `CapacitorCookies` registra o `CookieHandler` do processo
 * com o `CookieManager` do WebView, então o `HttpURLConnection` por trás do
 * `CapacitorHttp` envia e guarda os cookies do backend (o `Set-Cookie` do
 * refresh, inclusive) sem nenhum token em storage web.
 */
export async function nativeHttpRequest(
  input: NativeHttpRequestInput,
): Promise<NativeHttpResponse> {
  const response = await CapacitorHttp.request({
    url: input.url,
    method: input.method ?? 'GET',
    headers: input.headers,
    data: input.data,
    connectTimeout: input.connectTimeout,
    readTimeout: input.readTimeout,
  });
  return {
    status: response.status,
    headers: response.headers ?? {},
    data: response.data,
    url: response.url ?? input.url,
  };
}

/**
 * Esconde o splash da marca quando o app terminou de montar.
 *
 * <p>O splash é configurado com `launchAutoHide: false` em
 * `capacitor.config.ts` justamente para sair no momento certo: cobrindo o boot
 * do WebView e a primeira renderização, não um tempo fixo. No navegador não há
 * splash nativo, então a chamada é ignorada.
 */
export async function hideNativeSplash(fadeOutDuration = 150): Promise<void> {
  if (appPlatform() === 'web') return;
  try {
    await SplashScreen.hide({ fadeOutDuration });
  } catch {
    // Melhor esforço: sem splash nativo disponível, não há o que esconder.
  }
}
