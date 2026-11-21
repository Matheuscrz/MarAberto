import { useEffect, useState } from "react";
import { fetchBathingAreaOptions } from "@/features/management/api/bathing-areas";

export type BathingAreasStatus = "loading" | "ready" | "error";

/**
 * O mapeamento da coordenação existe?
 *
 * <p>A home `/gestao` é inútil sem área de banho importada: o mapa não tem
 * escopo, os pins não têm onde cair e o convite certo é `/gestao/importar`.
 * A consulta é a mesma lista de áreas ativas usada nos formulários; falha de
 * rede não esconde o mapa, só deixa o convite sem resposta.
 */
export function useBathingAreasAvailability(): {
  readonly status: BathingAreasStatus;
  readonly isEmpty: boolean;
} {
  const [status, setStatus] = useState<BathingAreasStatus>("loading");
  const [isEmpty, setIsEmpty] = useState(false);

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      try {
        const areas = await fetchBathingAreaOptions();
        if (cancelled) return;
        setIsEmpty(areas.length === 0);
        setStatus("ready");
      } catch {
        if (cancelled) return;
        setStatus("error");
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  return { status, isEmpty };
}
