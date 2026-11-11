/**
 * Canal de alerta do SOS: sirene, vibração e notificação do sistema.
 *
 * <p>Nada aqui depende de React. O console arma o canal em um gesto do usuário
 * (abrir plantão ou tocar em "Ativar alertas"), porque navegador nenhum libera
 * áudio nem notificação sem interação. Depois disso o alarme pode tocar a
 * qualquer momento, inclusive com a aba em segundo plano.
 *
 * <p>O alerta nunca carrega coordenada nem dado do banhista: a notificação do
 * sistema mostra só distância aproximada e um convite para abrir a mesa. O
 * detalhe continua vindo da rota autenticada.
 */

export type AlertPermission = "unsupported" | "default" | "granted" | "denied";

type AudioContextConstructor = typeof AudioContext;

const ALARM_CYCLE_MS = 1_500;

let audioContext: AudioContext | null = null;

function audioContextConstructor(): AudioContextConstructor | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }
  return (
    window.AudioContext ??
    (window as unknown as { webkitAudioContext?: AudioContextConstructor })
      .webkitAudioContext
  );
}

function ensureAudioContext(): AudioContext | null {
  if (audioContext) {
    return audioContext;
  }
  const Constructor = audioContextConstructor();
  if (!Constructor) {
    return null;
  }
  try {
    audioContext = new Constructor();
  } catch {
    return null;
  }
  return audioContext;
}

/**
 * Libera o áudio no gesto do usuário.
 *
 * <p>Chamar no clique é o que permite o alarme tocar depois sem gesto: sem
 * isso o `AudioContext` fica `suspended` e a sirene é silêncio. Devolve `true`
 * quando o contexto está rodando.
 */
export function unlockAlertAudio(): boolean {
  const context = ensureAudioContext();
  if (!context) {
    return false;
  }
  if (context.state === "suspended") {
    void context.resume().catch(() => undefined);
  }
  // Um blip inaudível conclui o desbloqueio no iOS/Safari.
  try {
    const now = context.currentTime;
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    gain.gain.setValueAtTime(0.0001, now);
    oscillator.connect(gain).connect(context.destination);
    oscillator.start(now);
    oscillator.stop(now + 0.02);
  } catch {
    // Sem blip o contexto ainda pode funcionar; não é motivo para falhar.
  }
  return context.state === "running";
}

export function isAlertAudioReady(): boolean {
  return audioContext?.state === "running";
}

export function readAlertPermission(): AlertPermission {
  if (typeof window === "undefined" || !("Notification" in window)) {
    return "unsupported";
  }
  return Notification.permission as AlertPermission;
}

/** Pede permissão de notificação. Só tem efeito dentro de um gesto do usuário. */
export async function requestAlertPermission(): Promise<AlertPermission> {
  const current = readAlertPermission();
  if (current !== "default") {
    return current;
  }
  try {
    return (await Notification.requestPermission()) as AlertPermission;
  } catch {
    return "denied";
  }
}

/**
 * Arma o canal completo em um gesto: áudio liberado e permissão pedida.
 *
 * <p>Chamado ao abrir o plantão e no botão "Ativar alertas". Nunca é chamado
 * sozinho no boot — pedir permissão sem gesto é ignorado pelo navegador.
 */
export async function armSosAlerts(): Promise<{
  readonly sound: boolean;
  readonly permission: AlertPermission;
}> {
  const sound = unlockAlertAudio();
  const permission = await requestAlertPermission();
  return { sound, permission };
}

export interface AlarmHandle {
  readonly stop: () => void;
}

/**
 * Sirene em ciclo até `stop()`.
 *
 * <p>O padrão sobe e desce a frequência a cada 1,5 s — dá para reconhecer sem
 * olhar a tela. Também vibra no aparelho que suporta `navigator.vibrate`. Se o
 * áudio ainda estiver bloqueado, o alarme segue chamando `resume()` a cada
 * ciclo: assim que o usuário tocar na tela, o som entra.
 */
export function startSosAlarm(): AlarmHandle {
  let stopped = false;
  let timer: number | undefined;

  const cycle = () => {
    if (stopped) {
      return;
    }
    const context = ensureAudioContext();
    if (context) {
      if (context.state === "suspended") {
        void context.resume().catch(() => undefined);
      }
      if (context.state === "running") {
        playSirenCycle(context);
      }
    }
    if ("vibrate" in navigator) {
      navigator.vibrate([500, 200, 500, 200, 500]);
    }
    timer = window.setTimeout(cycle, ALARM_CYCLE_MS);
  };

  cycle();

  return {
    stop: () => {
      stopped = true;
      if (timer !== undefined) {
        window.clearTimeout(timer);
        timer = undefined;
      }
      if ("vibrate" in navigator) {
        navigator.vibrate(0);
      }
    },
  };
}

