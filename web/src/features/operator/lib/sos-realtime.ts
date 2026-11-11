import { Client, type IMessage } from "@stomp/stompjs";
import { currentAccessToken } from "@/features/auth";
import type { SosNotification } from "@/features/operator/types";

/**
 * Fila pessoal do SOS. O cliente assina o **destino de usuário**; o broker
 * reescreve para a fila da sessão autenticada pelo JWT do frame `CONNECT`.
 * Assinar `/queue/sos` não recebe nada.
 */
const SOS_SUBSCRIPTION = "/user/queue/sos";

const RECONNECT_DELAY_MS = 5_000;
const HEARTBEAT_MS = 10_000;

export type SosRealtimeState = "connecting" | "live" | "offline";

export interface SosRealtimeHandle {
  readonly stop: () => void;
}

interface SosRealtimeOptions {
  readonly onNotification: (notification: SosNotification) => void;
  readonly onStateChange?: (state: SosRealtimeState) => void;
}

/**
 * URL do endpoint STOMP.
 *
 * Mesma origem por padrão (Nginx/Vite fazem proxy de `/ws`). Quando o app é
 * servido separado da API (`VITE_API_BASE_URL`), a URL do backend é convertida
 * para `ws(s)://…/ws` — o token nunca vai na query string.
 */
function brokerUrl(): string {
  const base = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");
  const url = new URL(base || window.location.origin, window.location.origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = `${url.pathname.replace(/\/$/, "")}/ws`;
  url.search = "";
  url.hash = "";
  return url.toString();
}

function parseNotification(body: string): SosNotification | null {
  try {
    const value = JSON.parse(body) as Partial<SosNotification>;
    if (
      typeof value.callId !== "string" ||
      typeof value.status !== "string"
    ) {
      return null;
    }
    return value as SosNotification;
  } catch {
    return null;
  }
}

/**
 * Abre o WebSocket autenticado do console.
 *
 * O `beforeConnect` roda a cada tentativa, então a reconexão sempre usa o
 * access token atual (em memória). Sem sessão, o cliente para e o polling de
 * reconciliação assume — o chamado nunca depende do socket para existir.
 */
export function connectSosRealtime(
  options: SosRealtimeOptions,
): SosRealtimeHandle {
  const client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: RECONNECT_DELAY_MS,
    heartbeatIncoming: HEARTBEAT_MS,
    heartbeatOutgoing: HEARTBEAT_MS,
    beforeConnect: () => {
      const token = currentAccessToken();
      if (!token) {
        void client.deactivate();
        options.onStateChange?.("offline");
        return;
      }
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    onConnect: () => {
      options.onStateChange?.("live");
      client.subscribe(SOS_SUBSCRIPTION, (message: IMessage) => {
        const notification = parseNotification(message.body);
        if (notification) {
          options.onNotification(notification);
        }
      });
    },
    onWebSocketClose: () => options.onStateChange?.("connecting"),
    onStompError: () => options.onStateChange?.("connecting"),
  });

  options.onStateChange?.("connecting");
  client.activate();

  return {
    stop: () => {
      options.onStateChange?.("offline");
      void client.deactivate();
    },
  };
}
