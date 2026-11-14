import { useCallback, useEffect, useState } from "react";
import {
  fetchAdminStations,
  stationErrorMessage,
  updateStationStatus,
} from "@/features/stations/api/stations";
import type { AdminStation } from "@/features/stations/types";

export type AdminStationsStatus = "loading" | "ready" | "error";

export interface AdminStationsState {
  readonly stations: readonly AdminStation[];
  readonly status: AdminStationsStatus;
  readonly message: string | null;
  readonly feedback: string | null;
  /** Identificador da estrutura com uma alteração em voo. */
  readonly busyId: string | null;
  readonly reload: () => void;
  readonly setActive: (
    station: AdminStation,
    active: boolean,
    statusNote: string | null,
  ) => Promise<boolean>;
  /** Substitui uma estrutura já atualizada (posição) na lista carregada. */
  readonly applyUpdate: (station: AdminStation) => void;
  readonly dismissFeedback: () => void;
}

/**
 * Cadastro de postos e bases no painel da TI.
 *
 * <p>A lista é do servidor, inclusive inativas: desativar só tira a estrutura do
 * mapa público e da lista de destinos do plantão — o histórico de plantões e
 * ocorrências continua apontando para a mesma linha.
 */
export function useAdminStations(): AdminStationsState {
  const [stations, setStations] = useState<readonly AdminStation[]>([]);
  const [status, setStatus] = useState<AdminStationsStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;

    void (async () => {
      try {
        const loaded = await fetchAdminStations();
        if (cancelled) return;
        setStations(loaded);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        setStatus("error");
        setMessage(stationErrorMessage(error));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const setActive = useCallback(
    async (station: AdminStation, active: boolean, statusNote: string | null) => {
      setBusyId(station.id);
      setFeedback(null);
      try {
        const updated = await updateStationStatus(
          station.kind,
          station.id,
          active,
          statusNote,
        );
        setStations((current) =>
          current.map((item) => (item.id === updated.id ? updated : item)),
        );
        setFeedback(
          active
            ? `${updated.name} voltou a aparecer no mapa público.`
            : `${updated.name} saiu do mapa público. O histórico de plantões e ocorrências continua.`,
        );
        return true;
      } catch (error) {
        setFeedback(stationErrorMessage(error));
        return false;
      } finally {
        setBusyId(null);
      }
    },
    [],
  );

  const applyUpdate = useCallback((updated: AdminStation) => {
    setStations((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
    setFeedback(`${updated.name}: posição atualizada com a leitura do aparelho.`);
  }, []);

  const reload = useCallback(() => {
    setStatus("loading");
    setAttempt((value) => value + 1);
  }, []);

  const dismissFeedback = useCallback(() => setFeedback(null), []);

  return {
    stations,
    status,
    message,
    feedback,
    busyId,
    reload,
    setActive,
    applyUpdate,
    dismissFeedback,
  };
}