function playSirenCycle(context: AudioContext): void {
  try {
    const now = context.currentTime + 0.02;
    const oscillator = context.createOscillator();
    const filter = context.createBiquadFilter();
    const gain = context.createGain();

    filter.type = "lowpass";
    filter.frequency.value = 2400;

    oscillator.type = "sawtooth";
    oscillator.frequency.setValueAtTime(620, now);
    oscillator.frequency.linearRampToValueAtTime(980, now + 0.45);
    oscillator.frequency.linearRampToValueAtTime(620, now + 0.9);

    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.linearRampToValueAtTime(0.18, now + 0.06);
    gain.gain.setValueAtTime(0.18, now + 0.82);
    gain.gain.linearRampToValueAtTime(0.0001, now + 0.95);

    oscillator.connect(filter).connect(gain).connect(context.destination);
    oscillator.start(now);
    oscillator.stop(now + 1);
  } catch {
    // Falha de áudio não pode impedir o popup e a notificação.
  }
}

interface SosNotificationInput {
  readonly callId: string;
  /** Distância já formatada, quando a fila autenticada já respondeu. */
  readonly distanceLabel?: string;
}

const NOTIFICATION_TITLE = "SOS na sua área";

/**
 * Notificação do sistema para o chamado.
 *
 * <p>Usa o service worker quando ele está registrado (funciona com a aba em
 * segundo plano e no app instalado). Sem service worker — por exemplo no modo
 * de desenvolvimento — cai para a `Notification` da própria página.
 */
export async function showSosNotification(
  input: SosNotificationInput,
): Promise<void> {
  if (readAlertPermission() !== "granted") {
    return;
  }
  const body = input.distanceLabel
    ? `Banhista a ${input.distanceLabel}. Abra a mesa para assumir o chamado.`
    : "Banhista pediu ajuda. Abra a mesa para assumir o chamado.";
  const options: NotificationOptions & { renotify: boolean } = {
    body,
    tag: `sos-${input.callId}`,
    renotify: true,
    requireInteraction: true,
    silent: true,
    icon: "/icons/icon-192.png",
    badge: "/icons/icon-192.png",
    data: { url: "/operador" },
  };

  const registration = await serviceWorkerRegistration();
  if (registration) {
    try {
      await registration.showNotification(NOTIFICATION_TITLE, options);
      return;
    } catch {
      // Sem permissão de exibição pelo SW: tenta a notificação da página.
    }
  }
  try {
    const notification = new Notification(NOTIFICATION_TITLE, options);
    notification.onclick = () => {
      window.focus();
      notification.close();
    };
  } catch {
    // Plataforma sem notificação de página (iOS antigo): resta o popup + som.
  }
}

/** Fecha a notificação daquele chamado, se ainda estiver na central. */
export async function closeSosNotification(callId: string): Promise<void> {
  if (typeof window === "undefined" || !("Notification" in window)) {
    return;
  }
  const registration = await serviceWorkerRegistration();
  const notifications = await registration?.getNotifications({
    tag: `sos-${callId}`,
  });
  await Promise.all(notifications?.map((notification) => notification.close()) ?? []);
}

interface WakeLockSentinelLike {
  release: () => Promise<void>;
}

interface WakeLockNavigator {
  wakeLock?: { request: (type: "screen") => Promise<WakeLockSentinelLike> };
}

let wakeLock: WakeLockSentinelLike | null = null;

/**
 * Mantém a tela ligada enquanto o alerta está na frente.
 *
 * <p>Sem isso o aparelho apaga no meio do plantão e a sirene toca para uma tela
 * preta. A permissão é do navegador e pode ser negada: falhar aqui nunca impede
 * o popup nem o som.
 */
export async function keepScreenAwake(): Promise<void> {
  if (typeof navigator === "undefined" || wakeLock) {
    return;
  }
  const wakeLockApi = (navigator as Navigator & WakeLockNavigator).wakeLock;
  if (!wakeLockApi) {
    return;
  }
  try {
    wakeLock = await wakeLockApi.request("screen");
  } catch {
    wakeLock = null;
  }
}

export async function releaseScreenAwake(): Promise<void> {
  const current = wakeLock;
  wakeLock = null;
  try {
    await current?.release();
  } catch {
    // Sentinela já liberada pelo navegador (aba oculta): nada a fazer.
  }
}

async function serviceWorkerRegistration(): Promise<ServiceWorkerRegistration | null> {
  if (!("serviceWorker" in navigator)) {
    return null;
  }
  try {
    const registration = await navigator.serviceWorker.getRegistration();
    return registration?.active ? registration : null;
  } catch {
    return null;
  }
}
