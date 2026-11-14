import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  CheckCircle,
  MapPin,
  Power,
  PowerOff,
  RefreshCw,
  Truck,
  X,
} from "lucide-react";
import { PhotoGallery } from "@/shared/components/PhotoGallery";
import { Card } from "@/shared/components/Card";
import { formatDateTime } from "@/shared/lib/format";
import { sanitizeText } from "@/shared/lib/sanitize";
import { useShift } from "@/features/operator/lib/use-shift";
import {
  fetchStation,
  stationErrorMessage,
  updateFieldStationStatus,
} from "@/features/stations/api/stations";
import {
  STATION_KIND_LABEL,
  type AdminStation,
  type StationKind,
} from "@/features/stations/types";

interface ShiftStationCardProps {
  /** Chamado quando a estrutura muda, para a lista pública refletir na hora. */
  readonly onChanged?: () => void;
}

interface Feedback {
  readonly tone: "ok" | "error";
  readonly text: string;
}

/**
 * Estrutura do plantão aberto, com ativação/desativação em campo (RF11).
 *
 * <p>O guarda-vidas só alterna o posto/base em que está de plantão: se precisar
 * sair, ou se o posto ficar indisponível, ele desliga a estrutura e o pin sai do
 * mapa sem esperar a TI. Nada é excluído — plantões e ocorrências continuam
 * apontando para a mesma linha. Reativar devolve a estrutura ao mapa.
 */
