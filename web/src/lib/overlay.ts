import { useEffect, type RefObject } from 'react';

/**
 * Utilitários de acessibilidade para overlays (menu gaveta, diálogos, ficha
 * sobre o mapa): foco inicial, retorno do foco, Escape e percursos de Tab.
 */

const FOCUSABLE =
  'a[href], button:not([disabled]), select, textarea, input, [tabindex]:not([tabindex="-1"])';

/** Move o foco para o elemento marcado com `data-autofocus` ou, na falta dele, para o primeiro elemento focalizável. */
export function focusFirstInside(container: HTMLElement | null): void {
  const preferred = container?.querySelector<HTMLElement>('[data-autofocus]');
  const target = preferred ?? container?.querySelector<HTMLElement>(FOCUSABLE);
  target?.focus();
}

/** Fecha com Escape e mantém o Tab preso dentro do overlay. */
export function handleOverlayKeyboard(
  event: { key: string; shiftKey: boolean; preventDefault(): void },
  container: HTMLElement | null,
  onClose: () => void,
): void {
  if (event.key === 'Escape') {
    onClose();
    return;
  }
  if (event.key !== 'Tab' || !container) {
    return;
  }

  const focusable = Array.from(
    container.querySelectorAll<HTMLElement>(FOCUSABLE),
  ).filter((element) => element.offsetParent !== null);
  if (focusable.length === 0) {
    event.preventDefault();
    return;
  }

  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = container.ownerDocument.activeElement;

  if (event.shiftKey && (active === first || !container.contains(active))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && active === last) {
    event.preventDefault();
    first.focus();
  }
}

/**
 * Gancho completo de overlay: enquanto `active`, foca o painel (respeitando
 * `data-autofocus`), liga Escape e o percurso de Tab e devolve o foco ao
 * elemento anterior ao desmontar.
 */
export function useOverlay(
  active: boolean,
  panelRef: RefObject<HTMLElement | null>,
  onClose: () => void,
): void {
  useEffect(() => {
    if (!active) {
      return;
    }

    const previous =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    focusFirstInside(panelRef.current);

    const handler = (event: KeyboardEvent) => {
      handleOverlayKeyboard(event, panelRef.current, onClose);
    };
    document.addEventListener('keydown', handler);

    return () => {
      document.removeEventListener('keydown', handler);
      previous?.focus();
    };
    // onClose muda a cada render do dono; o efeito pode rerodar sem efeito.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, panelRef]);
}
