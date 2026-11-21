import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/shared/lib/api-client";
import { fetchManagementTeam } from "@/features/management/api/team";
import type { ManagementTeam } from "@/features/management/types";

export type ManagementTeamStatus = "loading" | "ready" | "error";

export interface ManagementTeamState {
  readonly team: ManagementTeam | null;
  readonly status: ManagementTeamStatus;
  readonly message: string | null;
  readonly reload: () => void;
}

/**
 * Mapa de situação consulta a equipe a cada 15 s com a aba visível.
 *
 * <p>A posição do plantão é sobrescrita no servidor a cada 60 s, então 15 s de
 * leitura é o suficiente para a tela parecer viva. Aba escondida não consulta
 * nada; ao voltar/reconectar a leitura é imediata, igual aos outros polls do
 * mapa. Falha depois de já haver dados mantém a última foto na tela.
 */
const POLL_MS = 15_000;

export function useManagementTeam(): ManagementTeamState {
  const [team, setTeam] = useState<ManagementTeam | null>(null);
  const [status, setStatus] = useState<ManagementTeamStatus>("loading");
  const [message, setMessage] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    let timer = 0;
    let hasData = false;

    const schedule = () => {
      timer = window.setTimeout(() => {
        void run();
      }, POLL_MS);
    };

    const run = async () => {
      if (cancelled) return;
      if (document.hidden) {
        schedule();
        return;
      }
      try {
        const next = await fetchManagementTeam();
        if (cancelled) return;
        hasData = true;
        setTeam(next);
        setStatus("ready");
        setMessage(null);
      } catch (error) {
        if (cancelled) return;
        if (!hasData) setStatus("error");
        setMessage(teamErrorMessage(error));
      }
      schedule();
    };

    void run();

    const onVisible = () => {
      if (document.hidden) return;
      window.clearTimeout(timer);
      void run();
    };

    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", onVisible);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", onVisible);
    };
  }, [attempt]);

  const reload = useCallback(() => {
    setStatus((current) => (current === "error" ? "loading" : current));
    setAttempt((value) => value + 1);
  }, []);

  return { team, status, message, reload };
}

function teamErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return (
      error.problem?.detail ??
      error.problem?.title ??
      (error.network
        ? "Sem conexão com o servidor."
        : `Falha (${error.status}) ao carregar a equipe.`)
    );
  }
  return "Não foi possível carregar a equipe agora.";
}