export function ShiftStationCard({ onChanged }: ShiftStationCardProps) {
  const { shift, loading, loadError, reload } = useShift();
  const [station, setStation] = useState<AdminStation | null>(null);
  const [stationError, setStationError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  const kind: StationKind | null = shift?.postId
    ? "FIXED"
    : shift?.mobileBaseId
      ? "MOBILE"
      : null;
  const stationId = shift?.postId ?? shift?.mobileBaseId ?? null;

  const loadStation = useCallback(async () => {
    if (!kind || !stationId) {
      return;
    }
    try {
      const next = await fetchStation(kind, stationId);
      setStation(next);
      setStationError(null);
    } catch (error) {
      setStation(null);
      setStationError(stationErrorMessage(error));
    }
  }, [kind, stationId]);

  useEffect(() => {
    if (!kind || !stationId) {
      return;
    }
    let cancelled = false;
    const run = async () => {
      try {
        const next = await fetchStation(kind, stationId);
        if (cancelled) return;
        setStation(next);
        setStationError(null);
      } catch (error) {
        if (cancelled) return;
        setStation(null);
        setStationError(stationErrorMessage(error));
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [kind, stationId]);

  const toggle = async (active: boolean) => {
    if (!kind || !stationId) return;
    setBusy(true);
    setFeedback(null);
    try {
      const updated = await updateFieldStationStatus(
        kind,
        stationId,
        active,
        active ? null : note.trim() || null,
      );
      setStation(updated);
      setNote("");
      setFeedback({
        tone: "ok",
        text: active
          ? `${sanitizeText(updated.name, 120)} voltou ao mapa.`
          : `${sanitizeText(updated.name, 120)} saiu do mapa até você reativar.`,
      });
      onChanged?.();
    } catch (error) {
      setFeedback({ tone: "error", text: stationErrorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  if (loading && !shift) {
    return (
      <Card className="border-ocean-200 bg-white">
        <p className="text-xs font-semibold text-ocean-600">
          Verificando a estrutura do plantão…
        </p>
      </Card>
    );
  }

  if (!shift) {
    return (
      <Card className="border-dashed bg-ocean-50/40">
        <p className="text-sm font-bold text-ocean-950">
          Abra o plantão para gerenciar sua estrutura
        </p>
        <p className="mt-1 text-xs text-ocean-700">
          A ativação e a desativação em campo valem para o posto ou a base do
          plantão aberto.{" "}
          <Link
            to="/operador/sos"
            className="font-bold underline decoration-2 underline-offset-2"
          >
            Entrar de plantão
          </Link>
          .
        </p>
        {loadError ? (
          <button
            type="button"
            onClick={reload}
            className="mt-2 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-ocean-300 bg-white px-3 text-xs font-bold text-ocean-900 hover:bg-ocean-50"
          >
            <RefreshCw aria-hidden="true" className="size-3.5" />
            Tentar de novo
          </button>
        ) : null}
      </Card>
    );
  }

  return (
    <Card
      className={`flex flex-col gap-3 ${
        station && !station.active
          ? "border-amber-300 bg-amber-50/60"
          : "border-emerald-300 bg-emerald-50/50"
      }`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex items-start gap-2">
          <span
            className={`grid size-9 shrink-0 place-items-center rounded-xl text-white ${
              station && !station.active ? "bg-amber-600" : "bg-emerald-700"
            }`}
          >
            {kind === "MOBILE" ? (
              <Truck aria-hidden="true" className="size-5" />
            ) : (
              <MapPin aria-hidden="true" className="size-5" />
            )}
          </span>
          <div>
            <p className="text-[10px] font-black uppercase tracking-wider text-ocean-600">
              Sua estrutura do plantão
            </p>
            <h2 className="text-sm font-black text-ocean-950">
              {station ? sanitizeText(station.name, 120) : "Carregando estrutura"}
            </h2>
            <p className="text-[11px] text-ocean-600">
              {kind ? STATION_KIND_LABEL[kind] : ""}
              {station?.beachName
                ? ` · ${sanitizeText(station.beachName, 120)}`
                : ""}
            </p>
          </div>
        </div>
        <span
          className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-[10px] font-black uppercase tracking-wider ${
            station?.active
              ? "border-emerald-400 bg-white text-emerald-800"
              : "border-amber-400 bg-white text-amber-800"
          }`}
        >
          {station?.active ? "No mapa" : "Fora do mapa"}
        </span>
      </div>

      {stationError ? (
        <p className="flex flex-wrap items-center gap-2 rounded-xl border border-coral-300 bg-coral-50 px-3 py-2 text-xs font-semibold text-coral-900">
          {stationError}
          <button
            type="button"
            onClick={() => void loadStation()}
            className="rounded-lg border border-coral-300 bg-white px-2 py-0.5 text-[11px] font-bold text-coral-900"
          >
            Tentar de novo
          </button>
        </p>
      ) : null}

      {station?.active === false ? (
        <p className="rounded-xl border border-amber-300 bg-amber-50 px-3 py-2 text-[11px] font-semibold text-amber-900">
          Esta estrutura está fora do mapa.
          {station.deactivatedAt
            ? ` Desativada em ${formatDateTime(station.deactivatedAt)}.`
            : ""}
          {station.statusNote
            ? ` Motivo: ${sanitizeText(station.statusNote, 200)}`
            : ""}
        </p>
      ) : null}

      {station ? (
        <PhotoGallery
          urls={station.photoUrls}
          label={sanitizeText(station.name, 120)}
          compact
          openLabel="Ampliar foto do posto"
        />
      ) : null}

      {feedback ? (
        <output
          className={`flex items-start justify-between gap-3 rounded-xl border px-3 py-2 text-xs font-semibold ${
            feedback.tone === "ok"
              ? "border-emerald-300 bg-emerald-50 text-emerald-900"
              : "border-coral-300 bg-coral-50 text-coral-900"
          }`}
        >
          <span className="flex items-start gap-1.5">
            {feedback.tone === "ok" ? (
              <CheckCircle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            ) : (
              <AlertTriangle aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
            )}
            {feedback.text}
          </span>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            aria-label="Fechar aviso da estrutura"
            className="shrink-0 rounded p-0.5 transition hover:bg-black/5"
          >
            <X aria-hidden="true" className="size-3.5" />
          </button>
        </output>
      ) : null}

      {station?.active ? (
        <div className="grid gap-2">
          <label className="block text-xs font-semibold text-ocean-800">
            Motivo da desativação (opcional)
            <input
              type="text"
              value={note}
              maxLength={240}
              onChange={(event) => setNote(event.target.value)}
              disabled={busy}
              placeholder="Vou sair do posto, estrutura interditada…"
              className="mt-1 min-h-10 w-full rounded-xl border border-ocean-200 bg-white px-3 text-sm font-medium text-ocean-950 placeholder:text-ocean-400 disabled:opacity-60"
            />
          </label>
          <button
            type="button"
            onClick={() => void toggle(false)}
            disabled={busy}
            className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl border border-amber-300 bg-white px-3 text-sm font-bold text-amber-900 transition hover:bg-amber-50 disabled:cursor-progress disabled:opacity-60"
          >
            {busy ? (
              <RefreshCw aria-hidden="true" className="size-4 animate-spin" />
            ) : (
              <PowerOff aria-hidden="true" className="size-4" />
            )}
            Desativar e sair do mapa
          </button>
          <p className="text-[11px] leading-snug text-ocean-600">
            Nada é apagado: o plantão e as ocorrências continuam ligados a esta
            estrutura. Ao voltar, reative para o pin aparecer de novo.
          </p>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => void toggle(true)}
          disabled={busy || !station}
          className="inline-flex min-h-10 items-center justify-center gap-1.5 rounded-xl bg-ocean-950 px-3 text-sm font-bold text-white transition hover:bg-ocean-900 disabled:cursor-progress disabled:opacity-60"
        >
          {busy ? (
            <RefreshCw aria-hidden="true" className="size-4 animate-spin" />
          ) : (
            <Power aria-hidden="true" className="size-4" />
          )}
          Reativar e voltar ao mapa
        </button>
      )}
    </Card>
  );
}
