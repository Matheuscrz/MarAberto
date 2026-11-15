import { useSyncExternalStore } from "react";

/**
 * Estado da comunicação com o backend, compartilhado por todo o app.
 *
 * <p>Não é o estado da rede do aparelho (`navigator.onLine`): é a resposta do
 * servidor. Uma rede conectada com o backend fora do ar é justamente o caso em
 * que o mapa fica sem bandeiras e o banhista pode achar que a praia está
 * segura. O cliente HTTP marca indisponível em falha de rede e em respostas
 * de gateway (`502/503/504`); qualquer resposta do backend marca disponível de
 * novo, e o aviso some na hora.
 */
export type BackendStatus = "online" | "unavailable";

type Listener = () => void;

let status: BackendStatus = "online";
const listeners = new Set<Listener>();

function setStatus(next: BackendStatus): void {
  if (status === next) {
    return;
  }
  status = next;
  for (const listener of listeners) {
    listener();
  }
}

/** O backend respondeu (mesmo com erro de aplicação): o canal está de pé. */
export function reportBackendReachable(): void {
  setStatus("online");
}

/** Falha de rede ou gateway sem resposta: não dá para confiar no mapa. */
export function reportBackendUnreachable(): void {
  setStatus("unavailable");
}

function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function getSnapshot(): BackendStatus {
  return status;
}

export function useBackendStatus(): BackendStatus {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}
